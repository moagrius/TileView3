package com.github.moagrius.tileview.plugins;

import android.util.Log;

import com.github.moagrius.tileview.TileView;

/**
 * Note that coordinates are generally expressed as lat, lng
 * while 2D space is generally x, y
 * these are reversed - latitude is the y-axis of the earth, and longitude is the x-axis
 */
public class CoordinatePlugin implements TileView.Plugin, TileView.Listener, TileView.ReadyListener {

  private float mScale = 1;

  private double mWest;  // lng
  private double mNorth; // lat
  private double mEast;  // lng
  private double mSouth; // lat

  private double mDistanceLatitude;
  private double mDistanceLongitude;

  private int mPixelWidth;
  private int mPixelHeight;

  public CoordinatePlugin(double westLongitude, double northLatitude, double eastLongitude, double southLatitude) {
    mWest = westLongitude;
    mNorth = northLatitude;
    mEast = eastLongitude;
    mSouth = southLatitude;
    mDistanceLongitude = mEast - mWest;
    mDistanceLatitude = mSouth - mNorth;
  }

  @Override
  public void install(TileView tileView) {
    tileView.addReadyListener(this);
    tileView.addListener(this);
  }

  @Override
  public void onReady(TileView tileView) {
    mPixelWidth = tileView.getContentWidth();
    mPixelHeight = tileView.getContentHeight();
  }

  @Override
  public void onScaleChanged(float scale, float previous) {
    mScale = scale;
  }

  // coordinate to pixel is multiplied by scale, pixel to coordinate is divided by scale

  /**
   * Translate a relative X position to an absolute pixel value.
   *
   * @param longitude The relative X position (e.g., longitude) to translate to absolute pixels.
   * @return The translated position as a pixel value.
   */
  public int longitudeToX(double longitude) {
    double factor = (longitude - mWest) / mDistanceLongitude;
    return (int) ((mPixelWidth * factor) * mScale);
  }

  /**
   * Translate a latitude coordinate to an y pixel value.
   *
   * @param latitude The latitude coordinate to translate to an "x" pixel value.
   * @return The translated position as a pixel value.
   */
  public int latitudeToY(double latitude) {
    Log.d("TV", "latitude to translate=" + latitude + ", north=" + mNorth);
    double diff = latitude - mNorth;
    Log.d("TV", "difference=" + diff);
    double factor = diff / mDistanceLatitude;
    Log.d("TV", "factor=" + factor);
    int y = (int) (mPixelHeight * factor);
    Log.d("TV", "y=" + y);
    //double factor = (latitude - mNorth) / mDistanceLatitude;
    return (int) ((mPixelHeight * factor) * mScale);
  }

  /**
   * Translate an absolute pixel value to a relative coordinate.
   *
   * @param x The x value to be translated.
   * @return The relative value of the x coordinate supplied.
   */
  public double xToLongitude(int x) {
    return mWest + (x / mScale) * mDistanceLongitude / mPixelWidth;
  }

  /**
   * Translate an absolute pixel value to a relative coordinate.
   *
   * @param y The y value to be translated.
   * @return The relative value of the y coordinate supplied.
   */
  public double yToLatitude(int y) {
    return mNorth + (y / mScale) * mDistanceLatitude / mPixelHeight;
  }

}
