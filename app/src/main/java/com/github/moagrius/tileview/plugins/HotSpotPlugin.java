package com.github.moagrius.tileview.plugins;

import android.view.MotionEvent;

import com.github.moagrius.tileview.TileView;

public class HotSpotPlugin implements TileView.Plugin, TileView.TouchListener {

  @Override
  public void install(TileView tileView) {
    tileView.addTouchListener(this);
  }

  @Override
  public void onTouch(MotionEvent event) {

  }
}
