package com.github.moagrius.tileview.plugins;

import android.graphics.Point;
import android.util.Log;

import com.github.moagrius.tileview.TileView;

/**
 * Note that coordinates are generally expressed as lat, lng
 * while 2D space is generally x, y
 * these are reversed - latitude is the y-axis of the earth, and longitude is the x-axis
 */
public class CoordinatePlugin implements TileView.Plugin, TileView.ReadyListener {

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
  }

  @Override
  public void onReady(TileView tileView) {
    mPixelWidth = tileView.getContentWidth();
    mPixelHeight = tileView.getContentHeight();
  }

  /**
   * Translate a relative X position to an absolute pixel value.
   *
   * @param x The relative X position (e.g., longitude) to translate to absolute pixels.
   * @return The translated position as a pixel value.
   */
  public int longitudeToX(double x) {
    double factor = (x - mWest) / mDistanceLongitude;
    return (int) (mPixelWidth * factor);
  }

  /**
   * Translate a latitude coordinate to an y pixel value.
   *
   * @param latitude The latitude coordinate to translate to an "x" pixel value.
   * @return The translated position as a pixel value.
   */
  public int latitudeToY(double latitude) {
    Log.d("TV", "latitude to translate=" + latitude);
    double diff = latitude - mNorth;
    Log.d("TV", "difference=" + diff);
    
    double factor = (latitude - mNorth) / mDistanceLatitude;
    return (int) (mPixelHeight * factor);
  }

  /**
   * Translate an absolute pixel value to a relative coordinate.
   *
   * @param x The x value to be translated.
   * @return The relative value of the x coordinate supplied.
   */
  public double xToLongitude(int x) {
    return mWest + (x * mDistanceLongitude / mPixelWidth);
  }

  /**
   * Translate an absolute pixel value to a relative coordinate.
   *
   * @param y The y value to be translated.
   * @return The relative value of the y coordinate supplied.
   */
  public double yToLatitude(int y) {
    return mNorth + (y * mDistanceLatitude / mPixelHeight);
  }

  /**
   * Get a Point instance from lat lng coordinate.
   *
   * @param latitude
   * @param longitude
   * @return
   */
  public Point getPointFromLatLng(double latitude, double longitude) {
    return new Point(longitudeToX(longitude), latitudeToY(latitude));
  }

  /**
   * Determines if a given position (x, y) falls within the bounds defined.
   *
   * @param x The x value of the coordinate to test.
   * @param y The y value of the coordinate to test.
   * @return True if the point falls within the defined area; false if not.
   */
  public boolean contains(double x, double y) {
    return y <= mNorth && y >= mSouth && x >= mWest && x <= mEast;
  }

  /**
   * Get the left boundary.
   *
   * @return The left boundary (e.g., west longitude).
   */
  public double getWest() {
    return mWest;
  }

  /**
   * Get the right boundary.
   *
   * @return The right boundary (e.g., east longitude).
   */
  public double getEast() {
    return mEast;
  }

  /**
   * Get the top boundary.
   *
   * @return The top boundary (e.g., north latitude).
   */
  public double getNorth() {
    return mNorth;
  }

  /**
   * Get the bottom boundary.
   *
   * @return The bottom boundary (e.g., south latitude).
   */
  public double getSouth() {
    return mSouth;
  }

}
