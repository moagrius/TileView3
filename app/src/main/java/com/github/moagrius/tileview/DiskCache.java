package com.github.moagrius.tileview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;

import com.jakewharton.disklrucache.DiskLruCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DiskCache implements TileView.Cache {

  private static final String DIRECTORY_NAME = "com.github.moagrius.TileView";
  private static final int IO_BUFFER_SIZE = 8 * 1024;

  private DiskLruCache mDiskCache;

  public DiskCache(Context context, int size) throws IOException {
    File directory = new File(context.getCacheDir(), DIRECTORY_NAME);
    mDiskCache = DiskLruCache.open(directory, 1, 1, size);
  }

  private boolean writeBitmapToFile(Bitmap bitmap, DiskLruCache.Editor editor) throws IOException {
    OutputStream outputStream = null;
    try {
      outputStream = new BufferedOutputStream(editor.newOutputStream(0), IO_BUFFER_SIZE);
      return bitmap.compress(CompressFormat.PNG, 0, outputStream);
    } finally {
      if (outputStream != null) {
        outputStream.close();
      }
    }
  }

  public Bitmap put(String key, Bitmap data) {
    DiskLruCache.Editor editor = null;
    if (contains(key)) {  // TODO:
      return data;
    }
    try {
      editor = mDiskCache.edit(key);
      if (editor != null) {
        if (writeBitmapToFile(data, editor)) {
          mDiskCache.flush();
          editor.commit();
        } else {
          editor.abort();
        }
      }
    } catch (IOException e) {
      try {
        if (editor != null) {
          editor.abort();
        }
      } catch (IOException ignored) {
        //
      }
    }
    return data;
  }

  public Bitmap get(String key) {
    Bitmap bitmap = null;
    DiskLruCache.Snapshot snapshot = null;
    try {

      snapshot = mDiskCache.get(key);
      if (snapshot == null) {
        return null;
      }
      final InputStream in = snapshot.getInputStream(0);
      if (in != null) {
        final BufferedInputStream buffIn = new BufferedInputStream(in, IO_BUFFER_SIZE);
        bitmap = BitmapFactory.decodeStream(buffIn);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (snapshot != null) {
        snapshot.close();
      }
    }
    return bitmap;
  }

  public boolean contains(String key) {
    boolean contained = false;
    DiskLruCache.Snapshot snapshot = null;
    try {
      snapshot = mDiskCache.get(key);
      contained = snapshot != null;
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (snapshot != null) {
        snapshot.close();
      }
    }
    return contained;
  }

  public void clear() {
    try {
      mDiskCache.delete();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
