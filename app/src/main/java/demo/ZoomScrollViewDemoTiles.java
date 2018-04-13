package demo;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.github.moagrius.tileview.TileView;
import com.github.moagrius.tileview3.R;
import com.github.moagrius.widget.ZoomScrollView;

/**
 * @author Mike Dunn, 2/4/18.
 */

public class ZoomScrollViewDemoTiles extends AppCompatActivity {

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_demos_zoomscrollview_tileview);
    ZoomScrollView zoomScrollView = findViewById(R.id.zoomscrollview);
    TileView tileView = findViewById(R.id.tileview);
    //zoomScrollView.setShouldVisuallyScaleContents(true);
  }

}
