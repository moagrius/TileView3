package com.github.moagrius.tileview.plugins;

import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.view.MotionEvent;

import com.github.moagrius.tileview.TileView;

import java.util.ArrayList;
import java.util.List;

public class HotSpotPlugin implements TileView.Plugin, TileView.Listener, TileView.TouchListener {

  private List<HotSpot> mHotSpots = new ArrayList<>();
  private float mScale;
  private int mX;
  private int mY;

  @Override
  public void install(TileView tileView) {
    tileView.addTouchListener(this);
    tileView.addListener(this);
  }

  @Override
  public void onTouch(MotionEvent event) {
    int x = (int) ((event.getX() + mX) * mScale);
    int y = (int) ((event.getY() + mY) * mScale);
    processHit(x, y);
  }

  @Override
  public void onScaleChanged(float scale, float previous) {
    mScale = scale;
  }

  @Override
  public void onScrollChanged(int x, int y) {
    mX = x;
    mY = y;
  }

  public HotSpot addHotSpot(List<Point> positions, HotSpotTapListener hotSpotTapListener) {
    Path path = new Path();
    Point start = positions.get(0);
    path.moveTo(start.x, start.y);
    for (int i = 1; i < positions.size(); i++) {
      Point position = positions.get(i);
      path.lineTo(position.x, position.y);
    }
    path.close();
    RectF bounds = new RectF();
    path.computeBounds(bounds, true);
    Rect rect = new Rect();
    bounds.round(rect);
    Region clip = new Region(rect);
    HotSpot hotSpot = new HotSpot();
    hotSpot.setPath(path, clip);
    hotSpot.setHotSpotTapListener(hotSpotTapListener);
    return addHotSpot(hotSpot);
  }

  public HotSpot addHotSpot(HotSpot hotSpot) {
    addHotSpot(hotSpot);
    return hotSpot;
  }

  public void clear() {
    mHotSpots.clear();
  }

  private HotSpot getMatch(int x, int y) {  // must be scaled points
    for (int i = mHotSpots.size() - 1; i >= 0; i--) {
      HotSpot hotSpot = mHotSpots.get(i);
      if (hotSpot.contains(x, y)) {
        return hotSpot;
      }
    }
    return null;
  }

  public void processHit(int x, int y) {
    HotSpot hotSpot = getMatch(x, y);
    if (hotSpot != null) {
      HotSpotTapListener spotListener = hotSpot.getHotSpotTapListener();
      if (spotListener != null) {
        spotListener.onHotSpotTap(hotSpot, x, y);
      }
    }
  }

  public interface HotSpotTapListener {
    void onHotSpotTap(HotSpot hotSpot, int x, int y);
  }

  public static class HotSpot extends Region {

    private Object mTag;
    private HotSpotTapListener mHotSpotTapListener;

    public Object getTag() {
      return mTag;
    }

    public void setTag(Object object) {
      mTag = object;
    }

    public void setHotSpotTapListener(HotSpotTapListener hotSpotTapListener) {
      mHotSpotTapListener = hotSpotTapListener;
    }

    public HotSpotTapListener getHotSpotTapListener() {
      return mHotSpotTapListener;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (obj instanceof HotSpot) {
        HotSpot hotSpot = (HotSpot) obj;
        return super.equals(hotSpot) && hotSpot.mHotSpotTapListener.equals(mHotSpotTapListener);
      }
      return false;
    }
  }

}
