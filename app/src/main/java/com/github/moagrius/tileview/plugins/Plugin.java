package com.github.moagrius.tileview.plugins;

import android.graphics.Canvas;
import android.view.MotionEvent;

import com.github.moagrius.tileview.TileView;

public interface Plugin {
  void install(TileView tileView);
  default void onPrepared(TileView tileView) {}
  default void onTouched(TileView tileView, MotionEvent event) {}
  default void onTilesDrawn(TileView tileView, Canvas canvas) {}
}
