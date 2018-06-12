package com.github.moagrius.tileview;

import android.content.Context;

import java.io.InputStream;

public interface StreamProvider {
  InputStream getStream(int column, int row, Context context, Object data) throws Exception;
}
