package com.github.moagrius.tileview;

import java.lang.ref.SoftReference;
import java.util.LinkedList;
import java.util.Queue;

public class TilePool {

  private Queue<SoftReference<Tile>> mQueue = new LinkedList<>();

  public Tile get() {
    if (mQueue.peek() != null) {
      Tile tile = mQueue.poll().get();
      if (tile != null) {
        return tile;
      }
    }
    return new Tile();
  }

  public void put(Tile tile) {
    if (tile != null) {
      mQueue.add(new SoftReference<>(tile));
    }
  }

}
