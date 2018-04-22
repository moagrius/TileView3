package com.github.moagrius.tileview;

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

  private static final double LOG_2 = Math.log(2);

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

  public static float getSampleFromPercent(float percent) {
    return 1 / percent;
  }

  public static int getZoomFromScale(int sample) {
    return (int) (Math.log(sample) / LOG_2);
  }

  public static int getZoomFromPercent(float percent) {
    return (int) (Math.log(1 / percent) / LOG_2);
  }

  private int mZoom;
  private int mSample;
  private float mPercent;
  private String mUri;

  public Detail(int zoom, String uri) {
    mUri = uri;
    mZoom = zoom;
    mSample = getSampleFromZoom(zoom);
    mPercent = getPercentFromZoom(zoom);
  }

  public String getUri() {
    return mUri;
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
