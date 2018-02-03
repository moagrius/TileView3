package demo;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.LinearLayout;

import com.github.moagrius.tileview3.R;
import com.github.moagrius.widget.ZoomScrollView;

/**
 * @author Mike Dunn, 2/3/18.
 */

public class ZoomScrollViewDemoTextViews extends AppCompatActivity {

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_demos_zoomscrollview_textviews);
    ZoomScrollView zoomScrollView = (ZoomScrollView) findViewById(R.id.zoomscrollview);
    zoomScrollView.setShouldVisuallyScaleContents(true);
    LinearLayout linearLayout = (LinearLayout) findViewById(R.id.linearlayout);
    for (int i = 0; i < 100; i++) {
      LinearLayout row = new LinearLayout(this);
      Helpers.populateLinearLayout(row, 100);
      linearLayout.addView(row);
    }
  }

}
