package com.github.moagrius.tileview.plugins;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.MotionEvent;
import android.view.View;

import com.github.moagrius.tileview.TileView;

public class InfoWindowPlugin extends MarkerPlugin implements TileView.TouchListener {

  public InfoWindowPlugin(@NonNull Context context) {
    super(context);
  }

  @Override
  public void install(TileView tileView) {
    super.install(tileView);
    tileView.addTouchListener(this);
    bringToFront();
  }

  public void show(View view, int x, int y, float anchorX, float anchorY, float offsetX, float offsetY) {
    addMarker(view, x, y, anchorX, anchorY, offsetX, offsetY);
  }

  @Override
  public void onTouch(MotionEvent event) {
    removeAllViews();
  }

}
