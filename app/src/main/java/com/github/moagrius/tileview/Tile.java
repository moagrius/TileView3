package com.github.moagrius.tileview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Process;
import android.util.Log;

import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ThreadPoolExecutor;

public class Tile implements Runnable {

  public static final int TILE_SIZE = 256;

  enum State {
    IDLE, DECODING, DECODED
  }

  private int mStartRow;
  private int mStartColumn;
  private volatile State mState = State.IDLE;
  private Bitmap mBitmap;
  private int mImageSample = 1;
  private String mCacheKey;
  private Detail mDetail;
  private DrawingView mDrawingView;
  private Listener mListener;
  private Thread mThread;
  private Rect mDestinationRect = new Rect();
  private BitmapFactory.Options mDrawingOptions = new TileOptions(false);
  private BitmapFactory.Options mMeasureOptions = new TileOptions(true);
  private TileView.BitmapCache mMemoryCache;
  private TileView.BitmapCache mDiskCache;
  private TileView.BitmapPool mBitmapPool;
  private ThreadPoolExecutor mThreadPoolExecutor;

  public void setListener(Listener listener) {
    mListener = listener;
  }

  public void setThreadPoolExecutor(ThreadPoolExecutor threadPoolExecutor) {
    mThreadPoolExecutor = threadPoolExecutor;
  }

  public void setMemoryCache(TileView.BitmapCache memoryCache) {
    mMemoryCache = memoryCache;
  }

  public void setDiskCache(TileView.BitmapCache diskCache) {
    mDiskCache = diskCache;
  }

  public void setBitmapPool(TileView.BitmapPool bitmapPool) {
    mBitmapPool = bitmapPool;
  }

  public State getState() {
    return mState;
  }

  public void setStartRow(int startRow) {
    mStartRow = startRow;
  }

  public void setStartColumn(int startColumn) {
    mStartColumn = startColumn;
  }

  public void setImageSample(int imageSample) {
    mImageSample = imageSample;
    mDrawingOptions.inSampleSize = mImageSample;
    mMeasureOptions.inSampleSize = mImageSample;
  }

  public void setDetail(Detail detail) {
    mDetail = detail;
  }

  public void setDrawingView(DrawingView drawingView) {
    mDrawingView = drawingView;
  }

  public Rect getDrawingRect() {
    return mDestinationRect;
  }

  public Bitmap getBitmap() {
    return mBitmap;
  }

  public BitmapFactory.Options getDrawingOptions() {
    return mDrawingOptions;
  }

  public BitmapFactory.Options getMeasureOptions() {
    return mMeasureOptions;
  }

  private void updateDestinationRect() {
    int cellSize = TILE_SIZE * mDetail.getSample();
    int patchSize = cellSize * mImageSample;
    mDestinationRect.left = mStartColumn * cellSize;
    mDestinationRect.top = mStartRow * cellSize;
    mDestinationRect.right = mDestinationRect.left + patchSize;
    mDestinationRect.bottom = mDestinationRect.top + patchSize;
  }

  private String getFilePath() {
    String template = mDetail.getUri();
    return String.format(Locale.US, template, mStartColumn, mStartRow);
  }

  private String getCacheKey() {
    if (mCacheKey == null) {
      String normalized = getFilePath().replace(".", "_").replace("/", "_");
      mCacheKey = String.format(Locale.US, "%1$s-%2$s", normalized, mImageSample);
    }
    return mCacheKey;
  }

  // if destroyed by the time this is called, make sure bitmap stays null
  // otherwise, update state and notify drawing view
  private void setDecodedBitmap() {
    if (mState != State.DECODING) {
      mBitmap = null;
      return;
    }
    mState = State.DECODED;
    mThread = null;
    mDrawingView.setDirty(this);
  }

  private void setDecodedBitmap(Bitmap bitmap) {
    mBitmap = bitmap;
    setDecodedBitmap();
  }

  // TODO: we're assuming that sample size 1 is already on disk but if we allow BitmapProviders, then we'll need to allow that to not be the case
  protected void decode() throws Exception {
    if (mState != State.IDLE) {
      return;
    }
    mThread = Thread.currentThread();
    // this line is critical on some devices - we're doing so much work off thread that anything higher priority causes jank
    Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST);
    // putting a thread.sleep of even 100ms here shows that maybe we're doing work off screen that we should not be doing
    updateDestinationRect();
    String key = getCacheKey();
    Bitmap cached = mMemoryCache.get(key);
    if (cached != null) {
      mMemoryCache.remove(key);
      setDecodedBitmap(cached);
      return;
    }
    mState = State.DECODING;
    Context context = mDrawingView.getContext();
    // if image sample is greater than 1, we should cache the downsampled versions on disk
    boolean isSubSampled = mImageSample > 1;
    if (isSubSampled) {
      cached = mDiskCache.get(key);
      if (cached != null) {
        setDecodedBitmap(cached);
        return;
      }
      // if we're patching, we need a base bitmap to draw on
      if (mBitmap == null) {
        // let's try to use one from the cache if we have one
        // we need to fake the measurements
        mMeasureOptions.outWidth = TILE_SIZE;
        mMeasureOptions.outHeight = TILE_SIZE;
        // TODO: this is actually emulating sample size 1 - should measureOptions always be sample size 1?
        mMeasureOptions.inSampleSize = 1;
        mBitmap = mBitmapPool.getBitmapForReuse(this);
        if (mBitmap == null) {
          mBitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, mDrawingOptions.inPreferredConfig);
        }
        // can we now draw directly onto mBitmap using mDrawingOptions.inBitmap?
      }
      Canvas canvas = new Canvas(mBitmap);
      String template = mDetail.getUri();
      int size = TILE_SIZE / mImageSample;
      for (int i = 0; i < mImageSample; i++) {
        for (int j = 0; j < mImageSample; j++) {
          // if we got destroyed while decoding, drop out
          if (mState != State.DECODING) {
            return;
          }
          String file = String.format(Locale.US, template, mStartColumn + j, mStartRow + i);
          InputStream stream = context.getAssets().open(file);
          if (stream != null) {
            Bitmap piece = BitmapFactory.decodeStream(stream, null, mDrawingOptions);
            canvas.drawBitmap(piece, j * size, i * size, null);
          }
        }
      }
      setDecodedBitmap();
      mDiskCache.put(key, mBitmap);
    } else {  // no subsample means we have an explicit detail level for this scale, just use that
      String file = getFilePath();
      InputStream stream = context.getAssets().open(file);
      if (stream != null) {
        // measure it and populate measure options to pass to cache
        BitmapFactory.decodeStream(stream, null, mMeasureOptions);
        // if we made it this far, the exact bitmap wasn't in memory, but let's grab the least recently used bitmap from the cache and draw over it
        mDrawingOptions.inBitmap = mBitmapPool.getBitmapForReuse(this);
        // the measurement moved the stream's position - it must be reset to use the same stream to draw pixels
        stream.reset();
        Bitmap bitmap = BitmapFactory.decodeStream(stream, null, mDrawingOptions);
        setDecodedBitmap(bitmap);
      }
    }
  }

  // we use this signature to call from the Executor, so it can remove tiles via iterator
  public void destroy(boolean removeFromQueue) {
    if (mState == State.IDLE) {
      return;
    }
    if (mThread != null && mThread.isAlive()) {
      mThread.interrupt();
    }
    mDrawingOptions.inBitmap = null;
    if (removeFromQueue) {
      mThreadPoolExecutor.remove(this);
    }
    if (mState == State.DECODED) {
      mMemoryCache.put(getCacheKey(), mBitmap);
    }
    // since tiles are pooled and reused, make sure to reset the cache key or you'll render the wrong tile from cache
    mCacheKey = null;
    mBitmap = null;
    mState = State.IDLE;
    mListener.onTileDestroyed(this);
  }

  public void destroy() {
    destroy(true);
  }

  public void run() {
    try {
      decode();
    } catch (Exception e) {
      mListener.onTileDecodeError(this, e);
    }
  }

  public void draw(Canvas canvas) {
    if (mState == State.DECODED && mBitmap != null) {
      canvas.drawBitmap(mBitmap, null, mDestinationRect, null);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj instanceof Tile) {
      Tile compare = (Tile) obj;
      return compare.mStartColumn == mStartColumn
          && compare.mStartRow == mStartRow
          && compare.mDetail.getZoom() == mDetail.getZoom();
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = 17;
    hash = hash * 31 + mStartColumn;
    hash = hash * 31 + mStartRow;
    hash = hash * 31 + 1000 * mDetail.getZoom();
    return hash;
  }

  public interface DrawingView {
    void setDirty(Tile tile);
    Context getContext();
  }

  public interface Listener {
    void onTileDestroyed(Tile tile);
    void onTileDecodeError(Tile tile, Exception e);
  }

  private static class TileOptions extends BitmapFactory.Options {

    //https://developer.android.com/reference/android/graphics/BitmapFactory.Options.html#inTempStorage
    private static final byte[] sInTempStorage = new byte[16 * 1024];

    TileOptions(boolean measure) {
      inMutable = true;
      inPreferredConfig = Bitmap.Config.RGB_565;
      inTempStorage = sInTempStorage;
      inSampleSize = 1;
      inJustDecodeBounds = measure;
    }

  }

}