package com.github.moagrius.tileview;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;

public class Tile {

  private int mStartRow;
  private int mStartColumn;
  private BitmapFactory.Options mOptions;

  public void setStartRow(int startRow) {
    mStartRow = startRow;
  }

  public void setStartColumn(int startColumn) {
    mStartColumn = startColumn;
  }

  public void setOptions(BitmapFactory.Options options) {
    mOptions = options;
  }

  public void decode(Context context, TileView.Cache cache) {

  }

  public void draw(Canvas canvas) {

  }
}
