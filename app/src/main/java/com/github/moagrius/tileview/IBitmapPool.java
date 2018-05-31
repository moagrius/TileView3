package com.github.moagrius.tileview;

import android.graphics.Bitmap;

public interface IBitmapPool {
    Bitmap get(Tile tile);
    void add(Bitmap bitmap);
}
