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

  private static final String FILE_TEMPLATE = "tiles/phi-500000-%1$d_%2$d.jpg";

  enum State {
    IDLE, DECODING, DECODED
  }

  private int mStartRow;
  private int mStartColumn;
  private State mState = State.IDLE;
  private Bitmap bitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.RGB_565);
  private Rect destinationRect = new Rect();
  private BitmapFactory.Options mOptions;

  public void setStartRow(int startRow) {
    mStartRow = startRow;
  }

  public void setStartColumn(int startColumn) {
    mStartColumn = startColumn;
  }

  public void setOptions(BitmapFactory.Options options) {
    mOptions = options;
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

  public void decode(Context context, TileView.Cache cache) throws Exception {
    if (mState != State.IDLE) {
      return;
    }
    mState = State.DECODING;
    updateDestinationRect();
    String cacheKey = getCacheKey();
    Bitmap cached = cache.get(cacheKey);
    if (cached != null) {
      Log.d("T", "got bitmap from memory cache");
      bitmap = cached;
      mState = State.DECODED;
      return;
    }
    Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(Color.GREEN);
    int sample = mOptions.inSampleSize;
    int size = TILE_SIZE / sample;
    for (int i = 0; i < sample; i++) {
      for (int j = 0; j < sample; j++) {
        Log.d("G", "iterating i:$i, j:$j");
        String file = String.format(Locale.US, FILE_TEMPLATE, mStartColumn + j, mStartRow + i);
        InputStream stream = context.getAssets().open(file);
        if (stream != null) {
          Bitmap piece = BitmapFactory.decodeStream(stream, null, mOptions);
          int left = j * size;
          int top = i * size;
          Log.d("G", "putting piece for $startRow:$startColumn at $left:$top");
          canvas.drawBitmap(piece, left, top, null);
        }
      }
    }
    cache.put(cacheKey, bitmap);
    mState = State.DECODED;
  }

  public void draw(Canvas canvas) {
    canvas.drawBitmap(bitmap, null, destinationRect, null);
  }

  // TODO: this is not recognizing composite tiles correctly, so is over-aggressively redrawing
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
