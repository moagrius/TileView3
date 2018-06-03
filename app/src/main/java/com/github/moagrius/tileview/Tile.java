package com.github.moagrius.tileview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.Log;

import com.github.moagrius.utils.Hashes;

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
  private State mState = State.IDLE;
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
  private MemoryCache mMemoryCache;
  private DiskCache mDiskCache;
  private ThreadPoolExecutor mThreadPoolExecutor;

  public void setListener(Listener listener) {
    mListener = listener;
  }

  public void setThreadPoolExecutor(ThreadPoolExecutor threadPoolExecutor) {
    mThreadPoolExecutor = threadPoolExecutor;
  }

  public void setMemoryCache(MemoryCache memoryCache) {
    mMemoryCache = memoryCache;
  }

  public void setDiskCache(DiskCache diskCache) {
    mDiskCache = diskCache;
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

  public void setOptions(BitmapFactory.Options options) {
    mDrawingOptions = options;
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

  // TODO: we're assuming that sample size 1 is already on disk but if we allow BitmapProviders, then we'll need to allow that to not be the case
  public void decode() throws Exception {
    if (mState != State.IDLE) {
      return;
    }
    Context context = mDrawingView.getContext();
    mThread = Thread.currentThread();
    // putting a thread.sleep of even 100ms here shows that maybe we're doing work off screen that we should not be doing
    updateDestinationRect();
    String key = getCacheKey();
    Bitmap cached = mMemoryCache.get(key);
    if (cached != null) {
      Log.d("TV", "cache hit for " + key);
      mMemoryCache.remove(key);
      mBitmap = cached;
      mState = State.DECODED;
      mDrawingView.postInvalidate();
      return;
    }
    mState = State.DECODING;
    // if image sample is greater than 1, we should cache the downsampled versions on disk
    boolean isSubSampled = mImageSample > 1;
    if (isSubSampled) {
      cached = mDiskCache.get(key);
      if (cached != null) {
        mBitmap = cached;
        mState = State.DECODED;
        mDrawingView.postInvalidate();
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
        mBitmap = mMemoryCache.getBitmapForReuse(mMeasureOptions);
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
          String file = String.format(Locale.US, template, mStartColumn + j, mStartRow + i);
          InputStream stream = context.getAssets().open(file);
          if (stream != null) {
            Bitmap piece = BitmapFactory.decodeStream(stream, null, mDrawingOptions);
            canvas.drawBitmap(piece, j * size, i * size, null);
          }
        }
      }
      // if it got destroyed while we were decoding...
      if (mState != State.DECODING) {
        return;
      }
      mState = State.DECODED;
      mDrawingView.postInvalidate();
      mDiskCache.put(key, mBitmap);
    } else {  // no subsample means we have an explicit detail level for this scale, just use that
      String file = getFilePath();
      InputStream stream = context.getAssets().open(file);
      if (stream != null) {
        // measure it and populate measure options to pass to cache
        BitmapFactory.decodeStream(stream, null, mMeasureOptions);
        // if we made it this far, the exact bitmap wasn't in memory, but let's grab the least recently used bitmap from the cache and draw over it
        mDrawingOptions.inBitmap = mMemoryCache.getBitmapForReuse(mMeasureOptions);
        // the measurement moved the stream's position - it must be reset to use the same stream to draw pixels
        stream.reset();
        mBitmap = BitmapFactory.decodeStream(stream, null, mDrawingOptions);
        // if it got destroyed while we were decoding...
        if (mState != State.DECODING) {
          return;
        }
        mState = State.DECODED;
        mDrawingView.postInvalidate();
      }
    }
  }

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
    mCacheKey = null;  // CRITICAL!  this led to several bugs
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
      mListener.onTileDecodeError(e);
    }
  }

  public void draw(Canvas canvas) {
    if (mState == State.DECODED && mBitmap != null) {
      canvas.drawBitmap(mBitmap, null, mDestinationRect, null);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Tile) {
      Tile compare = (Tile) obj;
      return compare.mStartColumn == mStartColumn
          && compare.mStartRow == mStartRow
          && compare.mImageSample == mImageSample
          && compare.mDetail.getZoom() == mDetail.getZoom();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Hashes.compute(17, 31, mStartColumn, mStartRow, mImageSample, mDetail.getZoom());
  }

  public interface DrawingView {
    void postInvalidate();
    Context getContext();
  }

  public interface Listener {
    void onTileDestroyed(Tile tile);
    void onTileDecodeError(Exception e);
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