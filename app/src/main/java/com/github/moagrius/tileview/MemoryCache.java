package com.github.moagrius.tileview;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class MemoryCache implements TileView.Cache {

  private LinkedHashMap<String, Bitmap> mMap = new LinkedHashMap<>(0, 0.75f, true);
  private Set<String> mEmployed = new HashSet<>();
  private int mMaxSize;
  private int mSize;

  public MemoryCache(int maxSize) {
    mMaxSize = maxSize;
  }

  public synchronized Bitmap get(String key) {
    return mMap.get(key);
  }

  public synchronized void setEmployed(String key, boolean employed) {
    if (employed) {
      mEmployed.add(key);
    } else {
      mEmployed.remove(key);
    }
  }

  public synchronized Bitmap put(String key, Bitmap value) {
    if (key == null || value == null) {
      throw new NullPointerException("key == null || value == null");
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
    mMap.remove(key);
  }

  private void trimToSize(int maxSize) {
    while (mSize > maxSize && !mMap.isEmpty()) {
      Map.Entry<String, Bitmap> oldest = getOldest();
      if (oldest == null) {
        break;
      }
      mMap.remove(oldest.getKey());
      mSize -= sizeOf(oldest.getValue());
    }
  }

  private Map.Entry<String, Bitmap> getOldest() {
    if (mMap.isEmpty()) {
      return null;
    }
    return mMap.entrySet().iterator().next();
  }

  private int sizeOf(Bitmap bitmap) {
     return bitmap.getByteCount() / 1024;
  }

  public synchronized Bitmap getBitmapForReuse(BitmapFactory.Options options) {
    Iterator<Map.Entry<String, Bitmap>> iterator = mMap.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, Bitmap> entry = iterator.next();
      if (entry == null) {
        break;
      }
      String key = entry.getKey();
      if (mEmployed.contains(key)) {
        continue;
      }
      Bitmap candidate = entry.getValue();
      if (canUseForInBitmap(candidate, options)) {
        iterator.remove();
        return candidate;
      }
    }
    return null;
  }

  private static boolean canUseForInBitmap(Bitmap candidate, BitmapFactory.Options targetOptions) {

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      // From Android 4.4 (KitKat) onward we can re-use if the byte size of
      // the new bitmap is smaller than the reusable bitmap candidate
      // allocation byte count.
      int width = targetOptions.outWidth / targetOptions.inSampleSize;
      int height = targetOptions.outHeight / targetOptions.inSampleSize;
      int byteCount = width * height * getBytesPerPixel(candidate.getConfig());
      return byteCount <= candidate.getAllocationByteCount();
    }

    // On earlier versions, the dimensions must match exactly and the inSampleSize must be 1
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
