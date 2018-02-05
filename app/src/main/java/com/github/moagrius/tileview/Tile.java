package com.github.moagrius.tileview;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.support.v4.util.LruCache;

import com.github.moagrius.utils.Hashes;

import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * @author Mike Dunn, 2/4/18.
 */

public class Tile {

  private static final byte[] sDecodeBuffer = new byte[16 * 1024];
  private static final BitmapFactory.Options OPTIONS = new BitmapFactory.Options();

  static {
    OPTIONS.inPreferredConfig = Bitmap.Config.RGB_565;
    OPTIONS.inTempStorage = new byte[16 * 1024];
  }

  public enum State {IDLE, DECODING, DECODED}

  private Bitmap mBitmap;
  private State mState = State.IDLE;

  private int mRow;
  private int mColumn;

  private float mX;
  private float mY;

  public Bitmap getBitmap() {
    return mBitmap;
  }

  public int getColumn() {
    return mColumn;
  }

  public void setColumn(int column) {
    mColumn = column;
    mX = mColumn * 256;
  }

  public int getRow() {
    return mRow;
  }

  public void setRow(int row) {
    mRow = row;
    mY = mRow * 256;
  }

  public float getX() {
    return mX;
  }

  public float getY() {
    return mY;
  }

  public void decode(Context context) {
    if (mState != State.IDLE) {
      return;
    }
    mState = State.DECODING;
    String formattedFileName = String.format(Locale.US, "tiles/phi-500000-%d_%d.jpg", mColumn, mRow);
    AssetManager assetManager = context.getAssets();
    try {
      InputStream inputStream = assetManager.open(formattedFileName);
      if (inputStream != null) {
        try {
          BitmapFactory.Options options = new BitmapFactory.Options();
          options.inTempStorage = sDecodeBuffer;
          options.inPreferredConfig = Bitmap.Config.RGB_565;
          addInBitmapOptions(options);
          mBitmap = BitmapFactory.decodeStream(inputStream, null, OPTIONS);
          mState = State.DECODED;
        } catch (OutOfMemoryError | Exception e) {
          // this is probably an out of memory error - you can try sleeping (this method won't be called in the UI thread) or try again (or give up)
        }
      }
    } catch (Exception e) {
      // this is probably an IOException, meaning the file can't be found
    }
  }

  public void clear() {
    mBitmap = null;
    mState = State.IDLE;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Tile) {
      Tile compare = (Tile) o;
      return compare.getColumn() == mColumn && compare.getRow() == mRow;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Hashes.compute(17, 31, mColumn, mRow);
  }


  ///// reusable
  Set<SoftReference<Bitmap>> mReusableBitmaps = Collections.synchronizedSet(new HashSet<SoftReference<Bitmap>>());
  private LruCache<String, BitmapDrawable> mMemoryCache = new LruCache<String, BitmapDrawable>(1024 * 1024) {
    @Override
    protected void entryRemoved(boolean evicted, String key, BitmapDrawable oldValue, BitmapDrawable newValue) {
      mReusableBitmaps.add(new SoftReference<>(oldValue.getBitmap()));
    }
  };

  private void addInBitmapOptions(BitmapFactory.Options options) {
    // inBitmap only works with mutable bitmaps, so force the decoder to
    // return mutable bitmaps.
    options.inMutable = true;


    // Try to find a bitmap to use for inBitmap.
    Bitmap inBitmap = getBitmapFromReusableSet(options);

    if (inBitmap != null) {
      // If a suitable bitmap has been found, set it as the value of
      // inBitmap.
      options.inBitmap = inBitmap;
    }

  }

  // This method iterates through the reusable bitmaps, looking for one
// to use for inBitmap:
  protected Bitmap getBitmapFromReusableSet(BitmapFactory.Options options) {
    Bitmap bitmap = null;

    if (mReusableBitmaps != null && !mReusableBitmaps.isEmpty()) {
      synchronized (mReusableBitmaps) {
        final Iterator<SoftReference<Bitmap>> iterator
          = mReusableBitmaps.iterator();
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

  static boolean canUseForInBitmap(
    Bitmap candidate, BitmapFactory.Options targetOptions) {

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

  /**
   * A helper function to return the byte usage per pixel of a bitmap based on its configuration.
   */
  static int getBytesPerPixel(Bitmap.Config config) {
    if (config == Bitmap.Config.ARGB_8888) {
      return 4;
    } else if (config == Bitmap.Config.RGB_565) {
      return 2;
    } else if (config == Bitmap.Config.ARGB_4444) {
      return 2;
    } else if (config == Bitmap.Config.ALPHA_8) {
      return 1;
    }
    return 1;
  }

  ///// end reusable

}
