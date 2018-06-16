package com.github.moagrius.tileview.plugins;

import android.graphics.Canvas;
import android.graphics.Path;

import com.github.moagrius.tileview.TileView;

import java.util.List;

public class PathPlugin implements TileView.Plugin, TileView.CanvasDecorator {

  /**
   * Convenience method to convert a List of coordinates (pairs of doubles) to a Path instance.
   *
   * @param positions   List of coordinates (pairs of doubles).
   * @param shouldClose True if the path should be closed at the end of this operation.
   * @return The Path instance created from the positions supplied.
   */
  public static Path pathFromPositions(List<float[]> positions, boolean shouldClose) {
    Path path = new Path();
    float[] start = positions.get(0);
    path.moveTo(start[0], start[1]);
    for (int i = 1; i < positions.size(); i++) {
      float[] position = positions.get(i);
      path.lineTo(position[0], position[1]);
    }
    if (shouldClose) {
      path.close();
    }
    return path;
  }

  @Override
  public void install(TileView tileView) {
    tileView.addCanvasDecorator(this);
  }

  @Override
  public void decorate(Canvas canvas) {

  }
}
