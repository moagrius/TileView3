package com.github.moagrius.tileview;

import java.util.ArrayList;

public class DetailList extends ArrayList<String> {
  @Override
  public String set(int index, String element) {
    // fill with nulls
    int delta = index - (size() - 1);
    for (int i = 0; i < delta; i++) {
      add(null);
    }
    return super.set(index, element);
  }
}
