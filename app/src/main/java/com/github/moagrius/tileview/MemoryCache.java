package com.github.moagrius.tileview;

import android.graphics.Bitmap;
import android.util.LruCache;

public class MemoryCache extends LruCache<Object, Bitmap> implements Tile.Cache {

  public MemoryCache(int maxSize) {
    super(maxSize);
  }

  @Override
  protected int sizeOf(Object key, Bitmap bitmap) {
     return bitmap.getByteCount() / 1024;
  }

}
