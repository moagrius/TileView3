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


public class Tile {

  public static final int TILE_SIZE = 256;

  enum State {
    IDLE, DECODING, DECODED
  }

  private int mStartRow;
  private int mStartColumn;
  private String mCacheKey;
  private State mState = State.IDLE;
  private Bitmap mBitmap;
  private int mImageSample = 1;
  private Detail mDetail;
  private DrawingView mDrawingView;
  private Thread mThread;
  private MemoryCache mMemoryCache;
  private TileView.Cache mDiskCache;
  private Rect mDestinationRect = new Rect();
  private BitmapFactory.Options mDecodeOptions = new TileOptions(false);
  private BitmapFactory.Options mMeasureOptions = new TileOptions(true);

  public BitmapFactory.Options getOptions() {
    return mDecodeOptions;
  }

  public State getState() {
    return mState;
  }

  public int getImageSample() {
    return mImageSample;
  }

  public void setStartRow(int startRow) {
    mStartRow = startRow;
  }

  public void setStartColumn(int startColumn) {
    mStartColumn = startColumn;
  }

  public void setImageSample(int imageSample) {
    mImageSample = imageSample;
    mDecodeOptions.inSampleSize = imageSample;
  }

  public void setDetail(Detail detail) {
    mDetail = detail;
  }

  public void setDrawingView(DrawingView drawingView) {
    mDrawingView = drawingView;
  }

  public void setMemoryCache(MemoryCache memoryCache) {
    mMemoryCache = memoryCache;
  }

  public void setDiskCache(TileView.Cache diskCache) {
    mDiskCache = diskCache;
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
      Log.d("TV", "we're using a cached bitmap");
      mMemoryCache.setEmployed(key, true);
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
            Bitmap piece = BitmapFactory.decodeStream(stream, null, mDecodeOptions);
            int left = j * size;
            int top = i * size;
            canvas.drawBitmap(piece, left, top, null);
          }
        }
      }
      mState = State.DECODED;
      mDrawingView.postInvalidate();
      mMemoryCache.put(key, mBitmap);
      //mMemoryCache.setEmployed(key, true);
      mDiskCache.put(key, mBitmap);
    } else {  // no subsample means we have an explicit detail level for this scale, just use that
      Log.d("TV", "we must decode, try to reuse a different bitmap from memory cache to draw over");
      String file = getFilePath();
      InputStream stream = context.getAssets().open(file);
      if (stream != null) {
        // measure it for bitmap reuse
        BitmapFactory.decodeStream(stream, null, mMeasureOptions);
        attemptBitmapReuse();
        // now decode
        stream.reset();
        mBitmap = BitmapFactory.decodeStream(stream, null, mDecodeOptions);
        mState = State.DECODED;
        mDrawingView.postInvalidate();
        mMemoryCache.put(key, mBitmap);
        mMemoryCache.setEmployed(key, true);
      }
    }
  }

  public void draw(Canvas canvas) {
    if (mState == State.DECODED) {
      canvas.drawBitmap(mBitmap, null, mDestinationRect, null);
    }
  }

  public void destroy() {
    mBitmap = null;
    mState = State.IDLE;
    Log.d("TV", "freeing bitmap: " + getCacheKey());
    mMemoryCache.setEmployed(getCacheKey(), false);
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








  private void attemptBitmapReuse() {
    Bitmap bitmap = mMemoryCache.getBitmapForReuse(mMeasureOptions);
    if (bitmap != null) {
      Log.d("TV", "we are reusing a bitmap");
      mDecodeOptions.inBitmap = bitmap;
    } else {
      Log.d("TV", "we couldn't get a bitmap to draw on");
    }
  }

}
