package com.github.moagrius.tileview;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class TileOptions extends BitmapFactory.Options {

  //https://developer.android.com/reference/android/graphics/BitmapFactory.Options.html#inTempStorage
  private static final byte[] sInTempStorage = new byte[16 * 1024];

  {
    inMutable = true;
    inPreferredConfig = Bitmap.Config.RGB_565;
    inTempStorage = sInTempStorage;
    inSampleSize = 1;
  }

}
