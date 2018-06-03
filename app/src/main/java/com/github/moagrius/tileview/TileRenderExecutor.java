package com.github.moagrius.tileview;

import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TileRenderExecutor extends ThreadPoolExecutor {

  private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();

  public TileRenderExecutor() {
    super(AVAILABLE_PROCESSORS, AVAILABLE_PROCESSORS, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingDeque<>());
  }

  public void queue(Set<Tile> renderSet) {
    for (Runnable runnable : getQueue()) {
      Tile tile = (Tile) runnable;
      if (!renderSet.contains(tile)) {
        tile.destroy();
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

