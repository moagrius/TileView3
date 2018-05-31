package com.github.moagrius.tileview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;

import com.github.moagrius.utils.Hashes;

import java.io.InputStream;
import java.util.Locale;


public class Tile {

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
  private Thread mThread;
  private TileView.Cache mMemoryCache;
  private TileView.Cache mDiskCache;
  private BitmapPool mBitmapPool;
  private Rect mDestinationRect = new Rect();
  private BitmapFactory.Options mOptions = new TileOptions();

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
    mOptions.inSampleSize = imageSample;
  }

  public void setDetail(Detail detail) {
    mDetail = detail;
  }

  public void setDrawingView(DrawingView drawingView) {
    mDrawingView = drawingView;
  }

  public void setMemoryCache(TileView.Cache memoryCache) {
    mMemoryCache = memoryCache;
  }

  public void setDiskCache(TileView.Cache diskCache) {
    mDiskCache = diskCache;
  }

  public void setBitmapPool(BitmapPool bitmapPool) {
    mBitmapPool = bitmapPool;
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
  // TODO: reuse bitmaps https://developer.android.com/topic/performance/graphics/manage-memory
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
      mMemoryCache.put(key, mBitmap);
      mDiskCache.put(key, mBitmap);
    } else {  // no subsample means we have an explicit detail level for this scale, just use that
      String file = getFilePath();
      InputStream stream = context.getAssets().open(file);
      if (stream != null) {
        attemptBitmapReuse();
        Bitmap bitmap = BitmapFactory.decodeStream(stream, null, mOptions);
        mBitmap = bitmap;
        mState = State.DECODED;
        mDrawingView.postInvalidate();
        mMemoryCache.put(key, bitmap);
      }
    }
  }

  public void draw(Canvas canvas) {
    if (mState == State.DECODED) {
      canvas.drawBitmap(mBitmap, null, mDestinationRect, null);
    }
  }

  public void destroy() {
    //mBitmap.eraseColor(Color.BLACK);
    //mBitmapPool.put(mBitmap);
    mBitmap = null;
    mState = State.IDLE;
    if (mThread != null && mThread.isAlive()) {
      mThread.interrupt();
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
  }








  protected void attemptBitmapReuse() {
    Bitmap bitmap = mBitmapPool.get(mOptions);
    if (bitmap != null) {
      mOptions.inBitmap = bitmap;
    }
  }

}
