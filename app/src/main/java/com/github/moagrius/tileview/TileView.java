package com.github.moagrius.tileview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;

import com.github.moagrius.utils.Maths;
import com.github.moagrius.utils.Throttler;
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
    ScrollView.ScrollChangedListener, Tile.DrawingView {

  private static final int MEMORY_CACHE_SIZE = (int) ((Runtime.getRuntime().maxMemory() / 1024) / 4);
  private static final int DISK_CACHE_SIZE = 1024 * 20;

  private BitmapFactory.Options mBitmapOptions = new TileOptions();

  private float mScale = 1f;
  private int mZoom = 0;
  // sample will always be one unless we don't have a defined detail level, then its 1 shl for every zoom level from the last defined detail
  private int mImageSample = 1;

  private Grid mGrid = new Grid();
  private DetailList mDetailLevels = new DetailList();
  private Detail mLastValidDetail;

  private ZoomScrollView mZoomScrollView;

  private Rect mViewport = new Rect();
  private Set<Tile> mNewlyVisibleTiles = new HashSet<>();
  private Set<Tile> mTilesVisibleInViewport = new HashSet<>();
  private Set<Tile> mPreviouslyDrawnTiles = new HashSet<>();

  private Region mUnfilledRegion = new Region();

  private Executor mExecutor = Executors.newFixedThreadPool(3);
  private Throttler mThrottler = new Throttler(10);

  private Cache mDiskCache;
  private Cache mMemoryCache = new MemoryCache(MEMORY_CACHE_SIZE);

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
      throw new RuntimeException("Unable to initialize disk cache");
    }
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    ViewParent parent = getParent();
    while (parent != null && !(parent instanceof ZoomScrollView)) {
      parent = getParent();
    }
    if (parent == null) {
      throw new IllegalStateException("TileView must be a descendant of a ZoomScrollView");
    }
    mZoomScrollView = (ZoomScrollView) getParent();
    mZoomScrollView.setScaleChangedListener(this);
    mZoomScrollView.setScrollChangedListener(this);
    determineCurrentDetail();
    Log.d("DL", "onAttached, sample is now " + mImageSample);
    updateViewportAndComputeTilesThrottled();
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

  private void onZoomChanged(int current, int previous) {
    mPreviouslyDrawnTiles.clear();
    Log.d("TV", "clearing previous tiles");
    for (Tile tile : mTilesVisibleInViewport) {
      if (tile.getState() == Tile.State.DECODED) {
        mPreviouslyDrawnTiles.add(tile);
      }
    }
    Log.d("TV", "just populated previously drawn tile set: " + mPreviouslyDrawnTiles.size());
    mTilesVisibleInViewport.clear();
    determineCurrentDetail();
    Log.d("DL", "onZoomChanged, sample is now " + mImageSample + ", zoom is " + mZoom);
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

  public int getZoom() {
    return mZoom;
  }

  private void determineCurrentDetail() {
    // if zoom from scale is greater than the number of defined detail levels, we definitely don't have it
    if (mZoom >= mDetailLevels.size()) {
      mLastValidDetail = mDetailLevels.getHighestDefined();
      Log.d("DLS", "got highest defined, zoom is " + mLastValidDetail.getZoom() + ", " + mLastValidDetail.getUri());
      int zoomDelta = mZoom - mLastValidDetail.getZoom();
      mImageSample = 1 << zoomDelta;
      mBitmapOptions = new TileOptions();
      mBitmapOptions.inSampleSize = mImageSample;
      Log.d("DLS", "no matching DL, sample is " + mImageSample);
      return;
    }
    // if it's an exact match, use that and set sample to 1
    Detail exactMatch = mDetailLevels.get(mZoom);  // do we have an exact match?
    if (exactMatch != null) {
      mLastValidDetail = exactMatch;
      mBitmapOptions = new TileOptions();
      mImageSample = mBitmapOptions.inSampleSize = 1;
      Log.d("DLS", "exact match found, sample is " + mImageSample + ", zoom is " + mZoom);
      return;
    }
    // so it's not bigger than what we have defined, but we don't have an exact match
    for (int i = mZoom - 1; i >= 0; i--) {
      Detail current = mDetailLevels.get(i);
      if (current != null) {  // if it's defined
        mLastValidDetail = current;
        int zoomDelta = mZoom - mLastValidDetail.getZoom();
        mImageSample = 1 << zoomDelta;
        mBitmapOptions = new TileOptions();
        mBitmapOptions.inSampleSize = mImageSample;
        Log.d("DLS", "no matching DL, sample is " + mImageSample);
        return;
      }
    }
    // not top level, we need to patch together bitmaps from the last known zoom level
    // so if we have a detail level defined for zoom level 1 (sample 2) but are on zoom level 2 (sample 4) we want an actual sample of 2
    // similarly if we have definition for sample zoom 1 / sample 2 and are on zoom 3 / sample 8, we want actual sample of 4
    //int zoomDelta = mZoom - mLastValidDetail.getZoom();  // so defined 1 minus actual 2 = 1
    //Log.d("TV", "last sample = " + mLastValidDetail.getImageSample() + ", zoomDelta = " + zoomDelta);
    //mImageSample = mLastValidDetail.getImageSample() << zoomDelta;
    //mImageSample = 1 << zoomDelta;
  }

  // TODO: debug
  private Paint mDebugPaint = new Paint();
  {
    mDebugPaint.setStyle(Paint.Style.FILL);
    mDebugPaint.setStrokeWidth(20);
    mDebugPaint.setColor(Color.RED);
  }

  private RectF mDebugRect = new RectF();

  @Override
  protected void onDraw(Canvas canvas) {
    canvas.scale(mScale, mScale);
    establishDirtyRegion();
    drawPreviousTiles(canvas);
    for (Tile tile : mTilesVisibleInViewport) {
      tile.draw(canvas);
    }
  }

  // TODO: abstract this, new strategy entirely
  public void setBaseDetailLevel(String template) {
    addDetailLevel(0, template);
  }

  public void addDetailLevel(int zoom, String template) {
    mDetailLevels.set(zoom, new Detail(zoom, template));
    determineCurrentDetail();
  }

  private void updateViewportAndComputeTilesThrottled() {
    mThrottler.attempt(mUpdateAndComputeTilesRunnable);
  }

  private void updateViewport() {
    mViewport.left = mZoomScrollView.getScrollX();
    mViewport.top = mZoomScrollView.getScrollY();
    int visibleRight = mViewport.left + mZoomScrollView.getMeasuredWidth();
    int visibleBottom = mViewport.top + mZoomScrollView.getMeasuredHeight();
    int actualRight = getWidth();
    int actualBottom = getHeight();
    mViewport.right = Math.min(visibleRight, actualRight);
    mViewport.bottom = Math.min(visibleBottom, actualBottom);
  }

  public void populateTileGridFromViewport() {
    float tileSize = Tile.TILE_SIZE * mScale * mLastValidDetail.getSample();
    mGrid.rows.start = Maths.roundDownWithStep(mViewport.top / tileSize, mImageSample);
    mGrid.rows.end = Maths.roundUpWithStep(mViewport.bottom / tileSize, mImageSample);
    mGrid.columns.start = Maths.roundDownWithStep(mViewport.left / tileSize, mImageSample);
    mGrid.columns.end = Maths.roundUpWithStep(mViewport.right / tileSize, mImageSample);
  }

  private void computeTilesInCurrentViewport() {
    mNewlyVisibleTiles.clear();
    populateTileGridFromViewport();
    for (int row = mGrid.rows.start; row < mGrid.rows.end; row += mImageSample) {
      for (int column = mGrid.columns.start; column < mGrid.columns.end; column += mImageSample) {
        // TODO: recycle tiles
        Tile tile = new Tile();
        tile.setOptions(mBitmapOptions);
        tile.setStartColumn(column);
        tile.setStartRow(row);
        tile.setImageSample(mImageSample);
        tile.setDetail(mLastValidDetail);
        tile.setDrawingView(this);
        mNewlyVisibleTiles.add(tile);
      }
    }
    Iterator<Tile> previousAndCurrentlyVisibleTileIterator = mTilesVisibleInViewport.iterator();
    while (previousAndCurrentlyVisibleTileIterator.hasNext()) {
      Tile tile = previousAndCurrentlyVisibleTileIterator.next();
      if (!mNewlyVisibleTiles.contains(tile)) {
        previousAndCurrentlyVisibleTileIterator.remove();
      }
    }
    for (Tile tile : mNewlyVisibleTiles) {
      boolean added = mTilesVisibleInViewport.add(tile);
      // TODO: anything that's decoding that isn't in the set should be stopped
      if (added) {
        mExecutor.execute(() -> {
          try {
            tile.decode(getContext(), mMemoryCache, mDiskCache);
          } catch (Exception e) {
            Log.d("TV", "exception decoding: " + e.getMessage());
          }
        });
      }
    }
  }

  // fancy

  private void establishDirtyRegion() {
    // set unfilled to entire viewport
    mUnfilledRegion.set(
        (int) (mViewport.left / mScale),
        (int) (mViewport.top / mScale),
        (int) (mViewport.right / mScale),
        (int) (mViewport.bottom / mScale)
    );
    Log.d("PREDRAW", "unfilled: " + mUnfilledRegion.getBounds());
    // then punch holes in it for every decoded current tile
    // when drawing previous tiles, if there's no intersection with an unfilled area, it can be safely discarded
    // otherwise we should draw the previous tile
    for (Tile tile : mTilesVisibleInViewport) {
      if (tile.getState() == Tile.State.DECODED) {
        Log.d("PREDRAW", "punching hole at " + tile.getScaledRect());
        mUnfilledRegion.op(tile.getScaledRect(), Region.Op.DIFFERENCE);
      }
    }
  }

  // TODO: is the intersection math wrong?  or something else?  also, mPreviouslyDrawnTiles does not seem to be emptying...
  private void drawPreviousTiles(Canvas canvas) {
    Log.d("TV", "unfilled region: " + mUnfilledRegion.getBounds());
    Log.d("TV", "previously drawn tile count (before): " + mPreviouslyDrawnTiles.size());
    Iterator<Tile> tilesFromLastDetailLevelIterator = mPreviouslyDrawnTiles.iterator();
    while (tilesFromLastDetailLevelIterator.hasNext()) {
      Tile tile = tilesFromLastDetailLevelIterator.next();
      Rect rect = tile.getDrawingRect();
      Log.d("PREDRAW", "test previous tile, rect is " + rect);
      // if no part of the rect is in the unfilled area, we don't need it
      if (mUnfilledRegion.quickReject(rect)) {
        Log.d("TV", "this previous tile does not intersect with an undrawn area, remove it");
        tile.destroy();
        tilesFromLastDetailLevelIterator.remove();
        Log.d("TV", "previously drawn tile count (while): " + mPreviouslyDrawnTiles.size());
      } else {
        Log.d("TV", "previously drawn tile does intersect an undrawn area, draw it: " + tile.getDrawingRect());
        tile.draw(canvas);
      }
    }
    Log.d("TV", "previously drawn tile count (after): " + mPreviouslyDrawnTiles.size());
  }

  // end fancy

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
