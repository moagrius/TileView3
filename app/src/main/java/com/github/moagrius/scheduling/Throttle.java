package com.github.moagrius.scheduling;

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

public class Throttle {

  private long mLastFiredTimestamp;
  private long mInterval;

  public Throttle(long interval) {
    mInterval = interval;
  }

  public boolean attempt(Runnable runnable) {
    if (hasSatisfiedInterval()) {
      runnable.run();
      mLastFiredTimestamp = getNow();
      return true;
    }
    return false;
  }

  private boolean hasSatisfiedInterval() {
    long elapsed = getNow() - mLastFiredTimestamp;
    return elapsed >= mInterval;
  }

  private long getNow() {
    return AnimationUtils.currentAnimationTimeMillis();
  }

}