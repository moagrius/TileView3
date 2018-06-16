package com.github.moagrius.tileview.plugins;

import android.widget.ImageView;

import com.github.moagrius.tileview.TileView;

public class LowFidelityBackgroundPlugin implements TileView.Plugin {
  @Override
  public void install(TileView tileView) {
    ImageView imageView = new ImageView(tileView.getContext());

    tileView.getContainer().addView(imageView, 0);
  }
}
