package com.github.moagrius.scheduling;

public class Scheduler {

  private Throttle mThrottle;
  private Debounce mDebounce;

  /**
   * This wraps a Throttle and Debounce in a single instance, so that:
   * In the context of a "burst" or "stream" of events (represented by calls to `attempt`)
   * the first will fire, then one every interval, and if any were throttle due to the prior contract,
   * again a numer of ms after the stream has concluded.
   *
   * @param interval The amount of time that must pass between the Runnable running.
   */
  public Scheduler(long interval) {
    mThrottle = new Throttle(interval);
    mDebounce = new Debounce(interval);
  }

  public void attempt(Runnable runnable) {
    boolean wasThrottled = !mThrottle.attempt(runnable);
    if (wasThrottled) {
      mDebounce.attempt(runnable);
    }
  }

}
