package com.github.moagrius.tileview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;

import com.github.moagrius.utils.Maths;
import com.github.moagrius.utils.Throttler;
import com.github.moagrius.widget.ScrollView;
import com.github.moagrius.widget.ZoomScrollView;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TileView extends View implements
    ZoomScrollView.ScaleChangedListener,
    ScrollView.ScrollChangedListener,
    Tile.DetailProvider {

  private BitmapFactory.Options mBitmapOptions = new TileOptions();

  private float mScale = 1f;
  // cache zoom and sample from scale
  private int mZoom;
  private int mSample;

  private DetailList mDetailLevels = new DetailList();
  private Detail mCurrentDetail;

  private ZoomScrollView mZoomScrollView;

  private Rect mViewport = new Rect();
  private Set<Tile> mNewlyVisibleTiles = new HashSet<>();
  private Set<Tile> mTilesVisibleInViewport = new HashSet<>();

  private Executor mExecutor = Executors.newFixedThreadPool(3);
  private Throttler mThrottler = new Throttler(10);

  private Cache mMemoryCache = new MemoryCache((int) ((Runtime.getRuntime().maxMemory() / 1024) / 4));

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
    updateViewportAndComputeTilesThrottled();
  }

  public float getScale() {
    return mScale;
  }

  public void setScale(float scale) {
    int previousZoom = mZoom;
    mScale = scale;
    mZoom = Detail.getZoomFromPercent(mScale);
    mSample = Detail.getSampleFromPercent(mScale);
    Log.d("DLS", "setting zoom to " + mZoom);
    if (mZoom != previousZoom) {
      onZoomChanged(mZoom, previousZoom);
    }
    invalidate();
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

  @Override
  public int getCurrentZoom() {
    return mZoom;
  }

  @Override
  public int getCurrentSample() {
    return mSample;
  }

  private void onZoomChanged(int current, int previous) {
    Log.d("DL", "clearing tiles");
    mTilesVisibleInViewport.clear();
    determineCurrentDetail();
  }

  private void determineCurrentDetail() {
    String template = mDetailLevels.get(mZoom);  // do we have an exact match?
    if (template != null) {
      mCurrentDetail = new Detail(mZoom, template);
      return;
    }
    for (int i = 0; i < mZoom; i++) {
      template = mDetailLevels.get(i);
      if (template != null) {  // if it's defined
        mCurrentDetail = new Detail(i, template);
      }
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    canvas.scale(mScale, mScale);
    for (Tile tile : mTilesVisibleInViewport) {
      tile.draw(canvas);
    }
  }

  @Override
  public Detail getCurrentDetail() {
    return mCurrentDetail;
  }

  // TODO: abstract this, new strategy entirely
  public void setBaseDetailLevel(String template) {
    addDetailLevel(0, template);
  }

  public void addDetailLevel(int zoom, String template) {
    mDetailLevels.set(zoom, template);
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

  public Grid getCellGridFromViewport() {
    float tileSize = Tile.TILE_SIZE * mScale;
    // force rows and columns to be in increments equal to sample size...
    // round down the start and round up the end to make sure we cover the screen
    // e.g. rows 7:18 with sample size 4 become 4:20
    // this is to make sure that the cells are recognized as whole units and not redrawn when the viewport moves by a distance smaller than a computed tile
    Grid grid = new Grid();
    grid.rows.start = Maths.roundDownWithStep(mViewport.top / tileSize, mSample);
    grid.rows.end = Maths.roundUpWithStep(mViewport.bottom / tileSize, mSample);
    grid.columns.start = Maths.roundDownWithStep(mViewport.left / tileSize, mSample);
    grid.columns.end = Maths.roundUpWithStep(mViewport.right / tileSize, mSample);
    return grid;
  }

  private void computeTilesInCurrentViewport() {
    mNewlyVisibleTiles.clear();
    int sample = mBitmapOptions.inSampleSize;
    Grid grid = getCellGridFromViewport();
    for (int row = grid.rows.start; row < grid.rows.end; row += sample) {
      for (int column = grid.columns.start; column < grid.columns.end; column += sample) {
        // TODO: recycle tiles
        Tile tile = new Tile();
        tile.setDefaultColor(0xFFE7E7E7);
        tile.setOptions(mBitmapOptions);
        tile.setStartColumn(column);
        tile.setStartRow(row);
        tile.setDetailProvider(this);
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
            tile.decode(getContext(), mMemoryCache, this);
          } catch (Exception e) {
            Log.d("TV", "exception decoding: ${e.javaClass}, ${e.message}");
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
