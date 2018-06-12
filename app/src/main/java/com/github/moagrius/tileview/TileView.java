package com.github.moagrius.tileview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;

import com.github.moagrius.utils.Maths;
import com.github.moagrius.widget.ScrollView;
import com.github.moagrius.widget.ZoomScrollView;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class TileView extends View implements
    Handler.Callback,
    ZoomScrollView.ScaleChangedListener,
    ScrollView.ScrollChangedListener,
    Tile.DrawingView,
    Tile.Listener {

  private static final int MEMORY_CACHE_SIZE = (int) ((Runtime.getRuntime().maxMemory() / 1024) / 4);
  private static final int DISK_CACHE_SIZE = 1024 * 100;

  private static final int RENDER_THROTTLE_ID = 0;
  private static final int RENDER_THROTTLE_INTERVAL = 15;

  private float mScale = 1f;

  private int mZoom = 0;
  // sample will always be one unless we don't have a defined detail level, then its 1 shl for every zoom level from the last defined detail
  private int mImageSample = 1;

  private StreamProvider mStreamProvider;

  private Grid mGrid = new Grid();
  private DetailList mDetailList = new DetailList();
  private Detail mLastValidDetail;

  private ZoomScrollView mZoomScrollView;

  // we keep our tiles in Sets
  // that means we're ensured uniqueness (so we don't have to think about if a tile is already scheduled or not)
  // and O(1) contains, as well as no penalty with foreach loops (excluding the allocation of the iterator)
  private Set<Tile> mNewlyVisibleTiles = new HashSet<>();
  private Set<Tile> mTilesVisibleInViewport = new HashSet<>();
  private Set<Tile> mPreviouslyDrawnTiles = new HashSet<>();

  private Rect mViewport = new Rect();
  private Rect mScaledViewport = new Rect();  // really just a buffer for unfilled region
  private Region mUnfilledRegion = new Region();

  private TileRenderExecutor mExecutor = new TileRenderExecutor();

  private Handler mRenderThrottleHandler = new Handler(this);
  private boolean mIsDirty;

  private BitmapCache mDiskCache;
  private BitmapCache mMemoryCache;
  private BitmapPool mBitmapPool;

  private TilePool mTilePool = new TilePool();

  public TileView(Context context) {
    this(context, null);
  }

  public TileView(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public TileView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    try {
      mDiskCache = new DiskCache(context, DISK_CACHE_SIZE);
      MemoryCache memoryCache = new MemoryCache(MEMORY_CACHE_SIZE);
      mMemoryCache = memoryCache;
      mBitmapPool = memoryCache;
    } catch (IOException e) {
      // TODO: allow use without disk cache
      throw new RuntimeException("Unable to initialize disk cache");
    }
  }

  // TODO: use a prepare method (like MediaPlayer) and a background thread for setup
  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    ViewParent parent = getParent();
    while (!(parent instanceof ZoomScrollView)) {
      if (parent == null) {
        throw new IllegalStateException("TileView must be a descendant of a ZoomScrollView");
      }
      parent = getParent();
    }
    mZoomScrollView = (ZoomScrollView) getParent();
    mZoomScrollView.setScaleChangedListener(this);
    mZoomScrollView.setScrollChangedListener(this);
    determineCurrentDetail();
    updateViewportAndComputeTilesThrottled();
  }

  public StreamProvider getStreamProvider() {
    if (mStreamProvider == null) {
      mStreamProvider = new StreamProviderAssets();
    }
    return mStreamProvider;
  }

  public void setStreamProvider(StreamProvider streamProvider) {
    mStreamProvider = streamProvider;
  }

  public void defineZoomLevel(Object data) {
    defineZoomLevel(0, data);
  }

  public void defineZoomLevel(int zoom, Object data) {
    mDetailList.set(zoom, new Detail(zoom, data));
    determineCurrentDetail();
  }

  public int getZoom() {
    return mZoom;
  }

  public float getScale() {
    return mScale;
  }

  public void setScale(float scale) {
    int previousZoom = mZoom;
    mScale = scale;
    mZoom = Detail.getZoomFromPercent(mScale);
    if (mZoom != previousZoom) {
      onZoomChanged(mZoom, previousZoom);
    }
    updateScaledViewport();
    setDirty();
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
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    updateViewportAndComputeTilesThrottled();
  }

  @Override
  public void onScrollChanged(ScrollView scrollView, int x, int y) {
    updateViewportAndComputeTilesThrottled();
  }

  @Override
  public void onScaleChanged(ZoomScrollView zoomScrollView, float currentScale, float previousScale) {
    setScale(currentScale);
    updateViewportAndComputeTilesThrottled();
  }

  private void determineCurrentDetail() {
    // if zoom from scale is greater than the number of defined detail levels, we definitely don't have it
    // since it's not an exact match, we need to patch together bitmaps from the last known zoom level
    // so if we have a detail level defined for zoom level 1 (sample 2) but are on zoom level 2 (sample 4) we want an actual sample of 2
    // similarly if we have definition for sample zoom 1 / sample 2 and are on zoom 3 / sample 8, we want actual sample of 4
    // this is also the case for the third block, below.
    //
    // unrelated, any time a detail level changes, we create a new TileOptions object, because we hand these instances off to tiles,
    // which may get drawn even after zoom level has changed, and changing the inSampleSize of a single shared instance would cause
    // tiles from previous zoom levels to render improperly
    if (mZoom >= mDetailList.size()) {
      mLastValidDetail = mDetailList.getHighestDefined();
      int zoomDelta = mZoom - mLastValidDetail.getZoom();
      mImageSample = 1 << zoomDelta;
      return;
    }
    // best case, it's an exact match, use that and set sample to 1
    Detail exactMatch = mDetailList.get(mZoom);
    if (exactMatch != null) {
      mLastValidDetail = exactMatch;
      return;
    }
    // it's not bigger than what we have defined, but we don't have an exact match, start at the requested zoom and work back
    // toward 0 (full size) until we find any defined detail level
    for (int i = mZoom - 1; i >= 0; i--) {
      Detail current = mDetailList.get(i);
      if (current != null) {  // if it's defined
        mLastValidDetail = current;
        int zoomDelta = mZoom - mLastValidDetail.getZoom();
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
  public void setDirty() {
    if (mIsDirty) {
      return;
    }
    mIsDirty = true;
    postInvalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    canvas.save();
    canvas.scale(mScale, mScale);
    drawPreviousTiles(canvas);
    drawCurrentTiles(canvas);
    canvas.restore();
    mIsDirty = false;
  }

  // Implementing Handler.Callback handleMessage to react to throttled requests to start a render op
  @Override
  public boolean handleMessage(Message message) {
    updateViewport();
    computeAndRenderTilesInViewport();
    return true;
  }

  private void updateViewportAndComputeTilesThrottled() {
    if (!mRenderThrottleHandler.hasMessages(RENDER_THROTTLE_ID)) {
      mRenderThrottleHandler.sendEmptyMessageDelayed(RENDER_THROTTLE_ID, RENDER_THROTTLE_INTERVAL);
    }
  }

  private void updateViewport() {
    mViewport.left = mZoomScrollView.getScrollX();
    mViewport.top = mZoomScrollView.getScrollY();
    mViewport.right = mViewport.left + mZoomScrollView.getMeasuredWidth();
    mViewport.bottom = mViewport.top + mZoomScrollView.getMeasuredHeight();
    updateScaledViewport();
  }

  private void updateScaledViewport() {
    // set unfilled to entire viewport, virtualized to scale
    mUnfilledRegion.set(
        (int) (mViewport.left / mScale),
        (int) (mViewport.top / mScale),
        (int) (mViewport.right / mScale),
        (int) (mViewport.bottom / mScale)
    );
  }

  public void populateTileGridFromViewport() {
    float tileSize = Tile.TILE_SIZE * mScale * mLastValidDetail.getSample();
    mGrid.rows.start = Maths.roundDownWithStep(mViewport.top / tileSize, mImageSample);
    mGrid.rows.end = Maths.roundUpWithStep(mViewport.bottom / tileSize, mImageSample);
    mGrid.columns.start = Maths.roundDownWithStep(mViewport.left / tileSize, mImageSample);
    mGrid.columns.end = Maths.roundUpWithStep(mViewport.right / tileSize, mImageSample);
  }

  private Tile getDecoratedTile() {
    Tile tile = mTilePool.get();
    tile.setStreamProvider(getStreamProvider());
    tile.setThreadPoolExecutor(mExecutor);
    tile.setImageSample(mImageSample);
    tile.setDetail(mLastValidDetail);
    tile.setMemoryCache(mMemoryCache);
    tile.setBitmapPool(mBitmapPool);
    tile.setDiskCache(mDiskCache);
    tile.setDrawingView(this);
    tile.setListener(this);
    return tile;
  }

  private void computeAndRenderTilesInViewport() {
    // determine which tiles should be showing.  use sample size for patching very small tiles together
    mNewlyVisibleTiles.clear();
    populateTileGridFromViewport();
    for (int row = mGrid.rows.start; row < mGrid.rows.end; row += mImageSample) {
      for (int column = mGrid.columns.start; column < mGrid.columns.end; column += mImageSample) {
        Tile tile = getDecoratedTile();
        tile.setColumn(column);
        tile.setRow(row);
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
    mExecutor = null;
    mTilePool.clear();
    mTilePool = null;
    mRenderThrottleHandler = null;
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

}
