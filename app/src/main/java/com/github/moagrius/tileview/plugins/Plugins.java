package com.github.moagrius.tileview.plugins;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.github.moagrius.tileview.TileView;
import com.github.moagrius.widget.ZoomScrollView;

/**
 * Helper class for Plugins
 */
public class Plugins {

  public static void wedge(TileView tileView, View view) {
    if (tileView.getParent() instanceof ZoomScrollView) {
      Log.d("TV", "parent is a ZoomScrollView, create an intermediary and move children");
      ViewGroup zoomScrollView = (ViewGroup) tileView.getParent();
      zoomScrollView.removeView(tileView);
      // intermediary should wrap content, since TileView should supply the size
      ViewGroup intermediary = new LargestChildLayout(tileView.getContext());
      intermediary.addView(tileView);
      intermediary.addView(view);
      ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(MarkerPlugin.LayoutParams.WRAP_CONTENT, MarkerPlugin.LayoutParams.WRAP_CONTENT);
      zoomScrollView.addView(intermediary, layoutParams);
    } else {
      Log.d("TV", "parent is not a ZoomScrollView, assume we already created intermediary, add marker layout to it");
      ((ViewGroup) tileView.getParent()).addView(view, new FrameLayout.LayoutParams(MarkerPlugin.LayoutParams.MATCH_PARENT, MarkerPlugin.LayoutParams.MATCH_PARENT));
    }
  }

  public static class LargestChildLayout extends ViewGroup {

    private int mWidth;
    private int mHeight;

    public LargestChildLayout(Context context) {
      super(context);
    }

    public LargestChildLayout(Context context, AttributeSet attrs) {
      super(context, attrs);
    }

    public LargestChildLayout(Context context, AttributeSet attrs, int defStyleAttr) {
      super(context, attrs, defStyleAttr);
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
      for (int i = 0; i < getChildCount(); i++) {
        View child = getChildAt(i);
        mWidth = Math.max(mWidth, child.getMeasuredWidth());
        mHeight = Math.max(mHeight, child.getMeasuredHeight());
      }
      setMeasuredDimension(mWidth, mHeight);
    }

  }

}
