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

  private String getCacheKey() {
    String normalized = getFilePath().replace(".", "_").replace("/", "_");
    return String.format(Locale.US, "%1$s-%2$s", normalized, mProvider.getImageSample());
  }

  private void populateBitmap(Bitmap bitmap) {
    mBitmap = bitmap;
    mState = State.DECODED;
    mProvider.getTileView().postInvalidate();
  }

  // TODO: we're assuming that sample size 1 is already on disk but if we allow BitmapProviders, then we'll need to allow that to not be the case
  public void decode(Context context, TileView.Cache memoryCache, TileView.Cache diskCache) throws Exception {
    if (mState != State.IDLE) {
      return;
    }
    updateDestinationRect();
    String key = getCacheKey();
    Bitmap cached = memoryCache.get(key);
    if (cached != null) {
      populateBitmap(cached);
      return;
    }
    // if image sample is greater than 1, we should cache the downsampled versions on disk
    boolean isSubSampled = mProvider.getImageSample() > 1;
    if (isSubSampled) {
      cached = diskCache.get(key);
      if (cached != null) {
        populateBitmap(cached);
        return;
      }
    }
    String file = getFilePath();
    mState = State.DECODING;
    InputStream stream = context.getAssets().open(file);
    if (stream != null) {
      Bitmap bitmap = BitmapFactory.decodeStream(stream, null, mOptions);
      populateBitmap(bitmap);
      memoryCache.put(key, bitmap);
      if (isSubSampled) {
        diskCache.put(key, bitmap);
      }
    }  // TODO: else?
    // TODO: 051618, patching
    // basic idea is column % sample == 0, that's the start of "compound column"
    // same with rows, which we then grab the next <sample> tiles in size and save them to disk
    // so something like:
    // if (mStartColumn % sample == 0 && mStartRow % sample == 0) {
    //   for (i = 0; i < sample; i++) {
    //     for (j = 0; j < sample j++) {
    //       ... get tiles for mStartColumn + i and mStartRow + j, stitch it together, and save it
    //     }
    //   }
    // }
    // use the file name of the sample it _would_ be if one exists
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
