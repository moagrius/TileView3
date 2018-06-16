package demo;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.LinearLayout;

import com.github.moagrius.tileview3.R;

/**
 * @author Mike Dunn, 6/11/17.
 */

public class ScrollViewDemoVertical extends AppCompatActivity {

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_demos_scrollview_vertical);
    LinearLayout linearLayout = findViewById(R.id.linearlayout);
    Helpers.populateLinearLayout(linearLayout, 100);
  }
}
