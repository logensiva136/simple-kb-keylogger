package ambank.gtr.testteam;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Tiny setup screen: enable the keyboard, pick it, and try it out. */
public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad * 2, pad, pad);

        TextView title = new TextView(this);
        title.setText("✨ Fancy Keyboard");
        title.setTextSize(28);
        root.addView(title);

        Button enable = new Button(this);
        enable.setText("1. Enable keyboard");
        enable.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(enable);

        Button pick = new Button(this);
        pick.setText("2. Switch to Fancy Keyboard");
        pick.setOnClickListener(v -> ((InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
        root.addView(pick);

        EditText test = new EditText(this);
        test.setHint("3. Try it here (tap 🎨 to change theme)");
        root.addView(test);

        setContentView(root);
    }
}
