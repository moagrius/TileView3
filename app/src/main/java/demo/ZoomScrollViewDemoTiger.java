package demo;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.github.moagrius.tileview3.R;
import com.github.moagrius.widget.ZoomScrollView;

/**
 * @author Mike Dunn, 2/3/18.
 */

public class ZoomScrollViewDemoTiger extends AppCompatActivity {

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_demos_zoomscrollview_tiger);
    ZoomScrollView zoomScrollView = (ZoomScrollView) findViewById(R.id.zoomscrollview);
    zoomScrollView.setScaleLimits(0, 10);
    zoomScrollView.setShouldVisuallyScaleContents(true);
  }
}
