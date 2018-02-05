package com.github.moagrius.utils;

import android.view.animation.AnimationUtils;

/**
 * Do things no more often than interval.
 *
 * Throttler throttle = new Throttler(10000); // 10 seconds
 * throttler.attempt(new Runnable(){
 *   @Override
 *   public void run(){
 *     doStuff();
 *   }
 * });
 *
 * Created by michaeldunn on 3/2/17.
 */

public class Throttler {

  private long mLastFiredTimestamp;
  private long mInterval;

  public Throttler(long interval) {
    mInterval = interval;
  }

  public void attempt(Runnable runnable) {
    if (hasSatisfiedInterval()) {
      runnable.run();
      mLastFiredTimestamp = getNow();
    }
  }

  private boolean hasSatisfiedInterval() {
    long elapsed = getNow() - mLastFiredTimestamp;
    return elapsed >= mInterval;
  }

  private long getNow() {
    return AnimationUtils.currentAnimationTimeMillis();
  }

}