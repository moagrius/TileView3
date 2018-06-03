package com.github.moagrius.tileview;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implementation of LRU cache (String to Bitmap) with a method to grab the oldest Bitmap.
 */
public class MemoryCache implements TileView.Cache {

  private LinkedHashMap<String, Bitmap> mMap = new LinkedHashMap<>(0, 0.75f, true);
  private int mMaxSize;
  private int mSize;

  public MemoryCache(int maxSize) {
    mMaxSize = maxSize;
  }

  public synchronized Bitmap get(String key) {
    return mMap.get(key);
  }

  public synchronized Bitmap put(String key, Bitmap value) {
    if (value == null) {
      return null;
    }
    mSize += sizeOf(value);
    Bitmap previous = mMap.put(key, value);
    if (previous != null) {
      mSize -= sizeOf(previous);
    }
    trimToSize(mMaxSize);
    return previous;
  }

  public synchronized void remove(String key) {
    if (mMap.containsKey(key)) {
      Bitmap bitmap = mMap.get(key);
      mSize -= sizeOf(bitmap);
      mMap.remove(key);
    }
  }

  private void trimToSize(int maxSize) {
    while (mSize > maxSize && !mMap.isEmpty()) {
      Map.Entry<String, Bitmap> oldest =  mMap.entrySet().iterator().next();
      if (oldest == null) {
        break;
      }
      mMap.remove(oldest.getKey());
      mSize -= sizeOf(oldest.getValue());
    }
  }

  private int sizeOf(Bitmap bitmap) {
    return bitmap.getByteCount() / 1024;
  }

  public synchronized Bitmap getBitmapForReuse(BitmapFactory.Options options) {
    if (mMap.isEmpty()) {
      return null;
    }
    Iterator<Bitmap> iterator = mMap.values().iterator();
    while (iterator.hasNext()) {
      Bitmap candidate = iterator.next();
      if (candidate == null) {
        Log.d("TV", "got a null bitmap when iterating memory cache, quit");
        break;
      }
      if (canUseForInBitmap(candidate, options)) {
        iterator.remove();
        mSize -= sizeOf(candidate);
        candidate.eraseColor(Color.BLACK);
        return candidate;
      }
    }
    return null;
  }

  private static boolean canUseForInBitmap(Bitmap candidate, BitmapFactory.Options targetOptions) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      int width = targetOptions.outWidth / targetOptions.inSampleSize;
      int height = targetOptions.outHeight / targetOptions.inSampleSize;
      int byteCount = width * height * getBytesPerPixel(candidate.getConfig());
      return byteCount <= candidate.getAllocationByteCount();
    }
    return candidate.getWidth() == targetOptions.outWidth
        && candidate.getHeight() == targetOptions.outHeight
        && targetOptions.inSampleSize == 1;
  }

  private static int getBytesPerPixel(Bitmap.Config config) {
    switch (config) {
      case ARGB_8888:
        return 4;
      case RGB_565:
      case ARGB_4444:
        return 2;
    }
    return 1;
  }

}
