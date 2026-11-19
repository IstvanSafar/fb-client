package home.fbclient;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** User settings. The toggle keys match the FILTER_LABELS keys in cleaner.js. */
final class AppSettings {

    /** key, label, default */
    static final Object[][] TOGGLES = {
            {"hideSponsored", "Hide sponsored posts", true},
            {"hideAppBanners", "Hide \"Open in app\" banners", true},
            {"hideSuggested", "Hide suggested posts (\"Suggested for you\")", false},
            {"hideUnfollowed", "Hide posts from groups and pages you don't follow (with a Join / Follow button)", false},
            {"hideReelsInFeed", "Hide Reels in the feed (the Reels menu item stays)", false},
            {"hidePeopleYouMayKnow", "Hide \"People you may know\"", false},
            {"hideSuggestedGroups", "Hide suggested groups", false},
            {"autoplayVideos", "Autoplay videos", true},
    };
    static final String KEYWORDS = "keywords";
    static final String VERSION = "version";
    /** WebView text zoom in percent (WebSettings.setTextZoom) */
    static final String TEXT_ZOOM = "textZoom";
    static final int ZOOM_MIN = 50, ZOOM_MAX = 200, ZOOM_STEP = 10;

    private AppSettings() {}

    static SharedPreferences prefs(Context c) {
        SharedPreferences p = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
        migrateLegacy(c, p);
        return p;
    }

    static boolean isOn(Context c, String key) {
        for (Object[] t : TOGGLES) {
            if (t[0].equals(key)) return prefs(c).getBoolean(key, (Boolean) t[2]);
        }
        return false;
    }

    static int textZoom(Context c) {
        return prefs(c).getInt(TEXT_ZOOM, 100);
    }

    /** Settings object handed to cleaner.js as window.__fbcSettings. */
    static String toJson(Context c) {
        try {
            JSONObject o = new JSONObject();
            for (Object[] t : TOGGLES) o.put((String) t[0], isOn(c, (String) t[0]));
            JSONArray words = new JSONArray();
            for (String s : prefs(c).getString(KEYWORDS, "").split("[\\n,]")) {
                String w = s.trim().toLowerCase();
                if (w.length() >= 2) words.put(w);
            }
            o.put("keywords", words);
            return o.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    /**
     * One-time move of the settings saved by versions up to 2.1 (file "szurok", Hungarian
     * keys) to the current file and keys. Can be removed once every install is past 3.0.
     */
    private static void migrateLegacy(Context c, SharedPreferences p) {
        if (p.getBoolean("migrated", false)) return;
        SharedPreferences old = c.getSharedPreferences("szurok", Context.MODE_PRIVATE);
        String[][] map = {
                {"hirdetes", "hideSponsored"}, {"appSav", "hideAppBanners"},
                {"javasolt", "hideSuggested"}, {"nemKovetett", "hideUnfollowed"},
                {"reelsFeed", "hideReelsInFeed"}, {"ismeros", "hidePeopleYouMayKnow"},
                {"csoportJavaslat", "hideSuggestedGroups"}, {"videoAuto", "autoplayVideos"},
        };
        SharedPreferences.Editor e = p.edit();
        for (String[] m : map) {
            if (old.contains(m[0])) e.putBoolean(m[1], old.getBoolean(m[0], false));
        }
        if (old.contains("kulcsszavak")) e.putString(KEYWORDS, old.getString("kulcsszavak", ""));
        if (old.contains("betumeret")) e.putInt(TEXT_ZOOM, old.getInt("betumeret", 100));
        e.putBoolean("migrated", true);
        e.apply();
        old.edit().clear().apply();
    }
}
