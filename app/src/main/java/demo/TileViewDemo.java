package demo;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.moagrius.tileview.TileView;
import com.github.moagrius.tileview.plugins.InfoWindowPlugin;
import com.github.moagrius.tileview.plugins.MarkerPlugin;
import com.github.moagrius.tileview3.R;

/**
 * @author Mike Dunn, 2/4/18.
 */

public class TileViewDemo extends AppCompatActivity {

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_demos_tileview);

    TileView tileView = findViewById(R.id.tileview);
    new TileView.Builder(tileView)
        .installPlugin(new MarkerPlugin(this))
        .installPlugin(new InfoWindowPlugin(this))
        .setSize(17934, 13452)
        .defineZoomLevel("tiles/phi-1000000-%1$d_%2$d.jpg")
        .build();
    //tileView.defineZoomLevel("tiles/phi-500000-%1$d_%2$d.jpg");
    //tileView.defineZoomLevel(1, "tiles/phi-500000-%1$d_%2$d.jpg");
    //tileView.defineZoomLevel(2, "tiles/phi-250000-%1$d_%2$d.jpg");

    MarkerPlugin markerPlugin = tileView.getPlugin(MarkerPlugin.class);
    ImageView marker = new ImageView(this);
    marker.setImageResource(R.mipmap.ic_launcher);
    markerPlugin.addMarker(marker, 800, 800, -0.5f, -1f, 0, 0);

    marker.setOnClickListener(view -> {
      Log.d("SV", "clicked!");
      InfoWindowPlugin infoWindowPlugin = tileView.getPlugin(InfoWindowPlugin.class);
      TextView infoWindow = new TextView(this);
      infoWindow.setText("I'm a callout!");
      infoWindow.setPadding(100, 100, 100, 100);
      infoWindow.setBackgroundColor(Color.GRAY);
      infoWindowPlugin.addMarker(infoWindow, 800, 800, -0.5f, -1f, 0, 0);
    });

  }

}
