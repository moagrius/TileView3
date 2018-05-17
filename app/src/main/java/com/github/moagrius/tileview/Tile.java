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

  private static final String FILE_TEMPLATE = "tiles/phi-500000-%1$d_%2$d.jpg";

  enum State {
    IDLE, DECODING, DECODED
  }

  private int mDefaultColor = Color.GRAY;
  private int mStartRow;
  private int mStartColumn;
  private Provider mProvider;
  private State mState = State.IDLE;
  private Bitmap mBitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.RGB_565);
  private Rect destinationRect = new Rect();
  private BitmapFactory.Options mOptions;

  public void setProvider(Provider provider) {
    mProvider = provider;
  }

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

  private void updateDestinationRect() {
    // TODO: 051318 here.  Check 25% and smaller (skips rows and columns)
    int size = TILE_SIZE * mProvider.getDetailSample();
    destinationRect.left = mStartColumn * size;
    destinationRect.top = mStartRow * size;
    destinationRect.right = destinationRect.left + size;
    destinationRect.bottom = destinationRect.top + size;
  }

  private String getFilePath() {
    Detail detail = mProvider.getDetail();
    String template = detail.getUri();
    return String.format(Locale.US, template, mStartColumn, mStartRow);
  }

  private void populateBitmap(Bitmap bitmap) {
    mBitmap = bitmap;
    mState = State.DECODED;
    mProvider.getTileView().postInvalidate();
  }

  public void decode(Context context, TileView.Cache cache) throws Exception {
    if (mState != State.IDLE) {
      return;
    }
    updateDestinationRect();
    String file = getFilePath();
    Bitmap cached = cache.get(file);
    if (cached != null) {
      populateBitmap(cached);
      return;
    }
    mState = State.DECODING;
    InputStream stream = context.getAssets().open(file);
    if (stream != null) {
      Bitmap bitmap = BitmapFactory.decodeStream(stream, null, mOptions);
      populateBitmap(bitmap);
      cache.put(file, bitmap);
    }  // TODO: else?
  }


  public void draw(Canvas canvas) {
    if (mState == State.DECODED) {
      canvas.drawBitmap(mBitmap, null, destinationRect, null);
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
