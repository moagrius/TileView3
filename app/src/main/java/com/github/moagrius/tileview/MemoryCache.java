package com.github.moagrius.tileview;

import android.graphics.Bitmap;
import android.util.LruCache;

public class MemoryCache extends LruCache<String, Bitmap> implements TileView.Cache {

  public MemoryCache(int maxSize) {
    super(maxSize);
  }

  @Override
  protected int sizeOf(String key, Bitmap bitmap) {
     return bitmap.getByteCount() / 1024;
  }

}
