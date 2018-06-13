package com.github.moagrius.tileview.plugins;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.github.moagrius.tileview.TileView;

public class MarkerPlugin extends ViewGroup implements Plugin, TileView.Listener {

  private TileView mTileView;
  private float mScale = 1;

  public MarkerPlugin(@NonNull Context context) {
    super(context);
  }

  @Override
  public void install(TileView tileView) {
    mTileView = tileView;
    mTileView.setListener(this);
    mTileView.addOnAttachStateChangeListener(mOnAttachStateChangeListener);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    measureChildren(widthMeasureSpec, heightMeasureSpec);
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      if (child.getVisibility() != GONE) {
        MarkerPlugin.LayoutParams layoutParams = (MarkerPlugin.LayoutParams) child.getLayoutParams();
        // actual sizes of children
        int actualWidth = child.getMeasuredWidth();
        int actualHeight = child.getMeasuredHeight();
        // calculate combined anchor offsets
        float widthOffset = actualWidth * layoutParams.relativeAnchorX + layoutParams.absoluteAnchorX;
        float heightOffset = actualHeight * layoutParams.relativeAnchorY + layoutParams.absoluteAnchorY;
        // get offset position
        int scaledX = (int) (layoutParams.x * mScale);
        int scaledY = (int) (layoutParams.y * mScale);
        // save computed values
        layoutParams.mLeft = (int) (scaledX + widthOffset);
        layoutParams.mTop = (int) (scaledY + heightOffset);
        layoutParams.mRight = layoutParams.mLeft + actualWidth;
        layoutParams.mBottom = layoutParams.mTop + actualHeight;
      }
    }
    Log.d("TV", "mode = " + MeasureSpec.getMode(widthMeasureSpec));
    int availableWidth = MeasureSpec.getSize(widthMeasureSpec);
    int availableHeight = MeasureSpec.getSize(heightMeasureSpec);
    Log.d("TV", "availableWidth = " + availableWidth + ", availableHeight = " + availableHeight);
    ViewGroup parent = (ViewGroup) getParent();
    Log.d("TV", "parent width = " + parent.getMeasuredWidth() + ", " + parent.getWidth());
    int resolvedWidth = resolveSize(availableWidth, widthMeasureSpec);
    Log.d("TV", "resolved width = " + resolvedWidth);
    setMeasuredDimension(availableWidth, availableHeight);
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      if (child.getVisibility() != GONE) {
        LayoutParams layoutParams = (LayoutParams) child.getLayoutParams();
        child.layout(layoutParams.mLeft, layoutParams.mTop, layoutParams.mRight, layoutParams.mBottom);
      }
    }
  }

  private void reposition() {
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      if (child.getVisibility() != GONE) {
        MarkerPlugin.LayoutParams layoutParams = (MarkerPlugin.LayoutParams) child.getLayoutParams();
        // actual sizes of children
        int actualWidth = child.getMeasuredWidth();
        int actualHeight = child.getMeasuredHeight();
        // calculate combined anchor offsets
        float widthOffset = actualWidth * layoutParams.relativeAnchorX + layoutParams.absoluteAnchorX;
        float heightOffset = actualHeight * layoutParams.relativeAnchorY + layoutParams.absoluteAnchorY;
        // get offset position
        int scaledX = (int) (layoutParams.x * mScale);
        int scaledY = (int) (layoutParams.y * mScale);
        // save computed values
        layoutParams.mLeft = (int) (scaledX + widthOffset);
        layoutParams.mTop = (int) (scaledY + heightOffset);
        layoutParams.mRight = layoutParams.mLeft + actualWidth;
        layoutParams.mBottom = layoutParams.mTop + actualHeight;
        // update
        child.setLeft(layoutParams.mLeft);
        child.setTop(layoutParams.mTop);
      }
    }
  }

  private OnAttachStateChangeListener mOnAttachStateChangeListener = new OnAttachStateChangeListener() {

    @Override
    public void onViewAttachedToWindow(View v) {
      mTileView.removeOnAttachStateChangeListener(this);
      // if the TileView is an immediate child of a ZoomScrollView, we need to substitute a FrameLayout
      Plugins.wedge(mTileView, MarkerPlugin.this);
    }

    @Override
    public void onViewDetachedFromWindow(View v) {

    }
  };

  @Override
  public void onScaleChanged(float scale, float previous) {
    mScale = scale;
    reposition();
  }

  @Override
  public void onScrollChanged(int x, int y) {

  }

  @Override
  public void onReady(TileView tileView) {

  }

  public void addMarker(View view, int left, int top, float relativeAnchorLeft, float relativeAnchorTop, float absoluteAnchorLeft, float absoluteAnchorTop) {
    LayoutParams layoutParams = new MarkerPlugin.LayoutParams(
        LayoutParams.WRAP_CONTENT,
        LayoutParams.WRAP_CONTENT,
        left, top,
        relativeAnchorLeft, relativeAnchorTop,
        absoluteAnchorLeft, absoluteAnchorTop);
    addView(view, layoutParams);
  }

  public static class LayoutParams extends ViewGroup.LayoutParams {

    public int x;
    public int y;
    public float relativeAnchorX;
    public float relativeAnchorY;
    public float absoluteAnchorX;
    public float absoluteAnchorY;

    private int mTop;
    private int mLeft;
    private int mBottom;
    private int mRight;

    public LayoutParams(int width, int height, int left, int top, float relativeAnchorLeft, float relativeAnchorTop, float absoluteAnchorLeft, float absoluteAnchorTop) {
      super(width, height);
      x = left;
      y = top;
      relativeAnchorX = relativeAnchorLeft;
      relativeAnchorY = relativeAnchorTop;
      absoluteAnchorX = absoluteAnchorLeft;
      absoluteAnchorY = absoluteAnchorTop;
    }

  }

}
