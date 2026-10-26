package com.firehome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.PixelCopy;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tvOS-style home screen with only the apps you installed on it. HomeWatcher brings it up whenever Amazon's home
 * is started. One row of apps along the bottom of a sky wallpaper; the focused app's name and a line about it sit
 * above the row, and glass buttons below it: Appstore and Settings on the left, Fire TV home at the far right.
 * PINNED come first in this order; any other app you install later is added after them.
 */
public class HomeActivity extends Activity {
    static final String AMAZON_HOME = "com.amazon.tv.launcher";
    private static final List<String> PINNED = Arrays.asList(
            "com.amazon.firetv.youtube", "net.mbc.shahidTV", "com.watchit.vod", "org.clipbox.player",
            "com.sneig.livedrama", "org.videolan.vlc", "com.retroarch", "com.esaba.downloader");
    // every tile is the app's logo on a gradient in its colours, tvOS-style: (top colour, bottom colour, glow behind
    // the logo, mode). LOGO draws an adaptive icon's logo layer alone. WHITE is for artwork on its own white square:
    // a white tile, with the icon multiplied onto it so its white square takes the tile's gradient and disappears.
    // MASK is for a flat icon with a white logo on a coloured square: only its white pixels are kept, as a white
    // logo. ICON shows the whole icon as a rounded app icon (anything else). Whatever the artwork, logos
    // are measured and scaled to look the same size: LOGO_DP, the size of ClipBox's cube when this was set.
    private static final Map<String, int[]> LOGO_TILES = new HashMap<>();
    private static final int LOGO = 0;
    private static final int ICON = 1;
    private static final int WHITE = 2;
    private static final int MASK = 3;
    private static final int LOGO_DP = 62;
    private static final int[] LOGO_DEFAULT = {0xFF2A3448, 0xFF0E1320, 0x40FFFFFF, ICON};
    static {
        LOGO_TILES.put("com.amazon.firetv.youtube", new int[] {0xFFFFFFFF, 0xFFE2E6EE, 0x26FF2A2A, WHITE});
        LOGO_TILES.put("net.mbc.shahidTV", new int[] {0xFF0B3B33, 0xFF04140F, 0x7300D68F, MASK});
        LOGO_TILES.put("com.watchit.vod", new int[] {0xFF2E250A, 0xFF0D0A03, 0x73F0B429, LOGO});
        LOGO_TILES.put("org.clipbox.player", new int[] {0xFFFFFFFF, 0xFFE2E6EE, 0x26E0242F, WHITE});
        LOGO_TILES.put("com.sneig.livedrama", new int[] {0xFF1F2E78, 0xFF0A1033, 0x735B8CFF, LOGO});
        LOGO_TILES.put("org.videolan.vlc", new int[] {0xFF3A2410, 0xFF150C05, 0x73FF8A1F, LOGO});
        LOGO_TILES.put("com.retroarch", new int[] {0xFF33245C, 0xFF110A24, 0x738A5CFF, LOGO});
        LOGO_TILES.put("com.esaba.downloader", new int[] {0xFF3A1E0A, 0xFF140A03, 0x73FF7A1A, LOGO});
    }
    // optical correction on top of the measured size: a solid, high-contrast shape looks bigger than an outlined
    // one of the same size (percent of LOGO_DP; apps not listed get 100)
    private static final Map<String, Integer> LOGO_WEIGHT = new HashMap<>();
    static {
        LOGO_WEIGHT.put("com.esaba.downloader", 82);
        LOGO_WEIGHT.put("net.mbc.shahidTV", 85);
    }
    // where each app's logo sits inside its drawable (left, top, right, bottom as fractions), measured once
    private static final Map<String, float[]> LOGO_BOUNDS = new HashMap<>();
    // Amazon's apps are system apps and stay off the row; the Appstore gets a button instead, opening the store
    // itself (its launcher entry opens "Your Apps & Channels")
    private static final String APPSTORE = "com.amazon.venezia";
    private static final String APPSTORE_FRONT = "com.amazon.venezia.ade.ADEHomeActivity";
    // caption and line shown above the row for the focused app; others get a plain fallback
    private static final Map<String, String[]> ABOUT = new HashMap<>();
    static {
        ABOUT.put("com.amazon.firetv.youtube", new String[] {"WATCH NOW", "Videos, music and live streams."});
        ABOUT.put("net.mbc.shahidTV", new String[] {"WATCH NOW", "Arabic series, films and live TV."});
        ABOUT.put("com.watchit.vod", new String[] {"WATCH NOW", "Egyptian series and films."});
        ABOUT.put("org.videolan.vlc", new String[] {"PLAY", "Play files from your network and drives."});
        ABOUT.put("org.clipbox.player", new String[] {"WATCH NOW", "Films and series."});
        ABOUT.put("com.sneig.livedrama", new String[] {"LIVE", "Live channels and drama."});
        ABOUT.put("com.retroarch", new String[] {"PLAY", "Classic games."});
        ABOUT.put("com.esaba.downloader", new String[] {"TOOLS", "Download files and apps."});
    }
    // "Fire TV home" starts Amazon's home the way a Home press does, so the watcher stands down this long
    private static final long AMAZON_PAUSE_MS = 10 * 60 * 1000L;
    // Fire OS opens Settings by starting Amazon's home with a "show Settings" extra; long enough for that to land
    private static final long SETTINGS_PAUSE_MS = 4000L;

    private float dp;
    private FrameLayout root;
    private Bitmap picture;
    private LinearLayout row;
    private HorizontalScrollView scroll;
    private TextView caption;
    private TextView title;
    private TextView about;
    private String lastOpened;
    private ImageView weatherIcon;
    private TextView weatherTemp;
    private TextView weatherLine;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable refreshWeather = this::refreshWeather;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        startService(new Intent(this, HomeWatcher.class));

        root = new FrameLayout(this);
        root.setBackground(new SkyBackground());

        LinearLayout corner = new LinearLayout(this);
        corner.setOrientation(LinearLayout.VERTICAL);
        // always 12-hour with AM/PM, whatever the TV's time setting: the same pattern for both formats
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("h:mm");
        style(time, 56, 0xFFFFFFFF, "sans-serif-medium");
        time.setShadowLayer(px(12), 0, px(2), 0x66000000);
        TextClock half = new TextClock(this);
        half.setFormat12Hour("a");
        half.setFormat24Hour("a");
        style(half, 24, 0xE6FFFFFF, "sans-serif-medium");
        half.setShadowLayer(px(10), 0, px(1), 0x66000000);
        half.setPadding(px(8), 0, 0, 0);
        LinearLayout clock = new LinearLayout(this);   // horizontal and baseline-aligned: "PM" sits on the line
        clock.addView(time);
        clock.addView(half);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, MMMM d");
        date.setFormat24Hour("EEEE, MMMM d");
        style(date, 20, 0xD9FFFFFF, "sans-serif-medium");
        date.setShadowLayer(px(8), 0, px(1), 0x66000000);
        corner.addView(clock);
        corner.addView(date);
        root.addView(corner, at(Gravity.TOP | Gravity.START, 48, 30));

        // the weather, top right, sized and shadowed like the clock; empty until there is something to show
        LinearLayout weather = new LinearLayout(this);
        weather.setOrientation(LinearLayout.VERTICAL);
        weather.setGravity(Gravity.END);
        LinearLayout now = new LinearLayout(this);
        now.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new ImageView(this);
        weatherIcon.setLayerType(View.LAYER_TYPE_SOFTWARE, null);   // the icon's soft shadow needs software drawing
        now.addView(weatherIcon, new LinearLayout.LayoutParams(px(58), px(58)));
        weatherTemp = new TextView(this);
        style(weatherTemp, 56, 0xFFFFFFFF, "sans-serif-medium");
        weatherTemp.setShadowLayer(px(12), 0, px(2), 0x66000000);
        weatherTemp.setPadding(px(10), 0, 0, 0);
        now.addView(weatherTemp);
        weatherLine = new TextView(this);
        style(weatherLine, 20, 0xD9FFFFFF, "sans-serif-medium");
        weatherLine.setShadowLayer(px(8), 0, px(1), 0x66000000);
        weather.addView(now);
        weather.addView(weatherLine);
        root.addView(weather, at(Gravity.TOP | Gravity.END, 48, 30));

        // everything else stacks up from the bottom edge: what's focused, the app row, the buttons
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setClipChildren(false);

        caption = new TextView(this);
        style(caption, 12, 0xB3FFFFFF, "sans-serif-medium");
        caption.setLetterSpacing(0.12f);
        title = new TextView(this);
        style(title, 40, 0xFFFFFFFF, "sans-serif");
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setShadowLayer(px(14), 0, px(2), 0x66000000);
        about = new TextView(this);
        style(about, 15, 0xD9FFFFFF, "sans-serif");
        about.setShadowLayer(px(8), 0, px(1), 0x66000000);
        for (TextView t : new TextView[] {caption, title, about}) {
            t.setSingleLine();
            t.setPadding(px(52), 0, px(52), 0);
            bottom.addView(t);
        }

        row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setClipToPadding(false);   // the focused tile grows past the row's padding
        row.setPadding(px(46), px(16), px(46), px(16));
        scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.addView(row);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollLp.setMargins(0, px(6), 0, 0);
        bottom.addView(scroll, scrollLp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setPadding(px(48), px(4), px(48), 0);
        buttons.addView(pill("Appstore", v -> openAppstore()));
        buttons.addView(pill("Settings", v -> openSettings()));
        // the way out to Amazon's home sits apart, at the far right
        buttons.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));
        buttons.addView(pill("Fire TV home", v -> openAmazonHome()));
        bottom.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        bottomLp.setMargins(0, 0, 0, px(34));
        root.addView(bottom, bottomLp);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        HomeWatcher.pause(0);   // back from Settings or Amazon's home: Home brings this screen up again
        View focus = fillRow();   // apps may have been installed or removed while we were away
        if (focus != null) {
            focus.requestFocus();
        }
        showWeather(Weather.saved(this));
        refreshWeather();
        // let this screen draw a frame first, so the swap from the overlay's picture can't be seen
        root.postDelayed(HomeWatcher::hideOverlay, 150);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(refreshWeather);
        // the overlay shows this picture the next time Amazon's home is started. PixelCopy takes the screen as
        // drawn; drawing the views into a bitmap would lose rounded clipping and shadows, and RGB_565 bands the sky
        View window = getWindow().getDecorView();
        if (window.getWidth() > 0 && window.getHeight() > 0) {
            if (picture == null || picture.getWidth() != window.getWidth() || picture.getHeight() != window.getHeight()) {
                picture = Bitmap.createBitmap(window.getWidth(), window.getHeight(), Bitmap.Config.ARGB_8888);
            }
            Bitmap shot = picture;
            PixelCopy.request(getWindow(), shot, result -> {
                if (result == PixelCopy.SUCCESS) {
                    HomeWatcher.snapshot(shot);
                }
            }, window.getHandler());
        }
    }

    /** Fetches new weather if what's shown is older than Weather.REFRESH_MS, and checks again later. */
    private void refreshWeather() {
        main.removeCallbacks(refreshWeather);
        main.postDelayed(refreshWeather, Weather.REFRESH_MS);
        Weather shown = Weather.saved(this);
        if (shown != null && System.currentTimeMillis() - shown.fetched < Weather.REFRESH_MS) {
            return;
        }
        new Thread(() -> {
            Weather fresh = Weather.fetch(getApplicationContext());
            if (fresh != null) {
                main.post(() -> showWeather(fresh));
            }
        }, "weather").start();
    }

    private void showWeather(Weather weather) {
        if (weather == null) {
            return;   // nothing yet: the corner stays empty rather than showing an error
        }
        weatherIcon.setImageDrawable(new WeatherIcon(weather.kind(), weather.day));
        weatherTemp.setText(Weather.degrees(weather.temperature));
        String condition = weather.condition();
        weatherLine.setText((condition.isEmpty() ? "" : condition + "  ·  ")
                + "H " + Weather.degrees(weather.high) + "  L " + Weather.degrees(weather.low));
    }

    @Override
    public void onBackPressed() {
        // this is Home: Back has nowhere to go
    }

    /** Rebuilds the row and returns the tile to focus: the app opened last, else the first one. */
    private View fillRow() {
        row.removeAllViews();
        Map<String, ResolveInfo> apps = installedApps();
        View first = null;
        View again = null;
        for (Map.Entry<String, ResolveInfo> app : apps.entrySet()) {
            View tile = tile(app.getKey(), app.getValue());
            row.addView(tile);
            if (first == null) {
                first = tile;
            }
            if (app.getKey().equals(lastOpened)) {
                again = tile;
            }
        }
        if (apps.isEmpty()) {
            show(null, "No apps yet", "Open the Appstore below to get some.");
        }
        return again != null ? again : first;
    }

    /** Apps with a TV (or, failing that, a phone) launcher entry that you installed: PINNED first, then by name. */
    private Map<String, ResolveInfo> installedApps() {
        PackageManager pm = getPackageManager();
        Map<String, ResolveInfo> found = new LinkedHashMap<>();
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                ApplicationInfo ai = ri.activityInfo.applicationInfo;
                boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (!system && !ai.packageName.equals(getPackageName()) && !found.containsKey(ai.packageName)) {
                    found.put(ai.packageName, ri);
                }
            }
        }
        Map<String, ResolveInfo> ordered = new LinkedHashMap<>();
        for (String pkg : PINNED) {
            if (found.containsKey(pkg)) {
                ordered.put(pkg, found.remove(pkg));
            }
        }
        List<Map.Entry<String, ResolveInfo>> rest = new ArrayList<>(found.entrySet());
        rest.sort((a, b) -> label(a.getValue()).compareToIgnoreCase(label(b.getValue())));
        for (Map.Entry<String, ResolveInfo> e : rest) {
            ordered.put(e.getKey(), e.getValue());
        }
        return ordered;
    }

    private String label(ResolveInfo ri) {
        return String.valueOf(ri.activityInfo.applicationInfo.loadLabel(getPackageManager()));
    }

    /** Puts the focused app's caption, name and line above the row, with a short fade. */
    private void show(String captionText, String titleText, String aboutText) {
        caption.setText(captionText == null ? "" : captionText);
        title.setText(titleText);
        about.setText(aboutText);
        for (TextView t : new TextView[] {caption, title, about}) {
            t.setAlpha(0.4f);
            t.animate().alpha(1f).setDuration(220).start();
        }
    }

    /** A frosted panel: a light top-to-bottom sheen and a thin bright edge; brighter when focused. */
    private GradientDrawable glass(int radiusDp, boolean focused) {
        GradientDrawable panel = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                focused ? new int[] {0x66FFFFFF, 0x33FFFFFF} : new int[] {0x33FFFFFF, 0x17FFFFFF});
        panel.setCornerRadius(px(radiusDp));
        panel.setStroke(Math.max(1, px(1)), focused ? 0xCCFFFFFF : 0x40FFFFFF);
        return panel;
    }

    private View tile(String pkg, ResolveInfo ri) {
        int[] look = LOGO_TILES.containsKey(pkg) ? LOGO_TILES.get(pkg) : LOGO_DEFAULT;
        // the focused tile glows in the app's colour: the same colour as the glow behind its logo
        GlowTile tile = new GlowTile(this, look[2], px(22), px(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(184), px(104));
        lp.setMargins(px(8), 0, px(8), 0);
        tile.setLayoutParams(lp);
        GradientDrawable idle = glass(14, false);
        GradientDrawable lit = glass(14, true);
        tile.setBackground(idle);
        tile.setPadding(px(3), px(3), px(3), px(3));
        tile.setElevation(px(4));

        tile.addView(logoTile(ri, look), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return finishTile(tile, pkg, ri, idle, lit);
    }

    /** The app's logo, large, on a gradient in its colours (see LOGO_TILES). */
    private View logoTile(ResolveInfo ri, int[] colours) {
        FrameLayout art = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {colours[0], colours[1]});
        bg.setCornerRadius(px(11));
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setColors(new int[] {colours[2], colours[2] & 0x00FFFFFF});
        glow.setGradientRadius(px(84));
        art.setBackground(new LayerDrawable(new Drawable[] {bg, glow}));
        art.setClipToOutline(true);
        Drawable icon = sharpIcon(ri.activityInfo.applicationInfo);
        AdaptiveIconDrawable adaptive = icon instanceof AdaptiveIconDrawable ? (AdaptiveIconDrawable) icon : null;
        int mode = colours[3];
        if (mode == LOGO && (adaptive == null || adaptive.getForeground() == null)) {
            mode = ICON;   // no logo layer to lift out: show the icon whole
        }
        ImageView logo = new ImageView(this);
        if (mode == ICON) {
            // a rounded app icon: the layers' visible middle (72 of 108 dp) or the flat icon, clipped to a squircle
            int size = px(LOGO_DP);
            FrameLayout badge = new FrameLayout(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(0xFFFFFFFF);
            shape.setCornerRadius(size * 0.23f);
            badge.setBackground(shape);
            badge.setClipToOutline(true);
            badge.setElevation(px(6));
            logo.setScaleType(ImageView.ScaleType.FIT_XY);
            logo.setImageDrawable(adaptive != null ? layers(adaptive) : icon);
            int canvas = adaptive != null ? Math.round(size * 108f / 72f) : size;
            badge.addView(logo, new FrameLayout.LayoutParams(canvas, canvas, Gravity.CENTER));
            art.addView(badge, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
            return art;
        }

        Drawable picture = mode == WHITE ? (adaptive != null ? layers(adaptive) : icon)
                : mode == MASK ? whiteLogo(adaptive != null ? layers(adaptive) : icon)
                : adaptive.getForeground();
        String key = ri.activityInfo.packageName;
        if (!LOGO_BOUNDS.containsKey(key)) {
            LOGO_BOUNDS.put(key, logoBounds(picture, mode == WHITE));
        }
        float[] box = LOGO_BOUNDS.get(key);
        float w = box[2] - box[0];
        float h = box[3] - box[1];
        // the same area as a LOGO_DP square, so wide logos aren't shrunk to a sliver; never wider than 1.35 of it
        float target = px(LOGO_DP) * (LOGO_WEIGHT.containsKey(key) ? LOGO_WEIGHT.get(key) : 100) / 100f;
        float side = Math.min(target / (float) Math.sqrt(w * h), 1.35f * target / Math.max(w, h));
        logo.setScaleType(ImageView.ScaleType.FIT_XY);
        logo.setImageDrawable(picture);
        logo.setTranslationX((0.5f - (box[0] + box[2]) / 2) * side);
        logo.setTranslationY((0.5f - (box[1] + box[3]) / 2) * side);
        if (mode == WHITE) {
            Paint multiply = new Paint();
            multiply.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
            // some "white" squares are a few percent grey, which would show as a seam: lift them to pure white
            ColorMatrix lift = new ColorMatrix();
            lift.setScale(1.07f, 1.07f, 1.07f, 1f);
            multiply.setColorFilter(new ColorMatrixColorFilter(lift));
            logo.setLayerType(View.LAYER_TYPE_HARDWARE, multiply);
        }
        art.addView(logo, new FrameLayout.LayoutParams(Math.round(side), Math.round(side), Gravity.CENTER));
        return art;
    }

    /** The app's icon at the highest density it ships (Android would pick the TV's, often a small one). */
    private Drawable sharpIcon(ApplicationInfo ai) {
        try {
            Drawable icon = getPackageManager().getResourcesForApplication(ai)
                    .getDrawableForDensity(ai.icon, DisplayMetrics.DENSITY_XXXHIGH, null);
            if (icon != null) {
                return icon;
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            // fall back to the usual icon
        }
        return ai.loadIcon(getPackageManager());
    }

    /** Only the white parts of an icon, as a white logo on transparent: for MASK tiles. */
    private Drawable whiteLogo(Drawable icon) {
        int n = 384;
        Bitmap bitmap = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Rect was = new Rect(icon.getBounds());
        icon.setBounds(0, 0, n, n);
        icon.draw(new Canvas(bitmap));
        icon.setBounds(was);
        int[] pixels = new int[n * n];
        bitmap.getPixels(pixels, 0, n, 0, 0, n, n);
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i];
            int least = Math.min(Math.min((c >> 16) & 0xFF, (c >> 8) & 0xFF), c & 0xFF);
            // white has all three channels high; the coloured square has at least one low. Ramp between them so
            // the logo's edges stay smooth
            int alpha = Math.max(0, Math.min(255, (least - 150) * 255 / 70)) * (c >>> 24) / 255;
            pixels[i] = (alpha << 24) | 0xFFFFFF;
        }
        bitmap.setPixels(pixels, 0, n, 0, 0, n, n);
        return new BitmapDrawable(getResources(), bitmap);
    }

    private static Drawable layers(AdaptiveIconDrawable icon) {
        Drawable back = icon.getBackground();
        Drawable front = icon.getForeground();
        return back == null ? front : front == null ? back : new LayerDrawable(new Drawable[] {back, front});
    }

    /**
     * The box around a drawable's visible pixels, as fractions of its square. With ignoreWhite, near-white pixels
     * count as empty too: for logos on their own white square, which the WHITE tile makes disappear.
     */
    private static float[] logoBounds(Drawable picture, boolean ignoreWhite) {
        int n = 192;
        Bitmap bitmap = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Rect was = new Rect(picture.getBounds());
        picture.setBounds(0, 0, n, n);
        picture.draw(new Canvas(bitmap));
        picture.setBounds(was);
        int[] pixels = new int[n * n];
        bitmap.getPixels(pixels, 0, n, 0, 0, n, n);
        bitmap.recycle();
        int left = n, top = n, right = -1, bottom = -1;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int c = pixels[y * n + x];
                boolean seen = (c >>> 24) > 40;
                if (seen && ignoreWhite) {
                    seen = Math.min(Math.min((c >> 16) & 0xFF, (c >> 8) & 0xFF), c & 0xFF) < 225;
                }
                if (seen) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        if (right < 0) {
            return new float[] {0f, 0f, 1f, 1f};
        }
        return new float[] {left / (float) n, top / (float) n, (right + 1) / (float) n, (bottom + 1) / (float) n};
    }

    private View finishTile(GlowTile tile, String pkg, ResolveInfo ri, GradientDrawable idle, GradientDrawable lit) {
        String name = label(ri);
        String[] info = ABOUT.containsKey(pkg) ? ABOUT.get(pkg) : new String[] {"OPEN", "Press select to open."};
        tile.setFocusable(true);
        tile.setOnClickListener(v -> open(pkg));
        tile.setOnFocusChangeListener((v, focused) -> {
            tile.setBackground(focused ? lit : idle);
            tile.setGlowing(focused);
            float scale = focused ? 1.14f : 1f;
            v.animate().scaleX(scale).scaleY(scale).translationZ(focused ? px(12) : 0).setDuration(180).start();
            if (focused) {
                show(info[0], name, info[1]);
                v.post(() -> keepVisible(v));
            }
        });
        return tile;
    }

    /** Scrolls the row so the focused tile, grown by its focus scale, keeps a margin from both screen edges. */
    private void keepVisible(View tile) {
        int margin = px(46);
        int x = scroll.getScrollX();
        if (tile.getRight() + margin > x + scroll.getWidth()) {
            scroll.smoothScrollTo(tile.getRight() + margin - scroll.getWidth(), 0);
        } else if (tile.getLeft() - margin < x) {
            scroll.smoothScrollTo(Math.max(0, tile.getLeft() - margin), 0);
        }
    }

    private TextView pill(String label, View.OnClickListener click) {
        TextView pill = new TextView(this);
        style(pill, 13, 0xE6FFFFFF, "sans-serif-medium");
        pill.setText(label);
        pill.setPadding(px(16), px(7), px(16), px(7));
        GradientDrawable idle = glass(18, false);
        GradientDrawable lit = new GradientDrawable();
        lit.setCornerRadius(px(18));
        lit.setColor(0xF2FFFFFF);
        pill.setBackground(idle);
        pill.setFocusable(true);
        pill.setOnClickListener(click);
        pill.setOnFocusChangeListener((v, focused) -> {
            pill.setBackground(focused ? lit : idle);
            pill.setTextColor(focused ? 0xFF0B1424 : 0xE6FFFFFF);
            v.animate().scaleX(focused ? 1.08f : 1f).scaleY(focused ? 1.08f : 1f).setDuration(150).start();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(px(4), 0, px(4), 0);
        pill.setLayoutParams(lp);
        return pill;
    }

    private void open(String pkg) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage(pkg);
        }
        lastOpened = pkg;
        launch(intent, "Couldn't open that app.");
    }

    private void openAppstore() {
        Intent front = new Intent(Intent.ACTION_MAIN).setClassName(APPSTORE, APPSTORE_FRONT);
        if (getPackageManager().resolveActivity(front, 0) == null) {
            front = getPackageManager().getLaunchIntentForPackage(APPSTORE);
        }
        launch(front, "Couldn't open the Appstore.");
    }

    private void openSettings() {
        // Fire OS answers ACTION_SETTINGS by starting Amazon's home on its Settings row, which the watcher would
        // cover straight away
        HomeWatcher.pause(SETTINGS_PAUSE_MS);
        launch(new Intent(Settings.ACTION_SETTINGS), "Couldn't open Settings.");
    }

    private void openAmazonHome() {
        HomeWatcher.pause(AMAZON_PAUSE_MS);
        Toast.makeText(this, "Amazon's home for the next 10 minutes", Toast.LENGTH_LONG).show();
        launch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(AMAZON_HOME),
                "Couldn't open Amazon's home.");
    }

    private void launch(Intent intent, String failure) {
        try {
            if (intent == null) {
                throw new ActivityNotFoundException();
            }
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            HomeWatcher.pause(0);
            Toast.makeText(this, failure, Toast.LENGTH_LONG).show();
        }
    }

    private void style(TextView view, float sp, int color, String family) {
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(family, Typeface.NORMAL));
    }

    private FrameLayout.LayoutParams at(int gravity, int horizontalDp, int verticalDp) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
        lp.setMargins(px(horizontalDp), px(verticalDp), px(horizontalDp), px(verticalDp));
        return lp;
    }

    private int px(int dpValue) {
        return Math.round(dpValue * dp);
    }
}
