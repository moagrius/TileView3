package demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.github.moagrius.tileview3.R;

public class MainActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    findViewById(R.id.textview_demos_scrollview_vertical).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        startDemo(ScrollViewDemoVertical.class);
      }
    });
    findViewById(R.id.textview_demos_scrollview_horizontal).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        startDemo(ScrollViewDemoHorizontal.class);
      }
    });
    findViewById(R.id.textview_demos_scrollview_universal).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        startDemo(ScrollViewDemoUniversal.class);
      }
    });
    findViewById(R.id.textview_demos_zoomscrollview_textviews).setOnClickListener(new View.OnClickListener(){
      @Override
      public void onClick(View view) {
        startDemo(ZoomScrollViewDemoTextViews.class);
      }
    });
    findViewById(R.id.textview_demos_zoomscrollview_tiger).setOnClickListener(new View.OnClickListener(){
      @Override
      public void onClick(View view) {
        startDemo(ZoomScrollViewDemoTiger.class);
      }
    });
  }

  private void startDemo(Class<? extends Activity> activityClass) {
    Intent intent = new Intent(this, activityClass);
    startActivity(intent);
  }

}
