package demo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.moagrius.tileview.TileView;
import com.github.moagrius.tileview.plugins.CoordinatePlugin;
import com.github.moagrius.tileview.plugins.HotSpotPlugin;
import com.github.moagrius.tileview.plugins.InfoWindowPlugin;
import com.github.moagrius.tileview.plugins.MarkerPlugin;
import com.github.moagrius.tileview.plugins.PathPlugin;
import com.github.moagrius.tileview3.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author Mike Dunn, 2/4/18.
 */

public class TileViewDemo extends AppCompatActivity {

//  public static final double NORTH = 39.9639998777094;
//  public static final double WEST = -75.17261900652977;
//  public static final double SOUTH = 39.93699709962642;
//  public static final double EAST = -75.12462846235614;

  // the 39.x are latitude
  // the -75.x are longitude
  public static final double NORTH_LONGITUDE = -75.17261900652977;
  public static final double WEST_LATITUDE = 39.9639998777094;
  public static final double SOUTH_LONGITUDE = -75.12462846235614;
  public static final double EAST_LATITUDE = 39.93699709962642;

  /*

  tileView.defineBounds(
      -75.17261900652977,
      39.9639998777094,
       -75.12462846235614,
      39.9369970996264
    );
   */

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_demos_tileview);

    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inPreferredConfig = Bitmap.Config.RGB_565;
    Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.downsample, options);

    TileView tileView = findViewById(R.id.tileview);
    new TileView.Builder(tileView)
        .installPlugin(new MarkerPlugin(this))
        .installPlugin(new InfoWindowPlugin(this))
        .installPlugin(new CoordinatePlugin(WEST_LATITUDE, NORTH_LONGITUDE, EAST_LATITUDE, SOUTH_LONGITUDE))
        .installPlugin(new HotSpotPlugin())
        .installPlugin(new PathPlugin())
        //.installPlugin(new LowFidelityBackgroundPlugin(bitmap))
        .setSize(17934, 13452)
        .defineZoomLevel("tiles/phi-1000000-%1$d_%2$d.jpg")
        .addReadyListener(this::onReady)
        .build();
    //tileView.defineZoomLevel("tiles/phi-500000-%1$d_%2$d.jpg");
    //tileView.defineZoomLevel(1, "tiles/phi-500000-%1$d_%2$d.jpg");
    //tileView.defineZoomLevel(2, "tiles/phi-250000-%1$d_%2$d.jpg");

  }

  private void onReady(TileView tileView) {

    Log.d("TV", "width=" + tileView.getContentWidth() + ", height=" + tileView.getContentHeight());

    CoordinatePlugin coordinatePlugin = tileView.getPlugin(CoordinatePlugin.class);
    InfoWindowPlugin infoWindowPlugin = tileView.getPlugin(InfoWindowPlugin.class);
    MarkerPlugin markerPlugin = tileView.getPlugin(MarkerPlugin.class);

    List<double[]> sites = coordinates.subList(1, 5);

    // drop some markers, with info window expansions
    String template = "Clicked marker at:\n%1$f\n%2$f";
    View.OnClickListener markerClickListener = view -> {
      Log.d("SV", "clicked!");
      double[] coordinate = (double[]) view.getTag();
      int x = coordinatePlugin.longitudeToX(coordinate[1]);
      int y = coordinatePlugin.latitudeToY(coordinate[0]);
      tileView.smoothScrollTo(x - tileView.getMeasuredWidth() / 2, y - tileView.getMeasuredHeight() / 2);
      TextView infoWindow = new TextView(this);
      String label = String.format(Locale.US, template, coordinate[0], coordinate[1]);
      infoWindow.setText(label);
      infoWindow.setPadding(100, 100, 100, 100);
      infoWindow.setBackgroundColor(Color.GRAY);
      // TODO: this feels clunky.  think about how to handle coordinates wrt markers and info windows
      MarkerPlugin.LayoutParams lp = (MarkerPlugin.LayoutParams) view.getLayoutParams();
      infoWindowPlugin.show(infoWindow, lp.x, lp.y, -0.5f, -1f, 0, 0);
    };

    for (double[] coordinate : sites) {
      int x = coordinatePlugin.longitudeToX(coordinate[1]);
      int y = coordinatePlugin.latitudeToY(coordinate[0]);
      ImageView marker = new ImageView(this);
      marker.setTag(coordinate);
      marker.setImageResource(R.drawable.map_marker_normal);
      marker.setOnClickListener(markerClickListener);
      markerPlugin.addMarker(marker, x, y, -0.5f, -1f, 0, 0);
    }

    // draw a path
    Paint paint = new Paint();
    paint.setStyle(Paint.Style.STROKE);
    paint.setColor(0xFF4286f4);
    paint.setStrokeWidth(0);
    paint.setAntiAlias(true);

    DisplayMetrics metrics = getResources().getDisplayMetrics();
    paint.setShadowLayer(
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, metrics),
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, metrics),
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, metrics),
        0x66000000);
    paint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, metrics));
    paint.setPathEffect(new CornerPathEffect(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, metrics)));

    List<Point> points = new ArrayList<>();
    for (double[] coordinate : sites) {
      Point point = new Point();
      point.x = coordinatePlugin.longitudeToX(coordinate[1]);
      point.y = coordinatePlugin.latitudeToY(coordinate[0]);
      Log.d("TV", "point=" + point);
      points.add(point);
    }

    PathPlugin pathPlugin = tileView.getPlugin(PathPlugin.class);
    pathPlugin.drawPath(points, paint);

  }

  private ArrayList<double[]> coordinates = new ArrayList<>();

  {
    coordinates.add(new double[]{-75.1489070, 39.9484760});
    coordinates.add(new double[]{-75.1494000, 39.9487722});
    coordinates.add(new double[]{-75.1468350, 39.9474180});
    coordinates.add(new double[]{-75.1472000, 39.9482000});
    coordinates.add(new double[]{-75.1437980, 39.9508290});
    coordinates.add(new double[]{-75.1479650, 39.9523130});
    coordinates.add(new double[]{-75.1445500, 39.9472960});
    coordinates.add(new double[]{-75.1506100, 39.9490630});
    coordinates.add(new double[]{-75.1521278, 39.9508083});
    coordinates.add(new double[]{-75.1477600, 39.9475320});
    coordinates.add(new double[]{-75.1503800, 39.9489900});
    coordinates.add(new double[]{-75.1464200, 39.9482000});
    coordinates.add(new double[]{-75.1464850, 39.9498500});
    coordinates.add(new double[]{-75.1487030, 39.9524300});
    coordinates.add(new double[]{-75.1500167, 39.9488750});
    coordinates.add(new double[]{-75.1458360, 39.9479700});
    coordinates.add(new double[]{-75.1498222, 39.9515389});
    coordinates.add(new double[]{-75.1501990, 39.9498900});
    coordinates.add(new double[]{-75.1460060, 39.9474210});
    coordinates.add(new double[]{-75.1490230, 39.9533960});
    coordinates.add(new double[]{-75.1471980, 39.9485350});
    coordinates.add(new double[]{-75.1493500, 39.9490200});
    coordinates.add(new double[]{-75.1500910, 39.9503850});
    coordinates.add(new double[]{-75.1483930, 39.9485040});
    coordinates.add(new double[]{-75.1517260, 39.9473720});
    coordinates.add(new double[]{-75.1525630, 39.9471360});
    coordinates.add(new double[]{-75.1438400, 39.9473390});
    coordinates.add(new double[]{-75.1468240, 39.9495400});
    coordinates.add(new double[]{-75.1466410, 39.9499900});
    coordinates.add(new double[]{-75.1465050, 39.9501110});
    coordinates.add(new double[]{-75.1473460, 39.9436200});
    coordinates.add(new double[]{-75.1501570, 39.9480430});
  }

}
