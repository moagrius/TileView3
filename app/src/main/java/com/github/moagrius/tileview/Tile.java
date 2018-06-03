package com.github.moagrius.tileview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
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
  private Detail mDetail;
  private DrawingView mDrawingView;
  private Listener mListener;
  private Thread mThread;
  private Rect mDestinationRect = new Rect();
  private BitmapFactory.Options mOptions;
  private MemoryCache mMemoryCache;
  private DiskCache mDiskCache;
  private ThreadPoolExecutor mThreadPoolExecutor;

  public void destroy() {
    if (mState == State.IDLE) {
      return;
    }
    if(mThread != null && mThread.isAlive()) {
      mThread.interrupt();
    }
    mThreadPoolExecutor.remove(this);
    mMemoryCache.put(getCacheKey(), mBitmap);
    mBitmap = null;
    mState = State.IDLE;
    mListener.onTileDestroyed(this);
  }

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
    mOptions = options;
  }

  public void setImageSample(int imageSample) {
    mImageSample = imageSample;
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
    // TODO: lazy
    String normalized = getFilePath().replace(".", "_").replace("/", "_");
    return String.format(Locale.US, "%1$s-%2$s", normalized, mImageSample);
  }

  // TODO: we're assuming that sample size 1 is already on disk but if we allow BitmapProviders, then we'll need to allow that to not be the case
  public void decode(Context context) throws Exception {
    if (mState != State.IDLE) {
      return;
    }
    mThread = Thread.currentThread();
    // putting a thread.sleep of even 100ms here shows that maybe we're doing work off screen that we should not be doing
    updateDestinationRect();
    String key = getCacheKey();
    Bitmap cached = mMemoryCache.get(key);
    if (cached != null) {
      Log.d("TV", "cache hit");
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
//      cached = mDiskCache.get(key);
//      if (cached != null) {
//        mBitmap = cached;
//        mState = State.DECODED;
//        mDrawingView.postInvalidate();
//        return;
//      }
      // if we're patching, we need a base bitmap to draw on
      if (mBitmap == null) {
        mBitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.RGB_565);
      }
      Canvas canvas = new Canvas(mBitmap);
      canvas.drawColor(Color.GREEN);
      String template = mDetail.getUri();
      int size = TILE_SIZE / mImageSample;
      for (int i = 0; i < mImageSample; i++) {
        for (int j = 0; j < mImageSample; j++) {
          String file = String.format(Locale.US, template, mStartColumn + j, mStartRow + i);
          InputStream stream = context.getAssets().open(file);
          if (stream != null) {
            Bitmap piece = BitmapFactory.decodeStream(stream, null, mOptions);
            int left = j * size;
            int top = i * size;
            canvas.drawBitmap(piece, left, top, null);
          }
        }
      }
      mState = State.DECODED;
      mDrawingView.postInvalidate();
     // mMemoryCache.put(key, mBitmap);
      //mDiskCache.put(key, mBitmap);
    } else {  // no subsample means we have an explicit detail level for this scale, just use that
      String file = getFilePath();
      InputStream stream = context.getAssets().open(file);
      if (stream != null) {
        mBitmap = BitmapFactory.decodeStream(stream, null, mOptions);
        mState = State.DECODED;
        mDrawingView.postInvalidate();
        //mMemoryCache.put(key, bitmap);
      }
    }
  }
  
  public void run() {
    try {
      decode(mDrawingView.getContext());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void draw(Canvas canvas) {
    if (mState == State.DECODED) {
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
    return Hashes.compute(17, 31, mStartColumn, mStartRow, mImageSample);
  }

  public interface DrawingView {
    void postInvalidate();
    Context getContext();
  }

  public interface Listener {
    void onTileDestroyed(Tile tile);
  }

}
