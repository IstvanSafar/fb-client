package home.fbclient;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Text size and filters, modelled on FB Purity. Saves when leaving the screen. */
public class SettingsActivity extends Activity {

    private final List<Switch> switches = new ArrayList<>();
    private EditText keywords;
    private int zoom;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences p = AppSettings.prefs(this);
        int pad = dp(16);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(pad, pad, pad, pad);

        list.addView(heading("Settings", 24, 0));

        // Text size
        zoom = AppSettings.textZoom(this);
        TextView zoomLabel = new TextView(this);
        zoomLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        list.addView(zoomLabel);
        TextView sample = new TextView(this);
        sample.setText("Sample post text");
        sample.setPadding(0, dp(4), 0, dp(4));
        list.addView(sample);
        SeekBar slider = new SeekBar(this);
        slider.setMax((AppSettings.ZOOM_MAX - AppSettings.ZOOM_MIN) / AppSettings.ZOOM_STEP);
        slider.setProgress((zoom - AppSettings.ZOOM_MIN) / AppSettings.ZOOM_STEP);
        slider.setMinimumHeight(dp(48));
        Runnable refresh = () -> {
            zoomLabel.setText("Text size: " + zoom + "%");
            sample.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f * zoom / 100f);
        };
        refresh.run();
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                zoom = AppSettings.ZOOM_MIN + v * AppSettings.ZOOM_STEP;
                refresh.run();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        list.addView(slider);

        // Filters
        list.addView(heading("Filters", 20, pad));
        for (Object[] t : AppSettings.TOGGLES) {
            Switch s = new Switch(this);
            s.setText((String) t[1]);
            s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            s.setChecked(AppSettings.isOn(this, (String) t[0]));
            s.setTag(t[0]);
            s.setMinHeight(dp(56));
            s.setGravity(Gravity.CENTER_VERTICAL);
            list.addView(s);
            switches.add(s);
        }

        TextView keywordsLabel = new TextView(this);
        keywordsLabel.setText("Keywords: feed posts containing any of these are hidden. "
                + "Also matches what is in the pictures, using Facebook's automatic image "
                + "descriptions (e.g. kutya, macska). One per line or separated by commas.");
        keywordsLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        keywordsLabel.setPadding(0, pad, 0, dp(8));
        list.addView(keywordsLabel);

        keywords = new EditText(this);
        keywords.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        keywords.setMinLines(3);
        keywords.setGravity(Gravity.TOP);
        keywords.setText(p.getString(AppSettings.KEYWORDS, ""));
        list.addView(keywords);

        TextView note = new TextView(this);
        note.setText("Filters work by matching labels on the Facebook page. When Facebook "
                + "changes its page, some filters may stop working until updated.");
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        note.setAlpha(0.7f);
        note.setPadding(0, pad, 0, 0);
        list.addView(note);

        ScrollView scroller = new ScrollView(this);
        scroller.addView(list);
        scroller.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return WindowInsets.CONSUMED;
        });
        setContentView(scroller);
    }

    private TextView heading(String text, int sizeSp, int topPad) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setPadding(0, topPad, 0, dp(8));
        return t;
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences p = AppSettings.prefs(this);
        SharedPreferences.Editor e = p.edit();
        for (Switch s : switches) e.putBoolean((String) s.getTag(), s.isChecked());
        e.putString(AppSettings.KEYWORDS, keywords.getText().toString());
        e.putInt(AppSettings.TEXT_ZOOM, zoom);
        e.putInt(AppSettings.VERSION, p.getInt(AppSettings.VERSION, 0) + 1);
        e.apply();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
