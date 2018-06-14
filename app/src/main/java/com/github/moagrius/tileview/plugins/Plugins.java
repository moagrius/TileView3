package com.github.moagrius.tileview.plugins;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;

import com.github.moagrius.tileview.TileView;

/**
 * Helper class for Plugins
 */
public class Plugins {

  public static void wrap(TileView tileView, View view) {
    if (tileView.getParent() instanceof TileViewWrapper) {
      Log.d("TV", "parent is not a ZoomScrollView, assume we already created intermediary, add marker layout to it");
      ((ViewGroup) tileView.getParent()).addView(view);
    } else {
      Log.d("TV", "parent is a presumably a ZoomScrollView, create an intermediary and move children");
      ViewGroup originalParent = (ViewGroup) tileView.getParent();
      originalParent.removeView(tileView);
      // intermediary should wrap content, since TileView should supply the size
      ViewGroup intermediary = new TileViewWrapper(tileView);
      intermediary.addView(tileView);
      intermediary.addView(view);
      LayoutParams layoutParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
      originalParent.addView(intermediary, layoutParams);
    }
  }

  private static class TileViewWrapper extends ViewGroup {

    private TileView mTileView;

    public TileViewWrapper(TileView tileView) {
      super(tileView.getContext());
      mTileView = tileView;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
      int width = mTileView.getMeasuredWidth();
      int height = mTileView.getMeasuredHeight();
      for (int i = 0; i < getChildCount(); i++) {
        View child = getChildAt(i);
        child.layout(0, 0, width, height);
      }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
      measureChildren(widthMeasureSpec, heightMeasureSpec);
      setMeasuredDimension(mTileView.getMeasuredWidth(), mTileView.getMeasuredHeight());
    }

  }

}
