package com.github.moagrius.tileview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.InputStream;
import java.util.Locale;

public class BitmapProviderAssets implements BitmapProvider {

  private TileOptions mOptions = new TileOptions();

  // TODO: try with Picasso https://github.com/moagrius/TileView#use-a-third-party-image-loading-library-like-picasso-glide-uil-etc
  @Override
  public Bitmap getBitmap(Context context, Detail detail, int sample, int row, int column) throws Exception {
    String template = detail.getData();
    String file = String.format(Locale.US, template, row, column);
    mOptions.inSampleSize = sample;
    InputStream stream = context.getAssets().open(file);
    return BitmapFactory.decodeStream(stream, null, mOptions);
  }

}
