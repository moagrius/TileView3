package com.github.moagrius.tileview3;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.LinearLayout;

/**
 * @author Mike Dunn, 6/11/17.
 */

public class ScrollViewDemoHorizontal extends AppCompatActivity {

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_demos_scrollview_horizontal);
    LinearLayout linearLayout = (LinearLayout) findViewById(R.id.linearlayout);
    Helpers.populateLinearLayout(linearLayout, 100);
  }
}
