package home.fbclient;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.WindowInsets;
import android.view.WindowMetrics;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.window.OnBackInvokedDispatcher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Stripped-down Facebook client: a WebView showing m.facebook.com.
 * INTERNET permission only, nothing runs in the background, external links open in the
 * browser, and third-party and logging requests are dropped before they load.
 */
public class MainActivity extends Activity {

    private static final String HOME_URL = "https://m.facebook.com/";
    private static final String LOG_TAG = "fbclient";
    private static final int FILE_CHOOSER_REQUEST = 1;

    /** The main page may navigate within these. Everything else opens in the browser. */
    private static final String[] PAGE_DOMAINS = {
            "facebook.com", "meta.com",
    };

    /** Subresources (images, scripts, video) may load only from these. */
    private static final String[] RESOURCE_DOMAINS = {
            "facebook.com", "fbcdn.net", "fbsbx.com", "meta.com",
            "gstatic.com", "recaptcha.net", // reCAPTCHA on the login checkpoint
    };

    /** Facebook's own logging endpoints. If something breaks because of them, remove it here. */
    private static final String[] LOGGING_PATHS = {
            "/ajax/bz", "/ajax/bnzai", "/a/bz", "/ajax/logging/",
            "/logging_client_events", "/ajax/webstorage/process_keys",
    };

    private static final int MENU_FEED = 1, MENU_REELS = 2, MENU_GROUPS = 3,
            MENU_MARKETPLACE = 4, MENU_REFRESH = 5, MENU_SETTINGS = 6;

    private WebView web;
    private ZoomFrame zoomFrame;
    private ValueCallback<Uri[]> fileCallback;
    private String cleanerJs = "";
    private int settingsVersion;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // WebView inspectable over adb (chrome://inspect) only in builds made with -Debug
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG_WEBVIEW);

        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        zoomFrame = new ZoomFrame(this);
        zoomFrame.setZoom(AppSettings.textZoom(this) / 100f);
        zoomFrame.addView(web);
        root.addView(zoomFrame, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(createMenuButton(), menuButtonLayout());
        setContentView(root);
        settingsVersion = AppSettings.prefs(this).getInt(AppSettings.VERSION, 0);
        setUpSystemBars(root);

        cleanerJs = readAsset("cleaner.js");
        configureWebView();

        setWindowSizeCookie();
        if (state == null || web.restoreState(state) == null) {
            web.loadUrl(HOME_URL);
        }

        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
                    if (web.canGoBack()) web.goBack();
                    else finish();
                });
    }

    // Menu: Reels has its own item, plus settings

    private ImageButton createMenuButton() {
        ImageButton b = new ImageButton(this);
        b.setImageDrawable(null);
        b.setBackgroundResource(R.drawable.menu_button);
        b.setContentDescription("Menu");
        b.setElevation(dp(4));
        b.setOnClickListener(v -> {
            PopupMenu m = new PopupMenu(this, v);
            m.getMenu().add(0, MENU_FEED, 0, "Feed");
            m.getMenu().add(0, MENU_REELS, 1, "Reels");
            m.getMenu().add(0, MENU_GROUPS, 2, "Groups");
            m.getMenu().add(0, MENU_MARKETPLACE, 3, "Marketplace");
            m.getMenu().add(0, MENU_REFRESH, 4, "Refresh");
            m.getMenu().add(0, MENU_SETTINGS, 5, "Settings…");
            m.setOnMenuItemClickListener(this::onMenuItem);
            m.show();
        });
        return b;
    }

    private FrameLayout.LayoutParams menuButtonLayout() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(48), dp(48),
                Gravity.BOTTOM | Gravity.END);
        lp.setMargins(0, 0, dp(16), dp(16));
        return lp;
    }

    private boolean onMenuItem(MenuItem i) {
        switch (i.getItemId()) {
            case MENU_FEED: web.loadUrl(HOME_URL); return true;
            case MENU_REELS: web.loadUrl(HOME_URL + "reel/"); return true;
            case MENU_GROUPS: web.loadUrl(HOME_URL + "groups/"); return true;
            case MENU_MARKETPLACE: web.loadUrl(HOME_URL + "marketplace/"); return true;
            case MENU_REFRESH: web.reload(); return true;
            case MENU_SETTINGS: startActivity(new Intent(this, SettingsActivity.class)); return true;
        }
        return false;
    }

    /**
     * Facebook's server lays out the page for the window size in the "wd" and "vpd" cookies
     * (CSS pixels, e.g. 412x892), which its own script updates only after the page has loaded. Set it
     * before loading, from the window size divided by the zoom, so the first page already
     * fits the zoomed window.
     */
    private void setWindowSizeCookie() {
        WindowMetrics m = getWindowManager().getCurrentWindowMetrics();
        Insets i = m.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        float px = getResources().getDisplayMetrics().density * zoomFrame.zoom();
        int w = Math.round((m.getBounds().width() - i.left - i.right) / px);
        int h = Math.round((m.getBounds().height() - i.top - i.bottom) / px);
        // vpd is "v1;<height>x<width>x<pixel ratio>", the server reads it too
        String dpr = String.valueOf(getResources().getDisplayMetrics().density);
        CookieManager cm = CookieManager.getInstance();
        cm.setCookie("https://m.facebook.com",
                "wd=" + w + "x" + h + "; domain=.facebook.com; path=/; secure");
        cm.setCookie("https://m.facebook.com",
                "vpd=v1%3B" + h + "x" + w + "x" + dpr + "; domain=.facebook.com; path=/; secure");
    }

    /** Injects the current settings and the cleaner script into the page. */
    private void runCleaner(WebView v) {
        if (cleanerJs.isEmpty()) return;
        v.evaluateJavascript("window.__fbcSettings=" + AppSettings.toJson(this) + ";" + cleanerJs, null);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Edge-to-edge: keep content clear of the status bar, navigation bar and keyboard. */
    private void setUpSystemBars(FrameLayout root) {
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        root.setBackgroundColor(dark ? 0xFF242526 : 0xFFFFFFFF);
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) {
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            c.setSystemBarsAppearance(dark ? 0 : light, light);
        }
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return WindowInsets.CONSUMED;
        });
    }

    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setGeolocationEnabled(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        // Left at the default. It does not stop Facebook's muted autoplay; the autoplay switch
        // is enforced in cleaner.js instead.
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setTextZoom(100); // the page zoom is done by ZoomFrame, see there why
        // The scroll bar would show as a strip on the right, made thicker by ZoomFrame
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        // With "; wv" Facebook detects a WebView and pushes the app
        s.setUserAgentString(s.getUserAgentString()
                .replace("; wv)", ")")
                .replaceAll("Version/\\d+(\\.\\d+)* ", ""));

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, false);

        web.setWebViewClient(new Client());
        web.setWebChromeClient(new ChromeClient());
    }

    private class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            Uri u = r.getUrl();
            String scheme = u.getScheme();
            // Embedded frame (e.g. reCAPTCHA): keep it in place, the resource filter checks it
            if (!r.isForMainFrame() && isHttp(scheme)) return false;
            // intent://, fb://, fb-messenger:// : attempts to open the official app, refused
            if (!isHttp(scheme)) return true;
            String host = u.getHost();
            // l.facebook.com/l.php?u=... : Facebook's click-tracking redirect, skipped
            if ("l.facebook.com".equals(host) || "lm.facebook.com".equals(host)) {
                String target = u.getQueryParameter("u");
                if (target != null) openExternally(Uri.parse(target));
                return true;
            }
            if (inDomains(host, PAGE_DOMAINS)) return false;
            openExternally(u);
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
            if (r.isForMainFrame()) return null;
            Uri u = r.getUrl();
            if (!isHttp(u.getScheme())) return null; // data:, blob:
            String host = u.getHost();
            String path = u.getPath() == null ? "" : u.getPath();
            if (!inDomains(host, RESOURCE_DOMAINS) && !isRecaptcha(host, path)) {
                Log.i(LOG_TAG, "blocked domain: " + host); // host only, never the path
                return emptyResponse();
            }
            for (String p : LOGGING_PATHS) {
                if (path.startsWith(p)) return emptyResponse();
            }
            return null;
        }

        @Override
        public void onPageFinished(WebView v, String url) {
            runCleaner(v);
        }

        @Override
        public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
            if (!r.isForMainFrame()) return;
            String failed = r.getUrl().toString();
            String html = "<html><head><meta name=viewport content='width=device-width'>"
                    + "<style>body{font-family:sans-serif;text-align:center;padding-top:30vh;"
                    + "color:#888}a{display:inline-block;margin-top:16px;padding:12px 28px;"
                    + "background:#1F5FBF;color:#fff;border-radius:8px;text-decoration:none}"
                    + "</style></head><body>Could not load the page.<br>"
                    + TextUtils.htmlEncode(String.valueOf(e.getDescription()))
                    + "<br><a href=\"" + TextUtils.htmlEncode(failed) + "\">Retry</a></body></html>";
            // The failed URL is the base and the history entry, so Retry is a plain navigation
            // and Back does not get stuck on the error page
            v.loadDataWithBaseURL(failed, html, "text/html", "utf-8", failed);
        }
    }

    private class ChromeClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
            // Photo upload (e.g. Marketplace): the system picker, needs no permission
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = cb;
            try {
                startActivityForResult(p.createIntent(), FILE_CHOOSER_REQUEST);
            } catch (ActivityNotFoundException ex) {
                fileCallback = null;
                return false;
            }
            return true;
        }

        @Override
        public void onPermissionRequest(PermissionRequest r) {
            r.deny(); // camera, microphone: no
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin,
                GeolocationPermissions.Callback cb) {
            cb.invoke(origin, false, false);
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (request == FILE_CHOOSER_REQUEST && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileCallback = null;
        } else {
            super.onActivityResult(request, result, data);
        }
    }

    private static boolean isHttp(String scheme) {
        return "https".equals(scheme) || "http".equals(scheme);
    }

    private static boolean inDomains(String host, String[] domains) {
        if (host == null) return false;
        for (String d : domains) {
            if (host.equals(d) || host.endsWith("." + d)) return true;
        }
        return false;
    }

    /** From google.com only reCAPTCHA is allowed. */
    private static boolean isRecaptcha(String host, String path) {
        return ("www.google.com".equals(host) || "google.com".equals(host))
                && path.startsWith("/recaptcha");
    }

    private static WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "utf-8", 204, "No Content",
                Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }

    /** Opens an external link in the browser, without Facebook's tracking parameters. */
    private void openExternally(Uri u) {
        Uri.Builder b = u.buildUpon().clearQuery();
        for (String name : u.getQueryParameterNames()) {
            if (name.equals("fbclid") || name.startsWith("__cft__") || name.startsWith("__tn__")) continue;
            for (String value : u.getQueryParameters(name)) b.appendQueryParameter(name, value);
        }
        Intent i = new Intent(Intent.ACTION_VIEW, b.build());
        i.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException ignored) {
        }
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
        web.pauseTimers(); // nothing runs in the background
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.resumeTimers();
        web.onResume();
        // Back from Settings: apply immediately, without reloading the page
        int v = AppSettings.prefs(this).getInt(AppSettings.VERSION, 0);
        if (v != settingsVersion) {
            settingsVersion = v;
            float zoom = AppSettings.textZoom(this) / 100f;
            if (zoomFrame.zoom() != zoom) {
                // Facebook's server lays out the page for the window size in its "wd" cookie,
                // so a new zoom needs the cookie and a reload
                zoomFrame.setZoom(zoom);
                setWindowSizeCookie();
                web.reload();
            } else {
                runCleaner(web); // also applies the autoplay setting (see cleaner.js)
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        web.saveState(state);
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
