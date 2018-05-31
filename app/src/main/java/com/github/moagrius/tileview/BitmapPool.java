package com.github.moagrius.tileview;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BitmapPool implements IBitmapPool {

  private IBitmapPool mUpstream;

  public BitmapPool() {
    mUpstream = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
        ? new ModernBitmapPool()
        : new LegacyBitmapPool();
  }

  public synchronized Bitmap get(Tile tile) {
    return mUpstream.get(tile);
  }

  public synchronized void add(Bitmap bitmap) {
    if (bitmap == null) {
      return;
    }
    if (bitmap.getConfig() == null) {
      return;
    }
    mUpstream.add(bitmap);
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  private static class ModernBitmapPool implements IBitmapPool {

    // TODO: map to config
    private Map<Long, Queue<SoftReference<Bitmap>>> mPoolsMap = Collections.synchronizedMap(new HashMap<>());

    @Override
    public Bitmap get(Tile tile) {
      BitmapFactory.Options options = tile.getOptions();
      int width = options.outWidth / options.inSampleSize;
      int height = options.outHeight / options.inSampleSize;
      long size = width * height * getBytesPerPixel(options.inPreferredConfig);
      Log.d("BP", "trying to get bitmap from pool at size " + size);
      // if we have the exact size, use that
      if (mPoolsMap.containsKey(size)) {
        Bitmap bitmap = getBitmapForSize(size);
        if (bitmap != null) {
          return bitmap;
        }
      }
      // if we couldn't find an exact key match, check all other keys for a larger size
      for (Map.Entry<Long, Queue<SoftReference<Bitmap>>> entry : mPoolsMap.entrySet()) {
        long key = entry.getKey();
        if (key > size) {
          Bitmap bitmap = getBitmapForSize(key);
          if (bitmap != null) {
            return bitmap;
          }
        }
      }
      return null;
    }

    private Bitmap getBitmapForSize(long size) {
      Queue<SoftReference<Bitmap>> bitmaps = mPoolsMap.get(size);
      if (bitmaps.peek() != null) {
        Bitmap bitmap = bitmaps.poll().get();
        if (bitmap != null) {
          return bitmap;
        }
      }
      return null;
    }

    private int getBytesPerPixel(Bitmap.Config config) {
      switch (config) {
        case ARGB_8888:
          return 4;
        case RGB_565:
        case ARGB_4444:
          return 2;
      }
      return 1;
    }

    @Override
    public void add(Bitmap bitmap) {
      long size = bitmap.getAllocationByteCount();
      Log.d("BP", "adding bitmap to pool at size " + size);
      if (!mPoolsMap.containsKey(size)) {
        mPoolsMap.put(size, new ConcurrentLinkedQueue<>());
      }
      Queue<SoftReference<Bitmap>> bitmaps = mPoolsMap.get(size);
      bitmaps.add(new SoftReference<>(bitmap));
    }
  }

  private static class LegacyBitmapPool implements IBitmapPool {

    private final Set<SoftReference<Bitmap>> mReusableBitmaps = Collections.synchronizedSet(new HashSet<SoftReference<Bitmap>>());

    @Override
    public Bitmap get(Tile tile) {
      if (tile.getImageSample() != 1) {
        return null;
      }
      if (mReusableBitmaps.isEmpty()) {
        return null;
      }
      BitmapFactory.Options options = tile.getOptions();
      Iterator<SoftReference<Bitmap>> iterator = mReusableBitmaps.iterator();
      while (iterator.hasNext()) {
        Bitmap bitmap = iterator.next().get();
        if (bitmap == null || !bitmap.isMutable()) {
          iterator.remove();
          continue;
        }
        if (bitmap.getWidth() == options.outWidth && bitmap.getHeight() == options.outHeight) {
          iterator.remove();
          return bitmap;
        }
      }
      return null;
    }

    @Override
    public void add(Bitmap bitmap) {
      if (bitmap != null) {
        mReusableBitmaps.add(new SoftReference<>(bitmap));
      }
    }

  }

}
