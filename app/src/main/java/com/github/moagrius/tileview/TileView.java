package com.github.moagrius.tileview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.github.moagrius.tileview.io.StreamProvider;
import com.github.moagrius.tileview.io.StreamProviderAssets;
import com.github.moagrius.tileview.plugins.Plugin;
import com.github.moagrius.utils.Maths;
import com.github.moagrius.widget.ScrollView;
import com.github.moagrius.widget.ScalingScrollView;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class TileView extends ScalingScrollView implements
    Handler.Callback,
    ScalingScrollView.ScaleChangedListener,
    ScrollView.ScrollChangedListener,
    Tile.DrawingView,
    Tile.Listener,
    TilingBitmapView.Provider {

  // constants
  private static final int RENDER_THROTTLE_ID = 0;
  private static final int RENDER_THROTTLE_INTERVAL = 15;

  // variables (settable)
  private int mZoom = 0;
  private int mImageSample = 1; // sample will always be one unless we don't have a defined detail level, then its 1 shl for every zoom level from the last defined detail
  private int mTileSize;
  private boolean mIsPrepared;
  private Detail mCurrentDetail;
  private Listener mListener;

  // variables (from build or attach)
  private FixedSizeViewGroup mContainer;
  private TilingBitmapView mTilingBitmapView;
  private BitmapCache mDiskCache;
  private BitmapCache mMemoryCache;
  private BitmapPool mBitmapPool;
  private StreamProvider mStreamProvider;
  private Bitmap.Config mBitmapConfig = Bitmap.Config.RGB_565;
  private DiskCachePolicy mDiskCachePolicy = DiskCachePolicy.CACHE_PATCHES;

  // final
  private final Grid mGrid = new Grid();
  private final DetailList mDetailList = new DetailList();
  private final Map<Class<? extends Plugin>, Plugin> mPlugins = new HashMap<>();

  // we keep our tiles in Sets
  // that means we're ensured uniqueness (so we don't have to think about if a tile is already scheduled or not)
  // and O(1) contains, as well as no penalty with foreach loops (excluding the allocation of the iterator)
  private final Set<Tile> mNewlyVisibleTiles = new HashSet<>();
  private final Set<Tile> mTilesVisibleInViewport = new HashSet<>();
  private final Set<Tile> mPreviouslyDrawnTiles = new HashSet<>();

  private final Rect mViewport = new Rect();
  private final Rect mScaledViewport = new Rect();  // really just a buffer for unfilled region
  private final Region mUnfilledRegion = new Region();

  private final TilePool mTilePool = new TilePool(this::createTile);
  private final TileRenderExecutor mExecutor = new TileRenderExecutor();
  private final Handler mRenderThrottle = new Handler(this);

  public TileView(Context context) {
    this(context, null);
  }

  public TileView(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public TileView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    setScaleChangedListener(this);
    setScrollChangedListener(this);
    // as a ScrollView subclass, we can only use one child.
    // this could be the TilingBitmapView if we didn't allow plugins
    // use FixedSizeViewGroup as a simple, optimized layering scheme
    // by calling it during construction, no other views will be allowed
    // (unless the user hacks intended behavior by removing all views or by index)
    mContainer = new FixedSizeViewGroup(context);
    // we'll draw bitmaps to this view
    mTilingBitmapView = new TilingBitmapView(this);
    mContainer.addView(mTilingBitmapView);
    addView(mContainer);
  }

  // public

  public int getZoom() {
    return mZoom;
  }

  public Listener getListener() {
    return mListener;
  }

  public void setListener(Listener listener) {
    mListener = listener;
  }

  public ViewGroup getContainer() {
    return mContainer;
  }

  @SuppressWarnings("unchecked")
  public <T extends Plugin> T getPlugin(Class<T> clazz) {
    return (T) mPlugins.get(clazz);
  }

  // not

  private boolean isReady() {
    return mIsPrepared && ViewCompat.isAttachedToWindow(this);
  }

  private void defineZoomLevel(int zoom, Object data) {
    mDetailList.set(zoom, new Detail(zoom, data));
    determineCurrentDetail();
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    updateViewportAndComputeTilesThrottled();
  }

  @Override
  public void onScrollChanged(ScrollView scrollView, int x, int y) {
    updateViewportAndComputeTilesThrottled();
    if (mListener != null) {
      mListener.onScrollChanged(x, y);
    }
  }

  // when our scale provider changes, we must follow suit
  @Override
  public void onScaleChanged(ScalingScrollView scalingScrollView, float currentScale, float previousScale) {
    if (mListener != null) {
      mListener.onScaleChanged(currentScale, previousScale);
    }
    int previousZoom = mZoom;
    mZoom = Detail.getZoomFromPercent(currentScale);
    if (mZoom != previousZoom) {
      onZoomChanged(mZoom, previousZoom);
    }
    updateScaledViewport();
    updateViewportAndComputeTilesThrottled();
    mTilingBitmapView.invalidate();  // if this is setDirty or postInvalidate, things get wonky
  }

  protected void onZoomChanged(int current, int previous) {
    mPreviouslyDrawnTiles.clear();
    for (Tile tile : mTilesVisibleInViewport) {
      if (tile.getState() == Tile.State.DECODED) {
        mPreviouslyDrawnTiles.add(tile);
      }
    }
    mTilesVisibleInViewport.clear();
    determineCurrentDetail();
    if (mListener != null) {
      mListener.onZoomChanged(current, previous);
    }
  }

  private void determineCurrentDetail() {
    // if zoom from scale is greater than the number of defined detail levels, we definitely don't have it
    // since it's not an exact match, we need to patch together bitmaps from the last known zoom level
    // so if we have a detail level defined for zoom level 1 (sample 2) but are on zoom level 2 (sample 4) we want an actual sample of 2
    // similarly if we have definition for sample zoom 1 / sample 2 and are on zoom 3 / sample 8, we want actual sample of 4
    // this is also the case for the third block, below.
    if (mZoom >= mDetailList.size()) {
      mCurrentDetail = mDetailList.getHighestDefined();
      int zoomDelta = mZoom - mCurrentDetail.getZoom();
      mImageSample = 1 << zoomDelta;
      return;
    }
    // best case, it's an exact match, use that and set sample to 1
    Detail exactMatch = mDetailList.get(mZoom);
    if (exactMatch != null) {
      mCurrentDetail = exactMatch;
      return;
    }
    // it's not bigger than what we have defined, but we don't have an exact match, start at the requested zoom and work back
    // toward 0 (full size) until we find any defined detail level
    for (int i = mZoom - 1; i >= 0; i--) {
      Detail current = mDetailList.get(i);
      if (current != null) {  // if it's defined
        mCurrentDetail = current;
        int zoomDelta = mZoom - mCurrentDetail.getZoom();
        mImageSample = 1 << zoomDelta;
        return;
      }
    }
  }

  private void establishDirtyRegion() {
    mUnfilledRegion.set(mScaledViewport);
    // then punch holes in it for every decoded current tile
    // when drawing previous tiles, if there's no intersection with an unfilled area, it can be safely discarded
    // otherwise we should draw the previous tile
    for (Tile tile : mTilesVisibleInViewport) {
      if (tile.getState() == Tile.State.DECODED) {
        mUnfilledRegion.op(tile.getDrawingRect(), Region.Op.DIFFERENCE);
      }
    }
  }

  private void drawPreviousTiles(Canvas canvas) {
    establishDirtyRegion();
    if (mUnfilledRegion.isEmpty()) {
      return;
    }
    Iterator<Tile> iterator = mPreviouslyDrawnTiles.iterator();
    while (iterator.hasNext()) {
      Tile tile = iterator.next();
      Rect rect = tile.getDrawingRect();
      // if no part of the rect is in the unfilled area, we don't need it
      // use quickReject instead of quickContains because the latter does not work on complex Regions
      // https://developer.android.com/reference/android/graphics/Region.html#quickContains(android.graphics.Rect)
      if (mUnfilledRegion.quickReject(rect)) {
        tile.destroy();
        iterator.remove();
      } else {
        tile.draw(canvas);
      }
    }
  }

  private void drawCurrentTiles(Canvas canvas) {
    for (Tile tile : mTilesVisibleInViewport) {
      tile.draw(canvas);
    }
  }

  @Override
  public void drawTiles(Canvas canvas) {
    drawPreviousTiles(canvas);
    drawCurrentTiles(canvas);
  }

  @Override
  public void setDirty() {
    mTilingBitmapView.setDirty();
  }

  // Implementing Handler.Callback handleMessage to react to throttled requests to start a render op
  @Override
  public boolean handleMessage(Message message) {
    updateViewportAndComputeTiles();
    return true;
  }

  private void updateViewportAndComputeTiles() {
    if (isReady()) {
      updateViewport();
      computeAndRenderTilesInViewport();
    }
  }

  private void updateViewportAndComputeTilesThrottled() {
    if (!mRenderThrottle.hasMessages(RENDER_THROTTLE_ID)) {
      mRenderThrottle.sendEmptyMessageDelayed(RENDER_THROTTLE_ID, RENDER_THROTTLE_INTERVAL);
    }
  }

  private void updateViewport() {
    mViewport.left = getScrollX();
    mViewport.top = getScrollY();
    mViewport.right = mViewport.left + getMeasuredWidth();
    mViewport.bottom = mViewport.top + getMeasuredHeight();
    updateScaledViewport();
  }

  private void updateScaledViewport() {
    // set unfilled to entire viewport, virtualized to scale
    float scale = getScale();
    mScaledViewport.set(
        (int) (mViewport.left / scale),
        (int) (mViewport.top / scale),
        (int) (mViewport.right / scale),
        (int) (mViewport.bottom / scale)
    );
  }

  public void populateTileGridFromViewport() {
    float tileSize = mTileSize * getScale() * mCurrentDetail.getSample();
    mGrid.rows.start = Maths.roundDownWithStep(mViewport.top / tileSize, mImageSample);
    mGrid.rows.end = Maths.roundUpWithStep(mViewport.bottom / tileSize, mImageSample);
    mGrid.columns.start = Maths.roundDownWithStep(mViewport.left / tileSize, mImageSample);
    mGrid.columns.end = Maths.roundUpWithStep(mViewport.right / tileSize, mImageSample);
  }

  public Tile createTile() {
    return new Tile(mTileSize, mBitmapConfig, this, this, mExecutor, mStreamProvider, mMemoryCache, mDiskCache, mBitmapPool, mDiskCachePolicy);
  }

  private void computeAndRenderTilesInViewport() {
    // determine which tiles should be showing.  use sample size for patching very small tiles together
    mNewlyVisibleTiles.clear();
    populateTileGridFromViewport();
    for (int row = mGrid.rows.start; row < mGrid.rows.end; row += mImageSample) {
      for (int column = mGrid.columns.start; column < mGrid.columns.end; column += mImageSample) {
        Tile tile = mTilePool.get();
        tile.setColumn(column);
        tile.setRow(row);
        tile.setDetail(mCurrentDetail);
        tile.setImageSample(mImageSample);
        mNewlyVisibleTiles.add(tile);
      }
    }
    // update our sets to reflect the current state, schedule draws, and clean up
    Iterator<Tile> tilesVisibleInViewportIterator = mTilesVisibleInViewport.iterator();
    while (tilesVisibleInViewportIterator.hasNext()) {
      Tile tile = tilesVisibleInViewportIterator.next();
      // if a tile in the same zoom is not in the most recently computed grid, it's not longer "in viewport", remove it
      if (!mNewlyVisibleTiles.contains(tile)) {
        tile.destroy();
        tilesVisibleInViewportIterator.remove();
      }
    }
    // we just removed all tiles outside of the viewport, now add any new ones that are in the viewport that weren't there the last
    // time we performed this computation
    // we use add all instead of straight replacement because lets say tile(3:2) was being decoded - when tile(3:2) comes up in
    // mNewlyVisibleTiles, it won't be added to mTilesVisibleInViewport because Tile.equals will return true
    // if we just swapped out the set (mTilesVisibleInViewport = mNewlyVisibleTiles), all those tiles would lose their state
    boolean tilesWereAdded = mTilesVisibleInViewport.addAll(mNewlyVisibleTiles);
    if (tilesWereAdded) {
      mExecutor.queue(mTilesVisibleInViewport);
    }
  }

  @Override
  public void onTileDestroyed(Tile tile) {
    mTilePool.put(tile);
  }

  @Override
  public void onTileDecodeError(Tile tile, Exception e) {
    // no op for now, probably expose this to the user
  }

  public void destroy() {
    mExecutor.shutdownNow();
    mTilePool.clear();
    mRenderThrottle.removeMessages(RENDER_THROTTLE_ID);
  }

  private void prepare() {
    if (mDetailList.isEmpty()) {
      throw new IllegalStateException("TileView requires at least one defined detail level");
    }
    if (mTilingBitmapView.getLayoutParams().width == 0 || mTilingBitmapView.getLayoutParams().height == 0) {
      throw new IllegalStateException("TileView requires height and width be provided via Builder.setSize");
    }
    mIsPrepared = true;
    determineCurrentDetail();
    updateViewportAndComputeTiles();
  }

  private static class Grid {
    Range rows = new Range();
    Range columns = new Range();
    private static class Range {
      int start;
      int end;
    }
  }

  public interface BitmapCache {
    Bitmap get(String key);
    Bitmap put(String key, Bitmap value);
    Bitmap remove(String key);
  }

  public interface BitmapPool {
    Bitmap getBitmapForReuse(Tile tile);
  }

  public interface Listener {
    void onZoomChanged(int zoom, int previous);
    void onScaleChanged(float scale, float previous);
    void onScrollChanged(int x, int y);
    void onReady(TileView tileView);
  }

  private static class FixedSizeViewGroup extends ViewGroup {

    private int mWidth;
    private int mHeight;

    public FixedSizeViewGroup(Context context) {
      super(context);
    }

    public void setSize(int width, int height) {
      mWidth = width;
      mHeight = height;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
      for (int i = 0; i < getChildCount(); i++) {
        View child = getChildAt(i);
        child.layout(0, 0, mWidth, mHeight);
      }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
      measureChildren(widthMeasureSpec, heightMeasureSpec);
      setMeasuredDimension(mWidth, mHeight);
    }

  }

  public static class Builder {

    private TileView mTileView;
    private StreamProvider mStreamProvider;

    private Bitmap.Config mConfig = Bitmap.Config.RGB_565;
    private int mTileSize = 256;
    private int mMemoryCacheSize = (int) ((Runtime.getRuntime().maxMemory() / 1024) / 4);
    private int mDiskCacheSize = 1024 * 100;
    private DiskCachePolicy mDiskCachePolicy = DiskCachePolicy.CACHE_PATCHES;

    public Builder(TileView tileView) {
      mTileView = tileView;
    }

    public Builder(Context context) {
      mTileView = new TileView(context);
    }

    public Builder setSize(int width, int height) {
      mTileView.mContainer.setSize(width, height);
      return this;
    }

    public Builder defineZoomLevel(Object data) {
      return defineZoomLevel(0, data);
    }

    public Builder defineZoomLevel(int zoom, Object data) {
      mTileView.defineZoomLevel(zoom, data);
      return this;
    }

    public Builder setListener(TileView.Listener listener) {
      mTileView.setListener(listener);
      return this;
    }

    public Builder setBitmapConfig(Bitmap.Config config) {
      mConfig = config;
      return this;
    }

    public Builder setTileSize(int tileSize) {
      mTileSize = tileSize;
      return this;
    }

    public Builder setDiskCachePolicity(DiskCachePolicy policy) {
      mDiskCachePolicy = policy;
      return this;
    }

    public Builder setMemoryCacheSize(int memoryCacheSize) {
      mMemoryCacheSize = memoryCacheSize;
      return this;
    }

    public Builder setDiskCacheSize(int diskCacheSize) {
      mDiskCacheSize = diskCacheSize;
      return this;
    }

    public Builder setStreamProvider(StreamProvider streamProvider) {
      mStreamProvider = streamProvider;
      return this;
    }

    public Builder installPlugin(Plugin plugin) {
      mTileView.mPlugins.put(plugin.getClass(), plugin);
      plugin.install(mTileView);
      return this;
    }

    // getters

    private StreamProvider getStreamProvider() {
      if (mStreamProvider == null) {
        mStreamProvider = new StreamProviderAssets();
      }
      return mStreamProvider;
    }

    public TileView build() {
      mTileView.mTileSize = mTileSize;
      mTileView.mBitmapConfig = mConfig;
      // if the user provided a custom provider, use that, otherwise default to assets
      mTileView.mStreamProvider = getStreamProvider();
      // use memory cache instance for both memory cache and bitmap pool.  maybe allows these to be set in the future
      MemoryCache memoryCache = new MemoryCache(mMemoryCacheSize);
      mTileView.mMemoryCache = memoryCache;
      mTileView.mBitmapPool = memoryCache;
      // if the policy is to cache something and the size is not 0, try to create a disk cache
      // TODO: async?
      mTileView.mDiskCachePolicy = mDiskCachePolicy;
      if (mDiskCachePolicy != DiskCachePolicy.CACHE_NONE && mDiskCacheSize > 0) {
        try {
          mTileView.mDiskCache = new DiskCache(mTileView.getContext(), mDiskCacheSize);
        } catch (IOException e) {
          // no op
        }
      }
      mTileView.prepare();
      return mTileView;
    }

  }

  public enum DiskCachePolicy {
    CACHE_NONE, CACHE_PATCHES, CACHE_ALL
  }

}
