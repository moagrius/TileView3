package com.github.moagrius.tileview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.github.moagrius.utils.Throttler;
import com.github.moagrius.widget.ScrollView;
import com.github.moagrius.widget.ZoomScrollView;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author Mike Dunn, 2/3/18.
 */

public class TileView extends View implements
  ZoomScrollView.ScaleChangedListener,
  ZoomScrollView.ScrollChangedListener {

  private float mScale = 1f;

  private ZoomScrollView mZoomScrollView;

  private Rect mViewport = new Rect();
  private Set<Tile> mNewlyVisibleTiles = new HashSet<>();
  private Set<Tile> mTilesVisibleInViewport = new HashSet<>();

  private Executor mExecutor = Executors.newFixedThreadPool(3);
  private Throttler mRenderThrottle = new Throttler(50);

  public TileView(Context context) {
    this(context, null);
  }

  public TileView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public TileView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void setZoomScrollView(ZoomScrollView zoomScrollView) {
    mZoomScrollView = zoomScrollView;
    mZoomScrollView.setScrollChangedListener(this);
    mZoomScrollView.setScaleChangedListener(this);
    updateViewportAndComputeTilesThrottled();
  }

  @Override
  public void onScaleChanged(ZoomScrollView zoomScrollView, float currentScale, float previousScale) {
    mScale = currentScale;
    updateViewportAndComputeTilesThrottled();
    invalidate();
  }

  @Override
  public void onScrollChanged(ScrollView scrollView, int x, int y) {
    Log.d("T", "onScrollChanged");
    updateViewportAndComputeTilesThrottled();
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    updateViewportAndComputeTilesThrottled();
  }

  @Override
  public void onDraw(Canvas canvas) {
    Log.d("T", "onDraw");
    for (Tile tile : mTilesVisibleInViewport) {
      if (tile.getBitmap() != null) {
        canvas.drawBitmap(tile.getBitmap(), tile.getX(), tile.getY(), null);
      }
    }
  }

  private Runnable mUpdateAndComputeTilesRunnable = new Runnable() {
    @Override
    public void run() {
      updateViewport();
      computeTilesInCurrentViewport();
    }
  };

  private void updateViewportAndComputeTilesThrottled() {
    mRenderThrottle.attempt(mUpdateAndComputeTilesRunnable);
  }

  private void updateViewport() {
    mViewport.set(
      mZoomScrollView.getScrollX(),
      mZoomScrollView.getScrollY(),
      mZoomScrollView.getMeasuredWidth() + mZoomScrollView.getScrollX(),
      mZoomScrollView.getMeasuredHeight() + mZoomScrollView.getScrollY());
  }

  private void computeTilesInCurrentViewport() {
    Log.d("T", "computeTilesInCurrentViewport");
    Log.d("T", "current tile count: " + mTilesVisibleInViewport.size());
    mNewlyVisibleTiles.clear();
    int tileSize = 256;
    int rowStart = (int) Math.floor(mViewport.top / tileSize);
    int rowEnd = (int) Math.ceil(mViewport.bottom / tileSize);
    int columnStart = (int) Math.floor(mViewport.left / tileSize);
    int columnEnd = (int) Math.ceil(mViewport.right / tileSize);
    Log.d("T", rowStart + ", " + rowEnd + ", " + columnStart + ", " + columnEnd);
    for( int rowCurrent = rowStart; rowCurrent <= rowEnd; rowCurrent++ ) {
      for( int columnCurrent = columnStart; columnCurrent <= columnEnd; columnCurrent++ ) {
        final Tile tile = new Tile();
        tile.setColumn(columnCurrent);
        tile.setRow(rowCurrent);
        mNewlyVisibleTiles.add(tile);
      }
    }
    Log.d("T", "newly visible: " + mNewlyVisibleTiles.size());
    Iterator<Tile> previousAndCurrentlyVisibleTileIterator = mTilesVisibleInViewport.iterator();
    while (previousAndCurrentlyVisibleTileIterator.hasNext()) {
      Tile tile = previousAndCurrentlyVisibleTileIterator.next();
      if (!mNewlyVisibleTiles.contains(tile)) {
        previousAndCurrentlyVisibleTileIterator.remove();
      }
    }
    for (final Tile tile : mNewlyVisibleTiles) {
      boolean added = mTilesVisibleInViewport.add(tile);
      if (added) {
        mExecutor.execute(new Runnable() {
          @Override
          public void run() {
            tile.decode(TileView.this.getContext());
            postInvalidate();
          }
        });
      }
    }
    Log.d("T", "current tile count: " + mTilesVisibleInViewport.size());
  }
  

}
