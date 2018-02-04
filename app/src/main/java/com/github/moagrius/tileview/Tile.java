package com.github.moagrius.tileview;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.github.moagrius.utils.Hashes;

import java.io.InputStream;
import java.util.Locale;

/**
 * @author Mike Dunn, 2/4/18.
 */

public class Tile {

  private static final BitmapFactory.Options OPTIONS = new BitmapFactory.Options();

  static {
    OPTIONS.inPreferredConfig = Bitmap.Config.RGB_565;
  }

  public enum State { IDLE, DECODING, DECODED }

  private Bitmap mBitmap;
  private State mState = State.IDLE;

  private int mRow;
  private int mColumn;

  private float mX;
  private float mY;

  public Bitmap getBitmap() {
    return mBitmap;
  }

  public int getColumn() {
    return mColumn;
  }

  public void setColumn(int column) {
    mColumn = column;
    mX = mColumn * 256;
  }

  public int getRow() {
    return mRow;
  }

  public void setRow(int row) {
    mRow = row;
    mY = mRow * 256;
  }

  public float getX() {
    return mX;
  }

  public float getY() {
    return mY;
  }

  public void decode(Context context) {
    if (mState != State.IDLE) {
      return;
    }
    mState = State.DECODING;
    String formattedFileName = String.format( Locale.US, "tiles/phi-500000-%d_%d.jpg", mColumn, mRow );
    AssetManager assetManager = context.getAssets();
    try {
      InputStream inputStream = assetManager.open( formattedFileName );
      if( inputStream != null ) {
        try {
          mBitmap = BitmapFactory.decodeStream( inputStream, null, OPTIONS );
          mState = State.DECODED;
        } catch( OutOfMemoryError | Exception e ) {
          // this is probably an out of memory error - you can try sleeping (this method won't be called in the UI thread) or try again (or give up)
        }
      }
    } catch( Exception e ) {
      // this is probably an IOException, meaning the file can't be found
    }
  }

  public void clear() {
    mBitmap = null;
    mState = State.IDLE;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Tile) {
      Tile compare = (Tile) o;
      return compare.getColumn() == mColumn && compare.getRow() == mRow;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Hashes.compute(17, 31, mColumn, mRow);
  }

}
