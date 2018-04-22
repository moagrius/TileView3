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

import com.github.moagrius.utils.Throttler;
import com.github.moagrius.widget.ScrollView;
import com.github.moagrius.widget.ZoomScrollView;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TileView extends View implements ZoomScrollView.ScaleChangedListener, ScrollView.ScrollChangedListener {

  private BitmapFactory.Options mBitmapOptions = new BitmapFactory.Options();

  {
    //https://developer.android.com/reference/android/graphics/BitmapFactory.Options.html#inTempStorage
    mBitmapOptions.inPreferredConfig = Bitmap.Config.RGB_565;
    mBitmapOptions.inTempStorage = new byte[16 * 1024];
    mBitmapOptions.inSampleSize = 1;
  }

  private float mScale = 1f;

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
    //int layerType = (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) ? LAYER_TYPE_SOFTWARE : LAYER_TYPE_HARDWARE;
    //setLayerType(layerType, null);
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
    mScale = scale;
    Log.d("DL", "setting scale: " + scale);
    int previous = mBitmapOptions.inSampleSize;
    mBitmapOptions.inSampleSize = 1;
    float current = 1f;
    float divisor = 2f;
    while (true) {
      float next = current / divisor;
      if (next < scale) {
        break;
      }
      mBitmapOptions.inSampleSize <<= 1;
      current = next;
    }
    if (mBitmapOptions.inSampleSize != previous) {
      Log.d("DL", "clearing tiles");
      mTilesVisibleInViewport.clear();
    }
    invalidate();
    Log.d("DL", "sample: " + mBitmapOptions.inSampleSize);
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
  protected void onDraw(Canvas canvas) {
    canvas.scale(mScale, mScale);
    for (Tile tile : mTilesVisibleInViewport) {
      tile.draw(canvas);
    }
  }

  private void updateViewportAndComputeTilesThrottled() {
    mThrottler.attempt(mUpdateAndComputeTilesRunnable);
  }

  private void updateViewport() {
    int left = mZoomScrollView.getScrollX();
    int top = mZoomScrollView.getScrollY();
    int visibleRight = left + mZoomScrollView.getMeasuredWidth();
    int visibleBottom = top + mZoomScrollView.getMeasuredHeight();
    int actualRight = getWidth();
    int actualBottom = getHeight();
    int right = Math.min(visibleRight, actualRight);
    int bottom = Math.min(visibleBottom, actualBottom);
    mViewport.set(left, top, right, bottom);
  }

  public Grid getCellGridFromViewport() {
    float tileSize = Tile.TILE_SIZE * mScale;
    int sample = mBitmapOptions.inSampleSize;
    // force rows and columns to be in increments equal to sample size...
    // round down the start and round up the end to make sure we cover the screen
    // e.g. rows 7:18 with sample size 4 become 4:20
    // this is to make sure that the cells are recognized as whole units and not redrawn when the viewport moves by a distance smaller than a computed tile
    Grid grid = new Grid();
    grid.rows.start = (int) Math.floor((mViewport.top / tileSize) / sample) * sample;
    grid.rows.end = (int) Math.ceil((mViewport.bottom / tileSize) / sample) * sample;
    grid.columns.start = (int) Math.floor((mViewport.left / tileSize) / sample) * sample;
    grid.columns.end = (int) Math.ceil((mViewport.right / tileSize) / sample) * sample;
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
        // TODO: this seems like it should be somewhere else - e.g., only applied if necessary
        tile.addDetailLevel(4, "tiles/phi-125000-%1$d_%2$d.jpg");
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
  }

  private static class Range {
    int start;
    int end;
  }

  public interface Cache {
    Bitmap get(String key);
    Bitmap put(String key, Bitmap value);
  }

}
