package home.fbclient;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Text size and filters, modelled on FB Purity. Saves when leaving the screen. */
public class SettingsActivity extends Activity {

    private static final String RELEASES_PAGE = "https://github.com/IstvanSafar/fb-client/releases";
    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/IstvanSafar/fb-client/releases/latest";

    private final List<Switch> switches = new ArrayList<>();
    private EditText keywords;
    private Button updateButton;
    private TextView updateStatus;
    /** Set when a newer release was found: the button then opens this page. */
    private String releaseUrl;
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

        // Keyword filter: a free text field, the user types their own words
        list.addView(heading("Hide posts by your own words", 20, pad));
        TextView keywordsLabel = new TextView(this);
        keywordsLabel.setText("Type any words into the box below. Feed posts containing any "
                + "of them are hidden. It also checks what is in the pictures, using Facebook's "
                + "automatic image descriptions. One word per line, or separate them with commas. "
                + "Leave the box empty to hide nothing.");
        keywordsLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        keywordsLabel.setPadding(0, 0, 0, dp(8));
        list.addView(keywordsLabel);

        keywords = new EditText(this);
        keywords.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        keywords.setMinLines(4);
        keywords.setGravity(Gravity.TOP);
        keywords.setBackgroundResource(R.drawable.text_box);
        keywords.setHint("Tap here and type, for example:\npolitika\nhoroszkóp");
        keywords.setText(p.getString(AppSettings.KEYWORDS, ""));
        list.addView(keywords);

        TextView note = new TextView(this);
        note.setText("Filters work by matching labels on the Facebook page. When Facebook "
                + "changes its page, some filters may stop working until updated.");
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        note.setAlpha(0.7f);
        note.setPadding(0, pad, 0, 0);
        list.addView(note);

        // Version and manual update check (only when tapped, nothing runs in the background)
        list.addView(heading("About", 20, pad));
        TextView version = new TextView(this);
        version.setText("Version " + BuildConfig.VERSION_NAME);
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        list.addView(version);
        updateStatus = new TextView(this);
        updateStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        updateStatus.setPadding(0, dp(8), 0, dp(8));
        updateStatus.setVisibility(View.GONE);
        updateButton = new Button(this);
        updateButton.setText("Check for updates");
        updateButton.setOnClickListener(v -> checkForUpdates());
        list.addView(updateButton);
        list.addView(updateStatus);

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

    /**
     * Asks GitHub for the latest release. If it is newer, the button opens the release page in
     * the browser, where the APK can be downloaded. The app never installs anything itself.
     */
    private void checkForUpdates() {
        if (releaseUrl != null) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)));
            return;
        }
        updateButton.setEnabled(false);
        updateStatus.setVisibility(View.VISIBLE);
        updateStatus.setText("Checking…");
        new Thread(() -> {
            String status;
            String url = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(LATEST_RELEASE_API).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "fb-client");
                if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
                StringBuilder body = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(
                        c.getInputStream(), StandardCharsets.UTF_8))) {
                    for (String line; (line = r.readLine()) != null; ) body.append(line);
                }
                JSONObject release = new JSONObject(body.toString());
                String latest = release.getString("tag_name").replaceFirst("^v", "");
                if (compareVersions(latest, BuildConfig.VERSION_NAME) > 0) {
                    status = "Version " + latest + " is available.";
                    url = release.optString("html_url", RELEASES_PAGE);
                } else {
                    status = "You have the latest version.";
                }
            } catch (IOException | JSONException e) {
                status = "Could not check for updates. Check the internet connection and try again.";
            } finally {
                if (c != null) c.disconnect();
            }
            String finalStatus = status, finalUrl = url;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                updateStatus.setText(finalStatus);
                releaseUrl = finalUrl;
                updateButton.setText(finalUrl != null ? "Open download page" : "Check for updates");
                updateButton.setEnabled(true);
            });
        }).start();
    }

    /** Compares dotted version numbers, e.g. 3.10 > 3.4. Non-numeric parts count as 0. */
    static int compareVersions(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int d = Integer.compare(part(x, i), part(y, i));
            if (d != 0) return d;
        }
        return 0;
    }

    private static int part(String[] parts, int i) {
        if (i >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[i].replaceAll("\\D.*", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
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
