package demo;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.github.moagrius.tileview.TileView;
import com.github.moagrius.tileview3.R;

/**
 * @author Mike Dunn, 2/4/18.
 */

public class ZoomScrollViewDemoTiles extends AppCompatActivity {

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_demos_zoomscrollview_tileview);
    TileView tileView = findViewById(R.id.tileview);
    //tileView.defineZoomLevel("tiles/phi-1000000-%1$d_%2$d.jpg");
    tileView.defineZoomLevel("tiles/phi-500000-%1$d_%2$d.jpg");
    //tileView.defineZoomLevel(1, "tiles/phi-500000-%1$d_%2$d.jpg");
    //tileView.defineZoomLevel(2, "tiles/phi-250000-%1$d_%2$d.jpg");
  }

}
