package com.github.moagrius.tileview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;

import com.github.moagrius.utils.Hashes;

import java.util.Locale;


public class Tile {

  public static final int TILE_SIZE = 256;

  enum State {
    IDLE, DECODING, DECODED
  }

  private int mRow;
  private int mColumn;
  private Provider mProvider;
  private State mState = State.IDLE;
  private Bitmap mBitmap;
  private Rect destinationRect = new Rect();
  private BitmapFactory.Options mOptions;

  public void setProvider(Provider provider) {
    mProvider = provider;
  }

  public int getRow() {
    return mRow;
  }

  public void setRow(int row) {
    mRow = row;
  }

  public int getColumn() {
    return mColumn;
  }

  public void setColumn(int column) {
    mColumn = column;
  }

  public void setOptions(BitmapFactory.Options options) {
    mOptions = options;
  }

  private void updateDestinationRect() {
    int cellSize = TILE_SIZE * mProvider.getDetailSample();
    int patchSize = cellSize * mProvider.getImageSample();
    destinationRect.left = mColumn * cellSize;
    destinationRect.top = mRow * cellSize;
    destinationRect.right = destinationRect.left + patchSize;
    destinationRect.bottom = destinationRect.top + patchSize;
  }

  private String getCacheKey() {
    // TODO: lazy
    Detail detail = mProvider.getDetail();
    String template = detail.getData();
    String file = String.format(Locale.US, template, mColumn, mRow);
    String normalized = file.replace(".", "_").replace("/", "_");
    return String.format(Locale.US, "%1$s-%2$s", normalized, mProvider.getImageSample());
  }

  // TODO: write in english
  // TODO: we're assuming that sample size 1 is already on disk but if we allow BitmapProviders, then we'll need to allow that to not be the case
  // TODO: reuse bitmaps https://developer.android.com/topic/performance/graphics/manage-memory
  public void decode(Context context, TileView.Cache memoryCache, TileView.Cache diskCache) throws Exception {
    if (mState != State.IDLE) {
      return;
    }
    updateDestinationRect();
    String key = getCacheKey();
    Bitmap cached = memoryCache.get(key);
    if (cached != null) {
      mBitmap = cached;
      mState = State.DECODED;
      mProvider.postInvalidate();
      return;
    }
    mState = State.DECODING;
    // if image sample is greater than 1, we should cache the downsampled versions on disk
    int sample = mProvider.getImageSample();
    Detail detail = mProvider.getDetail();
    boolean isSubSampled = sample > 1;
    if (isSubSampled) {
      cached = diskCache.get(key);
      if (cached != null) {
        mBitmap = cached;
        mState = State.DECODED;
        mProvider.postInvalidate();
        return;
      }
      // if we're patching, we need a base bitmap to draw on
      if (mBitmap == null) {
        mBitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.RGB_565);
      }
      Canvas canvas = new Canvas(mBitmap);
      int size = TILE_SIZE / sample;
      for (int i = 0; i < sample; i++) {
        for (int j = 0; j < sample; j++) {
          Bitmap piece = mProvider.getBitmapProvider().getBitmap(context, detail, sample, mColumn + j, mRow + i);
          canvas.drawBitmap(piece, j * size, i * size, null);
        }
      }
      mState = State.DECODED;
      mProvider.postInvalidate();
      memoryCache.put(key, mBitmap);
      diskCache.put(key, mBitmap);
    } else {  // no subsample means we have an explicit detail level for this scale, just use that
      mBitmap = mProvider.getBitmapProvider().getBitmap(context, detail, sample, mColumn, mRow);
      mState = State.DECODED;
      mProvider.postInvalidate();
      memoryCache.put(key, mBitmap);
    }
  }

  public void draw(Canvas canvas) {
    if (mState == State.DECODED) {
      canvas.drawBitmap(mBitmap, null, destinationRect, null);
    }
  }

  // TODO: destroy

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Tile) {
      Tile compare = (Tile) obj;
      return compare.mColumn == mColumn
          && compare.mRow == mRow
          && compare.mOptions.inSampleSize == mOptions.inSampleSize;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Hashes.compute(17, 31, mColumn, mRow, mOptions.inSampleSize);
  }

  public interface Provider {
    void postInvalidate();
    Detail getDetail();
    int getImageSample();
    int getDetailSample();
    BitmapProvider getBitmapProvider();
  }

}
