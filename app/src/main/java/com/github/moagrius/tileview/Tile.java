package com.github.moagrius.tileview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.Log;
import android.util.SparseArray;

import com.github.moagrius.utils.Hashes;

import java.io.InputStream;
import java.util.Locale;

public class Tile {

  public static final int TILE_SIZE = 256;

  private static final String FILE_TEMPLATE = "tiles/phi-500000-%1$d_%2$d.jpg";

  enum State {
    IDLE, DECODING, DECODED
  }

  private int mDefaultColor = Color.GRAY;
  private int mStartRow;
  private int mStartColumn;
  private State mState = State.IDLE;
  private Bitmap bitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.RGB_565);
  private Rect destinationRect = new Rect();
  private BitmapFactory.Options mOptions;
  private SparseArray<String> mDetailLevels = new SparseArray<>();

  public void setDefaultColor(int color) {
    mDefaultColor = color;
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

  public void addDetailLevel(int position, String template) {
    mDetailLevels.put(position, template);
  }

  private void updateDestinationRect() {
    destinationRect.left = mStartColumn * TILE_SIZE;
    destinationRect.top = mStartRow * TILE_SIZE;
    destinationRect.right = destinationRect.left + (TILE_SIZE * mOptions.inSampleSize);
    destinationRect.bottom = destinationRect.top + (TILE_SIZE * mOptions.inSampleSize);
  }

  private String getCacheKey() {
    return mStartColumn + ":" + mStartRow + ":" + mOptions.inSampleSize;
  }

  private String getDetailLevel() {
    return mDetailLevels.get(mOptions.inSampleSize);
  }

  public void decode(Context context, TileView.Cache cache, TileView tileView) throws Exception {
    if (mState != State.IDLE) {
      return;
    }
    mState = State.DECODING;
    updateDestinationRect();
    // try to get from memory first
    String cacheKey = getCacheKey();
    Bitmap cached = cache.get(cacheKey);
    if (cached != null) {
      Log.d("T", "got bitmap from memory cache");
      bitmap = cached;
      mState = State.DECODED;
      tileView.postInvalidate();
      return;
    }
    // TODO: do we need to check disk cache for remote images?
    
    // optimize for detail level 1
    int sample = mOptions.inSampleSize;
    if (sample == 1) {
      Log.d("DL", "sample size one, use quick decode");
      String file = String.format(Locale.US, FILE_TEMPLATE, mStartColumn, mStartRow);
      InputStream stream = context.getAssets().open(file);
      if (stream != null) {
        bitmap = BitmapFactory.decodeStream(stream, null, mOptions);
        cache.put(cacheKey, bitmap);
        mState = State.DECODED;
        tileView.postInvalidate();
        return;
      }
    }
    // now check disk cache - we don't need disk cache for level 1 because it's already on disk
    // TODO: disk cache

    // do we have a special detail level?
    // TODO: we should use the last detail level (e.g., 4) for pieces smaller levels (e.g., 8)
    String detail = getDetailLevel();
    if (detail != null) {
      Log.d("DL", "has detail level: " + detail);
      String file = String.format(Locale.US, detail, mStartColumn, mStartRow);
      InputStream stream = context.getAssets().open(file);
      if (stream != null) {
        Log.d("DL", "steam is not null, should be rendering");
        bitmap = BitmapFactory.decodeStream(stream, null, mOptions);
        cache.put(cacheKey, bitmap);
        mState = State.DECODED;
        tileView.postInvalidate();
        return;
      }
    }
    // not top level, we need to patch together bitmaps
    Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(mDefaultColor);
    int size = TILE_SIZE / sample;
    for (int i = 0; i < sample; i++) {
      for (int j = 0; j < sample; j++) {
        String file = String.format(Locale.US, FILE_TEMPLATE, mStartColumn + j, mStartRow + i);
        InputStream stream = context.getAssets().open(file);
        if (stream != null) {
          Bitmap piece = BitmapFactory.decodeStream(stream, null, mOptions);
          canvas.drawBitmap(piece, j * size, i * size, null);
        }
      }
    }
    cache.put(cacheKey, bitmap);
    mState = State.DECODED;
    tileView.postInvalidate();
  }

  public void draw(Canvas canvas) {
    if (mState == State.DECODED) {
      canvas.drawBitmap(bitmap, null, destinationRect, null);
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

}
