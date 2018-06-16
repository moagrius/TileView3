package com.github.moagrius.tileview.plugins;

import android.content.Context;
import android.support.annotation.NonNull;

import com.github.moagrius.tileview.TileView;

public class InfoWindowPlugin extends MarkerPlugin {

  public InfoWindowPlugin(@NonNull Context context) {
    super(context);
  }

  @Override
  public void install(TileView tileView) {
    super.install(tileView);
    bringToFront();
  }

  @Override
  public void onScaleChanged(float scale, float previous) {
    super.onScaleChanged(scale, previous);
    removeAllViews();
  }

  @Override
  public void onScrollChanged(int x, int y) {
    super.onScrollChanged(x, y);
    removeAllViews();
  }

}
