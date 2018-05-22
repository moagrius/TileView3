package com.github.moagrius.tileview;

import android.content.Context;
import android.graphics.Bitmap;

public interface BitmapProvider {
  Bitmap getBitmap(Context context, Detail detail, int sample, int column, int row) throws Exception;
}
