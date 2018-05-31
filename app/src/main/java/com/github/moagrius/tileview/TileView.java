package com.github.moagrius.tileview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Region;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;

import com.github.moagrius.utils.Debounce;
import com.github.moagrius.utils.Maths;
import com.github.moagrius.utils.SimpleObjectCache;
import com.github.moagrius.widget.ScrollView;
import com.github.moagrius.widget.ZoomScrollView;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TileView extends View implements
    ZoomScrollView.ScaleChangedListener,
    ScrollView.ScrollChangedListener,
    Tile.DrawingView {

  //private static final int MEMORY_CACHE_SIZE = (int) ((Runtime.getRuntime().maxMemory() / 1024) / 4);
  private static final int MEMORY_CACHE_SIZE = 1024 * 5;
  private static final int DISK_CACHE_SIZE = 1024 * 20;

  private BitmapFactory.Options mBitmapOptions = new TileOptions();

  private float mScale = 1f;
  private int mZoom = 0;
  // sample will always be one unless we don't have a defined detail level, then its 1 shl for every zoom level from the last defined detail
  private int mImageSample = 1;

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
  private Region mUnfilledRegion = new Region();

  // TODO: get number of threads from available cores
  private Executor mExecutor = Executors.newFixedThreadPool(3);
  private Debounce mDebounce = new Debounce(10);

  private Cache mDiskCache;
  private MemoryCache mMemoryCache = new MemoryCache(MEMORY_CACHE_SIZE);
  private BitmapPool mBitmapPool = new BitmapPool();
  {
    mMemoryCache.setBitmapPool(mBitmapPool);
  }


  private Runnable mUpdateAndComputeTilesRunnable = () -> {
    updateViewport();
    computeTilesInCurrentViewport();
  };

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
    } catch (IOException e) {
      // TODO: allow use without disk cache
      throw new RuntimeException("Unable to initialize disk cache");
    }
  }

  // TODO: TileOptions should be configurable, maybe have a Factory pass it to tiles...



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

  // TODO: abstract this, allow any data type, consider Providers
  public void defineZoomLevel(String template) {
    defineZoomLevel(0, template);
  }

  public void defineZoomLevel(int zoom, String template) {
    mDetailList.set(zoom, new Detail(zoom, template));
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
    invalidate();
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
      mBitmapOptions = new TileOptions();
      mBitmapOptions.inSampleSize = mImageSample;
      return;
    }
    // best case, it's an exact match, use that and set sample to 1
    Detail exactMatch = mDetailList.get(mZoom);
    if (exactMatch != null) {
      mLastValidDetail = exactMatch;
      mBitmapOptions = new TileOptions();
      mImageSample = mBitmapOptions.inSampleSize = 1;
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
        mBitmapOptions = new TileOptions();
        mBitmapOptions.inSampleSize = mImageSample;
        return;
      }
    }
  }

  private void establishDirtyRegion() {
    // set unfilled to entire viewport, virtualized to scale
    mUnfilledRegion.set(
        (int) (mViewport.left / mScale),
        (int) (mViewport.top / mScale),
        (int) (mViewport.right / mScale),
        (int) (mViewport.bottom / mScale)
    );
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
    if (mUnfilledRegion.isEmpty()) {
      return;
    }
    Iterator<Tile> tilesFromLastDetailLevelIterator = mPreviouslyDrawnTiles.iterator();
    while (tilesFromLastDetailLevelIterator.hasNext()) {
      Tile tile = tilesFromLastDetailLevelIterator.next();
      Rect rect = tile.getDrawingRect();
      // if no part of the rect is in the unfilled area, we don't need it
      // use quickReject instead of quickContains because the latter does not work on complex Regions
      // https://developer.android.com/reference/android/graphics/Region.html#quickContains(android.graphics.Rect)
      if (mUnfilledRegion.quickReject(rect)) {
        tile.destroy();
        tilesFromLastDetailLevelIterator.remove();
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
  protected void onDraw(Canvas canvas) {
    canvas.scale(mScale, mScale);
    establishDirtyRegion();
    drawPreviousTiles(canvas);
    drawCurrentTiles(canvas);
  }

  private void updateViewportAndComputeTilesThrottled() {
    mDebounce.attempt(mUpdateAndComputeTilesRunnable);
  }

  private void updateViewport() {
    mViewport.left = mZoomScrollView.getScrollX();
    mViewport.top = mZoomScrollView.getScrollY();
    mViewport.right = mViewport.left + mZoomScrollView.getMeasuredWidth();
    mViewport.bottom = mViewport.top + mZoomScrollView.getMeasuredHeight();
  }

  public void populateTileGridFromViewport() {
    float tileSize = Tile.TILE_SIZE * mScale * mLastValidDetail.getSample();
    mGrid.rows.start = Maths.roundDownWithStep(mViewport.top / tileSize, mImageSample);
    mGrid.rows.end = Maths.roundUpWithStep(mViewport.bottom / tileSize, mImageSample);
    mGrid.columns.start = Maths.roundDownWithStep(mViewport.left / tileSize, mImageSample);
    mGrid.columns.end = Maths.roundUpWithStep(mViewport.right / tileSize, mImageSample);
  }

  private SimpleObjectCache<Tile> mTileCache = new SimpleObjectCache<>(Tile.class);

  private void computeTilesInCurrentViewport() {
    // determine which tiles should be showing.  use sample size for patching very small tiles together
    mNewlyVisibleTiles.clear();
    populateTileGridFromViewport();
    for (int row = mGrid.rows.start; row < mGrid.rows.end; row += mImageSample) {
      for (int column = mGrid.columns.start; column < mGrid.columns.end; column += mImageSample) {
        Tile tile = mTileCache.get();
        tile.setStartColumn(column);
        tile.setStartRow(row);
        tile.setImageSample(mImageSample);
        tile.setDetail(mLastValidDetail);
        tile.setMemoryCache(mMemoryCache);
        tile.setDiskCache(mDiskCache);
        tile.setBitmapPool(mBitmapPool);
        tile.setDrawingView(this);
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
        mTileCache.put(tile);
        tilesVisibleInViewportIterator.remove();
      }
    }
    // we just removed all tiles outside of the viewport, now add any new ones that are in the viewport that weren't there the last
    // time we performed this computation
    for (Tile tile : mNewlyVisibleTiles) {
      boolean added = mTilesVisibleInViewport.add(tile);
      // if added is false, that means it was already scheduled
      if (added) {
        mExecutor.execute(() -> {
          try {
            tile.decode(getContext());
          } catch (Exception e) {
            Log.d("TV", "exception decoding: " + e.getClass().getCanonicalName() + ":" + e.getMessage());
            e.printStackTrace();
          }
        });
      }
    }
  }

  private static class Grid {
    Range rows = new Range();
    Range columns = new Range();
    private static class Range {
      int start;
      int end;
    }
  }

  public interface Cache {
    Bitmap get(String key);
    Bitmap put(String key, Bitmap value);
  }

}
