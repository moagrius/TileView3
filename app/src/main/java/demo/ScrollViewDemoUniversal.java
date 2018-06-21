package demo;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.widget.LinearLayout;

import com.github.moagrius.tileview3.R;

/**
 * @author Mike Dunn, 6/11/17.
 */

public class ScrollViewDemoUniversal extends Activity {

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_demos_scrollview_universal);
    LinearLayout linearLayout = (LinearLayout) findViewById(R.id.linearlayout);
    for (int i = 0; i < 100; i++) {
      LinearLayout row = new LinearLayout(this);
      Helpers.populateLinearLayout(row, 100);
      linearLayout.addView(row);
    }
  }
}
