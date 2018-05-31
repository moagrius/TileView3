package com.github.moagrius.tileview;

import android.graphics.Bitmap;
import android.util.LruCache;

public class MemoryCache extends LruCache<String, Bitmap> implements TileView.Cache {

  private BitmapPool mBitmapPool;

  public MemoryCache(int maxSize) {
    super(maxSize);
  }

  public void setBitmapPool(BitmapPool bitmapPool) {
    mBitmapPool = bitmapPool;
  }

  @Override
  protected int sizeOf(String key, Bitmap bitmap) {
     return bitmap.getByteCount() / 1024;
  }

  @Override
  protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
    synchronized (this) {
      mBitmapPool.add(oldValue);
    }
  }

}
