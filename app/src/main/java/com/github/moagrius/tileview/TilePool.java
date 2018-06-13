package com.github.moagrius.tileview;

import com.github.moagrius.tileview.io.StreamProvider;

import java.lang.ref.SoftReference;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ThreadPoolExecutor;

public class TilePool {

  private final Queue<SoftReference<Tile>> mQueue = new ArrayDeque<>();
  private final Factory mFactory;

  public TilePool(Factory factory) {
    mFactory = factory;
  }

  public Tile get() {
    if (mQueue.peek() != null) {
      Tile tile = mQueue.poll().get();
      if (tile != null) {
        return tile;
      }
    }
    return mFactory.create();
  }

  public void put(Tile tile) {
    if (tile != null) {
      mQueue.add(new SoftReference<>(tile));
    }
  }

  public void clear() {
    mQueue.clear();
  }

  public interface Factory {
    Tile create();
  }

}
