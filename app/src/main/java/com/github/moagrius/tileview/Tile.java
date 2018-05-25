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
  private Provider mProvider;
  private State mState = State.IDLE;
  private Bitmap mBitmap;
  private Rect mRawRect = new Rect();
  private Rect mDestinationRect = new Rect();
  private BitmapFactory.Options mOptions;

  public State getState() {
    return mState;
  }

  public void setProvider(Provider provider) {
    mProvider = provider;
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

  public Rect getRect() {
    return mRawRect;
  }

  private void updateDestinationRect() {
    int cellSize = TILE_SIZE * mProvider.getDetailSample();
    int patchSize = cellSize * mProvider.getImageSample();
    mDestinationRect.left = mStartColumn * cellSize;
    mDestinationRect.top = mStartRow * cellSize;
    mDestinationRect.right = mDestinationRect.left + patchSize;
    mDestinationRect.bottom = mDestinationRect.top + patchSize;
    // raw rect to compare to raw viewport (in theory)
    mRawRect.left = mStartColumn * TILE_SIZE;
    mRawRect.top = mStartRow * TILE_SIZE;
    mRawRect.right = mRawRect.left + TILE_SIZE;
    mRawRect.bottom = mRawRect.top + TILE_SIZE;
  }

  private String getFilePath() {
    Detail detail = mProvider.getDetail();
    String template = detail.getUri();
    return String.format(Locale.US, template, mStartColumn, mStartRow);
  }

  private String getCacheKey() {
    // TODO: lazy
    String normalized = getFilePath().replace(".", "_").replace("/", "_");
    return String.format(Locale.US, "%1$s-%2$s", normalized, mProvider.getImageSample());
  }

  // TODO: write in english
  // TODO: we're assuming that sample size 1 is already on disk but if we allow BitmapProviders, then we'll need to allow that to not be the case
  // TODO: reuse bitmaps https://developer.android.com/topic/performance/graphics/manage-memory
  public void decode(Context context, TileView.Cache memoryCache, TileView.Cache diskCache) throws Exception {
    if (mState != State.IDLE) {
      return;
    }
    //Thread.sleep(2000 + new Random().nextInt(3000));
    updateDestinationRect();
    String key = getCacheKey();
    Bitmap cached = memoryCache.get(key);
    if (cached != null) {
      mBitmap = cached;
      mState = State.DECODED;
      mProvider.getTileView().postInvalidate();
      return;
    }
    mState = State.DECODING;
    // if image sample is greater than 1, we should cache the downsampled versions on disk
    boolean isSubSampled = mProvider.getImageSample() > 1;
    if (isSubSampled) {
      cached = diskCache.get(key);
      if (cached != null) {
        mBitmap = cached;
        mState = State.DECODED;
        mProvider.getTileView().postInvalidate();
        return;
      }
      // if we're patching, we need a base bitmap to draw on
      if (mBitmap == null) {
        mBitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.RGB_565);
      }
      Canvas canvas = new Canvas(mBitmap);
      canvas.drawColor(Color.GREEN);
      String template = mProvider.getDetail().getUri();
      int sample = mProvider.getImageSample();
      int size = TILE_SIZE / sample;
      for (int i = 0; i < sample; i++) {
        for (int j = 0; j < sample; j++) {  // TODO:
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
      mProvider.getTileView().postInvalidate();
      memoryCache.put(key, mBitmap);
      diskCache.put(key, mBitmap);
    } else {  // no subsample means we have an explicit detail level for this scale, just use that
      String file = getFilePath();
      InputStream stream = context.getAssets().open(file);
      if (stream != null) {
        Bitmap bitmap = BitmapFactory.decodeStream(stream, null, mOptions);
        mBitmap = bitmap;
        mState = State.DECODED;
        mProvider.getTileView().postInvalidate();
        memoryCache.put(key, bitmap);
      }
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
          && compare.mOptions.inSampleSize == mOptions.inSampleSize;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Hashes.compute(17, 31, mStartColumn, mStartRow, mOptions.inSampleSize);
  }

  public interface Provider {
    TileView getTileView();
    Detail getDetail();
    int getImageSample();
    int getDetailSample();
  }

}
