package com.github.moagrius.tileview;

import com.github.moagrius.utils.Maths;

/**
 * ZOOM     PERCENT     SAMPLE
 * 0        100%        1
 * 1        50%         2
 * 2        25%         4
 * 3        12.5%       8
 * 4        6.25%       16
 * 5        3.125%      32
 * ...
 */

public class Detail {

  // round to 5 digits
  public static float getPercentFromZoom(int zoom) {
    return (10000 >> zoom) / 10000f;
  }

  public static float getPercentFromSample(int sample) {
    return 1 / (float) sample;
  }

  public static int getSampleFromZoom(int zoom) {
    return 1 << zoom;
  }

  public static int getSampleFromPercent(float percent) {
    return 1 << (int) Maths.log2((int) (1 / percent));
  }

  public static int getZoomFromScale(int sample) {
    return (int) Maths.log2(sample);
  }

  public static int getZoomFromPercent(float percent) {
    return (int) Maths.log2((int) (1 / percent));
  }

  private int mZoom;
  // this sample is the sample size for grid computation, not image sampling
  private int mSample;
  private float mPercent;
  private Object mData;

  public Detail(int zoom, Object data) {
    mData = data;
    mZoom = zoom;
    mSample = getSampleFromZoom(zoom);
    mPercent = getPercentFromZoom(zoom);
  }

  public Object getData() {
    return mData;
  }

  public int getZoom() {
    return mZoom;
  }

  public int getSample() {
    return mSample;
  }

  public float getPercent() {
    return mPercent;
  }

}
