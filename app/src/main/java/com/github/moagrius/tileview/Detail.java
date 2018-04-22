package com.github.moagrius.tileview;

/**
 * ZOOM     PERCENT     SCALE
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

  public static float getPercentFromScale(int scale) {
    return 1 / (float) scale;
  }

  public static int getScaleFromZoom(int zoom) {
    return 1 << zoom;
  }

  public static float getScaleFromPercent(float percent) {
    return 1 / percent;
  }

  public static int getZoomFromScale(int scale) {
    return (int) (Math.log(scale) / LOG_2);
  }

  public static int getZoomFromPercent(float percent) {
    return (int) (Math.log(1 / percent) / LOG_2);
  }

  private int mZoom;
  private String mUri;

  public Detail(int zoom, String uri) {
    mZoom = zoom;
    mUri = uri;
  }

  public int getZoom() {
    return mZoom;
  }

  public void setZoom(int zoom) {
    mZoom = zoom;
  }

  public String getUri() {
    return mUri;
  }

  public void setUri(String uri) {
    mUri = uri;
  }

}
