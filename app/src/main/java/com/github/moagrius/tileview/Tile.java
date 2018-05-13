package com.github.moagrius.tileview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
  private StateProvider mStateProvider;
  private State mState = State.IDLE;
  private Bitmap bitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.RGB_565);
  private Rect destinationRect = new Rect();
  private BitmapFactory.Options mOptions;

  public void setStateProvider(StateProvider stateProvider) {
    mStateProvider = stateProvider;
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

  public int getSampleSize() {
    return mStateProvider == null ? 1 : mStateProvider.getSample();
  }

  private void updateDestinationRect() {
    int size = TILE_SIZE * getSampleSize();
    destinationRect.left = mStartColumn * size;
    destinationRect.top = mStartRow * size;
    destinationRect.right = destinationRect.left + size;
    destinationRect.bottom = destinationRect.top + size;
  }

  private String getCacheKey() {
    return mStateProvider.getDetail().getUri() + ":" + mStartColumn + ":" + mStartRow + ":" + getSampleSize();
  }

  public void decode(Context context, TileView.Cache cache, TileView tileView) throws Exception {
    if (mState != State.IDLE) {
      return;
    }
    mState = State.DECODING;
    updateDestinationRect();
    // try to get from memory first
    String cacheKey = getCacheKey();
        /*
    Bitmap cached = cache.get(cacheKey);
    if (cached != null) {
      Log.d("T", "got bitmap from memory cache");
      bitmap = cached;
      mState = State.DECODED;
      tileView.postInvalidate();
      return;
    }
    */
    // TODO: do we need to check disk cache for remote images?
    Detail detail = mStateProvider.getDetail();
    String template = detail.getUri();
    // optimize for detail level 1
    //if (getSampleSize() == 1) {
      String file = String.format(Locale.US, template, mStartColumn, mStartRow);
      InputStream stream = context.getAssets().open(file);
      if (stream != null) {
        bitmap = BitmapFactory.decodeStream(stream, null, mOptions);
        //cache.put(cacheKey, bitmap);
        mState = State.DECODED;
        tileView.postInvalidate();
      }
      return;
    //}
    // use last known detail

    // now check disk cache - we don't need disk cache for level 1 because it's already on disk
    // TODO: disk cache

    /*
    Log.d("DLS", "patching bitmaps from last known detail");
    Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(mDefaultColor);
    int size = TILE_SIZE / getSampleSize();
    BitmapFactory.Options options = new TileOptions();
    options.inSampleSize = getSampleSize();
    for (int i = 0; i < getSampleSize(); i++) {
      for (int j = 0; j < getSampleSize(); j++) {
        String file = String.format(Locale.US, detail.getUri(), mStartColumn + j, mStartRow + i);
        InputStream stream = context.getAssets().open(file);
        if (stream != null) {
          Bitmap piece = BitmapFactory.decodeStream(stream, null, options);
          canvas.drawBitmap(piece, j * size, i * size, null);
        }
      }
    }
    */

    //cache.put(cacheKey, bitmap);
    //mState = State.DECODED;
    //tileView.postInvalidate();
  }

  // TODO: DEBUG
  Paint mAlphaPaint = new Paint();
  {
    mAlphaPaint.setAlpha(100);
  }
  public void draw(Canvas canvas) {
    if (mState == State.DECODED) {
      canvas.drawBitmap(bitmap, null, destinationRect, mAlphaPaint);
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

  public interface StateProvider {
    Detail getDetail();
    int getZoom();
    int getSample();
  }

}
