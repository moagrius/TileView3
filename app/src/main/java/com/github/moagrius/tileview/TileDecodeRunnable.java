package com.github.moagrius.tileview;

import android.content.Context;
import android.util.Log;

import java.lang.ref.WeakReference;

/**
 * @author Mike Dunn, 2/4/18.
 */

public class TileDecodeRunnable implements Runnable {

  private WeakReference<Tile> mTileWeakReference;
  private WeakReference<Context> mContextWeakReference;

  public TileDecodeRunnable(Tile tile, Context context) {
    mTileWeakReference = new WeakReference<>(tile);
    mContextWeakReference = new WeakReference<>(context);
  }

  @Override
  public void run() {
    Log.d("T", "TileDecodeRunnable.run");
    Tile tile = mTileWeakReference.get();
    if (tile != null) {
      Context context = mContextWeakReference.get();
      if (context != null) {
        tile.decode(context);
      }
    }
  }
}
