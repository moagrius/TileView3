package com.github.moagrius.scheduling;

import android.os.Handler;

public class Debounce {

  private Handler mHandler = new Handler();
  private long mInterval;

  public Debounce(long interval) {
    mInterval = interval;
  }

  public void attempt(Runnable runnable) {
    mHandler.removeCallbacks(runnable);
    mHandler.postDelayed(runnable, mInterval);
  }

}