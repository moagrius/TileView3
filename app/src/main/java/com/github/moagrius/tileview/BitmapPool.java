package com.github.moagrius.tileview;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class BitmapPool {

  private final Set<SoftReference<Bitmap>> mReusableBitmaps = Collections.synchronizedSet(new HashSet<SoftReference<Bitmap>>());

  public synchronized Bitmap get(BitmapFactory.Options options) {
    Bitmap bitmap = null;
    if (mReusableBitmaps != null && !mReusableBitmaps.isEmpty()) {
      synchronized (mReusableBitmaps) {
        final Iterator<SoftReference<Bitmap>> iterator = mReusableBitmaps.iterator();
        Bitmap item;

        while (iterator.hasNext()) {
          item = iterator.next().get();

          if (null != item && item.isMutable()) {
            // Check to see it the item can be used for inBitmap.
            if (canUseForInBitmap(item, options)) {
              bitmap = item;

              // Remove from reusable set so it can't be used again.
              iterator.remove();
              break;
            }
          } else {
            // Remove from the set if the reference has been cleared.
            iterator.remove();
          }
        }
      }
    }
    return bitmap;

  }

  public void add(Bitmap bitmap) {
    mReusableBitmaps.add(new SoftReference<>(bitmap));
  }

  private static int BYTE_COUNT = Tile.TILE_SIZE * Tile.TILE_SIZE * 2; // "2" is for RGB_565
  private static boolean canUseForInBitmap(Bitmap candidate, BitmapFactory.Options targetOptions) {

    if (candidate == null) {
      return false;
    }

    if (!candidate.isMutable()) {
      return false;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      // From Android 4.4 (KitKat) onward we can re-use if the byte size of
      // the new bitmap is smaller than the reusable bitmap candidate
      // allocation byte count.
      //int byteCount = TILE_SIZE * TILE_SIZE * getBytesPerPixel(candidate.getConfig());
      return BYTE_COUNT <= candidate.getAllocationByteCount();
    }

    // On earlier versions, the dimensions must match exactly and the inSampleSize must be 1
    return candidate.getWidth() == Tile.TILE_SIZE
        && candidate.getHeight() == Tile.TILE_SIZE
        && targetOptions.inSampleSize == 1;
  }

}
