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
    mZoomScrollView.setScrollChangedListener(this);
    mZoomScrollView.setScrollChangedListener(this);
    updateViewportAndComputeTilesThrottled();
  }

  public float getScale() {
    return mScale;
  }

  public void setScale(float scale) {
    mScale = scale;
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
      mTilesVisibleInViewport.clear();
    }
    Log.d("DL", "sample: ${bitmapOptions.inSampleSize}");
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
    invalidate();
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
    mViewport.set(
        mZoomScrollView.getScrollX(),
        mZoomScrollView.getScrollY(),
        mZoomScrollView.getMeasuredWidth() + mZoomScrollView.getScrollX(),
        mZoomScrollView.getMeasuredHeight() + mZoomScrollView.getScrollY()
    );
  }

  private void computeTilesInCurrentViewport() {
    mNewlyVisibleTiles.clear();
    float tileSize = KTile.TILE_SIZE * mScale;
    int rowStart = (int) Math.floor(mViewport.top / tileSize);
    int rowEnd = (int) Math.ceil(mViewport.bottom / tileSize);
    int columnStart = (int) Math.floor(mViewport.left / tileSize);
    int columnEnd = (int) Math.ceil(mViewport.right / tileSize);
    //Log.d("T", "$rowStart, $rowEnd, $columnStart, $columnEnd")
    int sample = mBitmapOptions.inSampleSize;
    for (int row = rowStart; row < rowEnd; row++) {
      if (row % sample != 0) {
        continue;
      }
      for (int column = columnStart; column < columnEnd; column++) {
        if (column % sample != 0) {
          continue;
        }
        Tile tile = new Tile();
        tile.setOptions(mBitmapOptions);
        tile.setStartColumn(column);
        tile.setStartRow(row);
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
            tile.decode(getContext(), mMemoryCache);
            postInvalidate();
          } catch (Exception e) {
            Log.d("TV", "exception decoding: ${e.javaClass}, ${e.message}");
          }
        });
      }
    }
  }


  public interface Cache {
    Bitmap get(String key);
    Bitmap put(String key, Bitmap value);
  }

}
