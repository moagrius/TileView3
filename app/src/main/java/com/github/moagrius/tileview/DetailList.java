package com.github.moagrius.tileview;

import android.util.Log;

import java.util.ArrayList;

public class DetailList extends ArrayList<Detail> {

  @Override
  public Detail set(int zoom, Detail detail) {
    // fill with nulls
    while (size() <= zoom) {
      add(null);
    }
    return super.set(zoom, detail);
  }

  public Detail getHighestDefined() {
    Log.d("TV", "getHighestDefined");
    for (int i = size() - 1; i >= 0; i--) {
      Log.d("TV", "getHighestDefined, i=" + i + ", detail=" + get(i));
      Detail detail = get(i);
      if (detail != null) {
        return detail;
      }
    }
    return null;
  }

}
