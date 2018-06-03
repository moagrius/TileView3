package com.github.moagrius.tileview;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TileRenderExecutor extends ThreadPoolExecutor {

  public TileRenderExecutor(int cores) {
    super(cores, cores, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>());
  }

  public TileRenderExecutor() {
    this(Runtime.getRuntime().availableProcessors());
  }

  public void queue(Set<Tile> renderSet) {
    Iterator<Runnable> iterator = getQueue().iterator();
    while (iterator.hasNext()) {
      Tile tile = (Tile) iterator.next();
      if (!renderSet.contains(tile)) {
        tile.destroy(false);
        iterator.remove();
      }
    }
    for (Tile tile : renderSet) {
      if (isShutdownOrTerminating()) {
        return;
      }
      execute(tile);
    }
  }

  public void cancel() {
    for (Runnable runnable : getQueue()) {
      Tile tile = (Tile) runnable;
      tile.destroy();
    }
    getQueue().clear();
  }

  public boolean isShutdownOrTerminating() {
    return isShutdown() || isTerminating() || isTerminated();
  }

  @Override
  protected void afterExecute(Runnable runnable, Throwable throwable) {
    synchronized (this) {
      super.afterExecute(runnable, throwable);
      if (getQueue().size() == 0) {
        // TODO: notify something?
      }
    }
  }

}

