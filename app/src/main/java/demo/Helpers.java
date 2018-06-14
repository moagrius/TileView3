package demo;

import android.content.Context;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * @author Mike Dunn, 6/11/17.
 */

public class Helpers {

  public static void populateLinearLayout(LinearLayout linearLayout, int quantity) {
    Context context = linearLayout.getContext();
    for (int i = 0; i < quantity; i++) {
      TextView textView = new TextView(context);
      textView.setText("Hi I'm TextView #" + i);
      textView.setTextSize(30);
      textView.setPadding(100, 100, 100, 100);
      textView.setBackgroundColor(Color.BLACK);
      linearLayout.addView(textView);
    }
  }
}
