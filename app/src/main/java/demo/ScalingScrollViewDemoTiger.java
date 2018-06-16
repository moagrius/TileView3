package demo;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.github.moagrius.tileview3.R;
import com.github.moagrius.widget.ScalingScrollView;

/**
 * @author Mike Dunn, 2/3/18.
 */

public class ScalingScrollViewDemoTiger extends AppCompatActivity {

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_demos_scalingscrollview_tiger);
    ScalingScrollView scalingScrollView = findViewById(R.id.scalingscrollview);
    scalingScrollView.setScaleLimits(0, 10);
    scalingScrollView.setShouldVisuallyScaleContents(true);
  }
}
