package com.github.moagrius.tileview.plugins;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import com.github.moagrius.tileview.TileView;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PathPlugin implements TileView.Plugin, TileView.CanvasDecorator {

  private static final int DEFAULT_STROKE_COLOR = 0xFF000000;
  private static final int DEFAULT_STROKE_WIDTH = 10;

  private Path mRecyclerPath = new Path();
  private Paint mDefaultPaint = new Paint();
  private Set<DrawablePath> mDrawablePaths = new LinkedHashSet<>();

  {
    mDefaultPaint.setStyle(Paint.Style.STROKE);
    mDefaultPaint.setColor(DEFAULT_STROKE_COLOR);
    mDefaultPaint.setStrokeWidth(DEFAULT_STROKE_WIDTH);
    mDefaultPaint.setAntiAlias(true);
  }

  @Override
  public void install(TileView tileView) {
    tileView.addCanvasDecorator(this);
  }

  @Override
  public void decorate(Canvas canvas) {
    for (DrawablePath drawablePath : mDrawablePaths) {
      mRecyclerPath.set(drawablePath.getPath());
      canvas.drawPath(mRecyclerPath, drawablePath.getPaint());
    }
  }

  public DrawablePath drawPath(List<int[]> positions, Paint paint) {
    Path path = new Path();
    int[] start = positions.get(0);
    path.moveTo(start[0], start[1]);
    for (int i = 1; i < positions.size(); i++) {
      int[] position = positions.get(i);
      path.lineTo(position[0], position[1]);
    }
    return addPath(path, paint);
  }

  public DrawablePath addPath(Path path, Paint paint) {
    if (paint == null) {
      paint = mDefaultPaint;
    }
    return addPath(new DrawablePath(path, paint));
  }

  public DrawablePath addPath(DrawablePath DrawablePath) {
    mDrawablePaths.add(DrawablePath);
    return DrawablePath;
  }

  public void removePath(DrawablePath path) {
    mDrawablePaths.remove(path);
  }

  public void clear() {
    mDrawablePaths.clear();
  }

  public static class DrawablePath {
    private Path mPath;
    private Paint mPaint;

    public DrawablePath(Path path, Paint paint) {
      mPath = path;
      mPaint = paint;
    }

    public Path getPath() {
      return mPath;
    }

    public Paint getPaint() {
      return mPaint;
    }
  }

}
