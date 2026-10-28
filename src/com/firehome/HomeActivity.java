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
import android.graphics.drawable.TransitionDrawable;
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
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tvOS-style home screen with only the apps you installed on it. HomeWatcher brings it up whenever Amazon's home
 * is started. One row of apps along the bottom of a sky wallpaper; the focused app's name and a line about it sit
 * above the row, and glass buttons centred below it: the Home Assistant rooms, a thin line, Fire TV, Appstore, Settings.
 * PINNED come first in this order; any other app you install later is added after them.
 */
public class HomeActivity extends Activity {
    static final String AMAZON_HOME = "com.amazon.tv.launcher";
    private static final String OOBE_RESOURCES = "com.amazon.tv.ooberesource";   // holds the Fire TV logo
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

    // tvOS gives the focused tile a soft neutral shadow; true brings back a glow in each app's colour
    private static final boolean COLOURED_GLOW = false;
    // the indoor climate corner: outside these, the value is shown in AMBER
    private static final double HUMIDITY_LOW = 40;
    private static final double HUMIDITY_HIGH = 60;
    private static final double CO2_HIGH = 1000;
    private static final int AMBER = 0xFFFFB13B;
    // the clock sits top centre between the climate and the departures
    private static final int CLOCK_SP = 46;
    private static final int DAY_TINT = 0xFF9EDDE2;   // the "27th" over the month: a pale cyan, a shade off the white
    private static final float CAP_HEIGHT = 0.71f;   // Roboto's capital height, as a share of the text size
    private static final float UNIT_SIZE = 0.7f;
    private static final int COLDER = 0xFF8CCBFF;
    private static final int WARMER = 0xFFFF9A7A;
    private static final int CLIMATE_SP = 37;   // the indoor temperature; the rest of the corner scales with it
    private static final int ICON_BUTTON_DP = 33;   // the Appstore, Settings and Fire TV buttons: as big as a room's
    private static final long OUTSIDE_MAX_AGE_MS = 60 * 60 * 1000L;

    private float dp;
    private int lastFocusedTile = -1;
    private HomeBar bar;
    private SkyBackground.Phase phase;
    private FrameLayout root;
    private Bitmap picture;
    private LinearLayout row;
    private HorizontalScrollView scroll;
    private TextView caption;
    private TextView title;
    private TextView about;
    private String lastOpened;
    private String rowApps;   // the APKs the row was built from
    // Android only trims a window's GPU cache when the app leaves the screen, which a home screen hardly ever does, so
    // the textures and offscreen buffers of every animation pile up (~35 MB). Let them go once things have settled
    private static final long TRIM_DELAY_MS = 4000L;
    private final Runnable trimGpu = HomeActivity::trimGpuCache;
    private View climate;
    private TextView salat;
    private Departures bus;
    private Departures metro;
    private TextView climateTemp;
    private TextView outsideDiff;
    private double indoor = Double.NaN;
    private TextView climateLine;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable refreshWeather = this::refreshWeather;
    private final Runnable followSun = this::followSun;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        startService(new Intent(this, HomeWatcher.class));

        root = new FrameLayout(this);
        // the wallpaper follows the sun here (Sun), checked once a minute
        phase = sunPhase();
        root.setBackground(new SkyBackground(phase));

        // "27th" small above "Oct", then "5:53 PM": always 12-hour with AM/PM, whatever the TV's time setting (the same pattern for
        // both formats); horizontal and baseline-aligned, so "Oct" and "PM" sit on the time's line
        LinearLayout corner = new LinearLayout(this);
        TextView day = new TextView(this);
        style(day, CLOCK_SP * 2 / 7, DAY_TINT, "sans-serif-medium");
        day.setShadowLayer(px(8), 0, px(1), 0x66000000);
        TextView month = new TextView(this);
        style(month, CLOCK_SP * 4 / 7, 0xE6FFFFFF, "sans-serif-medium");
        month.setShadowLayer(px(10), 0, px(1), 0x66000000);
        month.setIncludeFontPadding(false);
        day.setIncludeFontPadding(false);
        LinearLayout date = new LinearLayout(this);
        date.setOrientation(LinearLayout.VERTICAL);
        // both wrap_content (a vertical LinearLayout's default is match_parent); the day sits over the month's end
        LinearLayout.LayoutParams dayLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dayLp.gravity = Gravity.END;
        date.addView(day, dayLp);
        day.setTranslationY(3.5f * dp);   // drawn a touch lower, so its top lines up with the top of the time's digits
        date.addView(month, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        date.setBaselineAlignedChildIndex(1);   // the month is what lines up with the time
        date.setPadding(0, 0, px(14), 0);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("h:mm");
        style(time, CLOCK_SP, 0xFFFFFFFF, "sans-serif-medium");
        time.setShadowLayer(px(12), 0, px(2), 0x66000000);
        // TextClock can't write "27th", so the date follows the time: it changes whenever the minute does
        time.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable text) {
                showDate(day, month);
                if (bus != null) {
                    bus.tick();   // the departures count down with the clock
                    metro.tick();
                }
            }
        });
        showDate(day, month);
        TextClock half = new TextClock(this);
        half.setFormat12Hour("a");
        half.setFormat24Hour("a");
        style(half, CLOCK_SP * 3 / 7, 0xE6FFFFFF, "sans-serif-medium");
        half.setShadowLayer(px(10), 0, px(1), 0x66000000);
        half.setPadding(px(8), 0, 0, 0);
        corner.addView(date);
        corner.addView(time);
        corner.addView(half);
        // under the clock, centred: the next prayer from Home Assistant (Mawaqit), "Fajr 4:59 AM"; empty until known
        salat = new TextView(this);
        style(salat, 17, 0xD9FFFFFF, "sans-serif-medium");
        salat.setShadowLayer(px(8), 0, px(1), 0x66000000);
        LinearLayout clockColumn = new LinearLayout(this);
        clockColumn.setOrientation(LinearLayout.VERTICAL);
        clockColumn.setGravity(Gravity.CENTER_HORIZONTAL);
        clockColumn.addView(corner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        clockColumn.addView(salat, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(clockColumn, at(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 30));

        // the indoor climate from Home Assistant, top left, a little smaller than the clock; empty until there is
        // something to show
        LinearLayout climate = new LinearLayout(this);
        this.climate = climate;
        climate.setOrientation(LinearLayout.VERTICAL);
        climate.setGravity(Gravity.START);
        LinearLayout now = new LinearLayout(this);
        now.setGravity(Gravity.CENTER_VERTICAL);
        ImageView house = new ImageView(this);
        BarIcon houseIcon = new BarIcon(BarIcon.Kind.HOME);
        houseIcon.set(false, 0f, 0);
        house.setImageDrawable(houseIcon);
        now.addView(house, new LinearLayout.LayoutParams(px(29), px(29)));
        climateTemp = new TextView(this);
        style(climateTemp, CLIMATE_SP, 0xFFFFFFFF, "sans-serif-medium");
        climateTemp.setShadowLayer(px(10), 0, px(2), 0x66000000);
        climateTemp.setPadding(px(10), 0, 0, 0);
        LinearLayout reading = new LinearLayout(this);   // horizontal and baseline-aligned, like the clock's "PM"
        reading.addView(climateTemp);
        now.addView(reading);
        // how the home compares with outside: "↑13°" (coral) means 13° warmer at home than outside, "↓" (blue) cooler
        outsideDiff = new TextView(this);
        style(outsideDiff, 20, 0xD9FFFFFF, "sans-serif-medium");
        outsideDiff.setShadowLayer(px(8), 0, px(1), 0x66000000);
        outsideDiff.setPadding(px(12), 0, 0, 0);
        reading.addView(outsideDiff);
        climateLine = new TextView(this);
        style(climateLine, 14, 0xD9FFFFFF, "sans-serif-medium");
        climateLine.setShadowLayer(px(8), 0, px(1), 0x66000000);
        climate.addView(now);
        climate.addView(climateLine);
        climate.setVisibility(View.INVISIBLE);
        root.addView(climate, at(Gravity.TOP | Gravity.START, 48, 30));

        // the next departures, top right: bus 147 over the U8, right-aligned, each a line icon beside its directions
        LinearLayout transport = new LinearLayout(this);
        transport.setOrientation(LinearLayout.VERTICAL);
        transport.setGravity(Gravity.END);
        bus = new Departures(this, dp, BarIcon.Kind.BUS, "147", 11, 26);
        metro = new Departures(this, dp, BarIcon.Kind.METRO, "U8", 11, 26);
        // both as wide as the wider one, so their icons line up
        transport.addView(bus.view(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams metroLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        metroLp.setMargins(0, px(10), 0, 0);
        transport.addView(metro.view(), metroLp);
        root.addView(transport, at(Gravity.TOP | Gravity.END, 48, 30));

        // everything else stacks up from the bottom edge: what's focused, the app row, the buttons
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setClipChildren(false);

        // the caption ("WATCH NOW") stands upright beside the name and the line about it, reading bottom to top
        caption = new VerticalText(this);
        style(caption, 12, 0xB3FFFFFF, "sans-serif-medium");
        caption.setLetterSpacing(0.12f);
        title = new TextView(this);
        style(title, 40, 0xFFFFFFFF, "sans-serif");
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setShadowLayer(px(14), 0, px(2), 0x66000000);
        about = new TextView(this);
        style(about, 15, 0xD9FFFFFF, "sans-serif");
        about.setShadowLayer(px(8), 0, px(1), 0x66000000);
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.TOP);   // the caption is placed by its top margin, set below
        heading.setPadding(px(52), 0, px(52), 0);
        LinearLayout named = new LinearLayout(this);
        named.setOrientation(LinearLayout.VERTICAL);
        for (TextView t : new TextView[] {caption, title, about}) {
            t.setSingleLine();
        }
        LinearLayout.LayoutParams captionLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        captionLp.setMargins(0, 0, px(14), 0);   // a margin, not padding: padding would turn with the text
        heading.addView(caption, captionLp);
        named.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        named.addView(about, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        heading.addView(named);
        // the caption runs from the top of the name's capitals to the baseline of the line about it, and starts there
        named.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int capTop = title.getTop() + title.getBaseline() - Math.round(CAP_HEIGHT * title.getTextSize());
            int baseline = about.getTop() + about.getBaseline();
            ((VerticalText) caption).fitTo(baseline - capTop);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) caption.getLayoutParams();
            if (lp.topMargin != capTop) {
                lp.topMargin = capTop;
                caption.setLayoutParams(lp);
            }
        });
        bottom.addView(heading);

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

        // the Home Assistant bar (if configured), then Fire TV (the way out to Amazon's home), Appstore and Settings,
        // all centred
        FrameLayout buttons = new FrameLayout(this);
        buttons.setClipChildren(false);
        buttons.setClipToPadding(false);   // a focused bar button grows past the row's edge
        buttons.setPadding(px(48), px(4), px(48), 0);
        LinearLayout system = new LinearLayout(this);
        system.addView(iconButton(fireTvLogo(), 3, "Fire TV", "AMAZON", "Amazon's home screen, for 10 minutes.",
                v -> confirmAmazonHome()));
        system.addView(iconButton(appstoreLogo(), 3, "Appstore", "STORE", "Find and install apps.", v -> openAppstore()));
        system.addView(iconButton(BarIcon.Kind.SETTINGS, "Settings", "SYSTEM", "Fire TV settings, network and apps.",
                v -> openSettings()));
        bar = HomeBar.load(this, dp, this::show);
        if (bar != null) {
            bar.onExtra((name, payload) -> {
                if (name.equals("climate")) {
                    showClimate(payload, true);
                } else if (name.equals("bus")) {
                    bus.update(payload);
                } else if (name.equals("metro")) {
                    metro.update(payload);
                } else if (name.equals("salat")) {
                    showSalat(payload);
                }
            });
            showClimate(getSharedPreferences("climate", MODE_PRIVATE).getString("json", null), false);
            bar.start();   // one connection for the app's lifetime: pausing on every Home press would race it
        }
        // both groups together in the middle, the rooms first, with one thin line between them
        LinearLayout group = new LinearLayout(this);
        View rooms = bar != null ? bar.view() : null;   // builds the room buttons: call it once
        group.setGravity(Gravity.CENTER_VERTICAL);
        group.setClipChildren(false);
        if (bar != null) {
            group.addView(rooms);
            View line = new View(this);
            line.setBackgroundColor(0x40FFFFFF);
            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(Math.max(1, px(1)), px(22));
            lineLp.setMargins(px(14), 0, px(14), 0);
            group.addView(line, lineLp);
        }
        group.addView(system);
        buttons.addView(group, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        bottom.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        bottomLp.setMargins(0, 0, 0, px(34));
        if (bar != null) {
            // a room's controls open in a glass panel just above the bottom row, over the app row's edge, centred
            // over the room buttons rather than over the whole group
            FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            panelLp.setMargins(0, 0, 0, px(88));
            View panel = bar.panel();
            root.addView(panel, panelLp);
            rooms.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                int[] at = new int[2];
                v.getLocationInWindow(at);
                panel.setTranslationX(at[0] + v.getWidth() / 2f - root.getWidth() / 2f);
            });
            bar.dimWhileOpen(scroll);
        }
        root.setClipChildren(false);
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
        followSun();
        showOutside();
        refreshWeather();
        // let this screen draw a frame first, so the swap from the overlay's picture can't be seen
        root.postDelayed(HomeWatcher::hideOverlay, 150);
        main.postDelayed(trimGpu, TRIM_DELAY_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(refreshWeather);
        main.removeCallbacks(followSun);
        main.removeCallbacks(trimGpu);
        // the overlay shows this picture the next time Amazon's home is started. PixelCopy takes the screen as
        // drawn; drawing the views into a bitmap would lose rounded clipping and shadows, and RGB_565 bands the sky
        View window = getWindow().getDecorView();
        if (window.getWidth() > 0 && window.getHeight() > 0) {
            if (picture == null || picture.getWidth() != window.getWidth() * 2 / 3) {
                picture = Bitmap.createBitmap(window.getWidth() * 2 / 3, window.getHeight() * 2 / 3,
                        Bitmap.Config.ARGB_8888);
            }
            Bitmap shot = picture;
            PixelCopy.request(getWindow(), shot, result -> {
                if (result == PixelCopy.SUCCESS) {
                    HomeWatcher.snapshot(shot);
                }
            }, window.getHandler());
        }
    }

    /** Frees the renderer's cached GPU memory; what is still on screen is simply uploaded again on the next frame. */
    private static void trimGpuCache() {
        try {
            Class.forName("android.view.ThreadedRenderer").getMethod("trimMemory", int.class)
                    .invoke(null, android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);   // COMPLETE drops the EGL context under a live window and crashes
        } catch (ReflectiveOperationException | RuntimeException e) {
            // a hidden Android method: without it the cache just stays until Android trims it
        }
    }

    /** Fetches new sunrise/sunset times if the saved ones are older than Weather.REFRESH_MS, and checks again later. */
    private void refreshWeather() {
        main.removeCallbacks(refreshWeather);
        main.removeCallbacks(followSun);
        main.postDelayed(refreshWeather, Weather.REFRESH_MS);
        Weather shown = Weather.saved(this);
        if (shown != null && System.currentTimeMillis() - shown.fetched < Weather.REFRESH_MS) {
            return;
        }
        new Thread(() -> {
            if (Weather.fetch(getApplicationContext()) != null) {
                main.post(this::showOutside);
            }
        }, "weather").start();
    }

    /** The indoor-minus-outdoor difference, beside the indoor temperature; hidden while either is missing or the
     *  weather is over an hour old. */
    private void showOutside() {
        Weather w = Weather.saved(this);
        boolean show = w != null && !Double.isNaN(indoor) && System.currentTimeMillis() - w.fetched < OUTSIDE_MAX_AGE_MS;
        outsideDiff.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            return;
        }
        long diff = Math.round(indoor - w.temperature);
        outsideDiff.setText(diff == 0 ? "=" : (diff < 0 ? "↓" : "↑") + Math.abs(diff) + "°");
        outsideDiff.setTextColor(diff < 0 ? COLDER : diff > 0 ? WARMER : 0xD9FFFFFF);
    }

    /**
     * Shows Home Assistant's {"temperature": 22.7, "humidity": 44.7, "co2": 632} (living room); any value can be
     * missing or "unavailable". The last one is saved, so the corner is filled straight after a restart.
     */
    private void showClimate(String payload, boolean save) {
        if (payload == null) {
            return;
        }
        double temperature;
        double humidity;
        double co2;
        try {
            org.json.JSONObject json = new org.json.JSONObject(payload);
            temperature = json.optDouble("temperature");
            humidity = json.optDouble("humidity");
            co2 = json.optDouble("co2");
        } catch (org.json.JSONException e) {
            return;
        }
        if (Double.isNaN(temperature)) {
            return;   // the corner keeps what it showed rather than an error
        }
        climateTemp.setText(String.format(java.util.Locale.ROOT, "%.1f°", temperature));
        indoor = temperature;
        showOutside();
        // a value outside the comfortable range turns amber: humidity outside 40-60 %, CO₂ above 1000 ppm
        android.text.SpannableStringBuilder line = new android.text.SpannableStringBuilder();
        if (!Double.isNaN(humidity)) {
            append(line, "Humidity " + Math.round(humidity), "%", humidity < HUMIDITY_LOW || humidity > HUMIDITY_HIGH);
        }
        if (!Double.isNaN(co2)) {
            if (line.length() > 0) {
                line.append("  ·  ");
            }
            append(line, "CO₂ " + Math.round(co2), " ppm", co2 > CO2_HIGH);
        }
        climateLine.setText(line);
        climate.setVisibility(View.VISIBLE);
        if (save) {
            getSharedPreferences("climate", MODE_PRIVATE).edit().putString("json", payload).apply();
        }
    }

    /** Appends "label value" and its unit, the unit smaller; all of it in AMBER when the value is high. */
    private static void append(android.text.SpannableStringBuilder line, String text, String unit, boolean high) {
        int start = line.length();
        line.append(text);
        int unitStart = line.length();
        line.append(unit);
        line.setSpan(new android.text.style.RelativeSizeSpan(UNIT_SIZE), unitStart, line.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (high) {
            line.setSpan(new android.text.style.ForegroundColorSpan(AMBER), start, line.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /**
     * Home Assistant's {"name": "fajr", "ts": epoch seconds} as "Fajr 4:59 AM", the name in the day's pale cyan;
     * anything unreadable leaves the line as it was.
     */
    private void showSalat(String payload) {
        String name;
        long ts;
        try {
            org.json.JSONObject json = new org.json.JSONObject(payload);
            name = json.getString("name");
            ts = json.getLong("ts");
        } catch (org.json.JSONException e) {
            return;
        }
        if (name.isEmpty() || ts <= 0) {
            salat.setText("");
            return;
        }
        String time = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH).format(new java.util.Date(ts * 1000));
        android.text.SpannableStringBuilder line = new android.text.SpannableStringBuilder();
        line.append(Character.toUpperCase(name.charAt(0)) + name.substring(1));
        line.setSpan(new android.text.style.ForegroundColorSpan(DAY_TINT), 0, line.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        line.append("  ").append(time);
        salat.setText(line);
    }

    /** Today as "27th" (the day of the month with its English ordinal) and "Oct" (the month's short name). */
    private static void showDate(TextView day, TextView month) {
        Calendar now = Calendar.getInstance();
        int d = now.get(Calendar.DAY_OF_MONTH);
        String suffix = d >= 11 && d <= 13 ? "th" : d % 10 == 1 ? "st" : d % 10 == 2 ? "nd" : d % 10 == 3 ? "rd" : "th";
        day.setText(d + suffix);
        month.setText(now.getDisplayName(Calendar.MONTH, Calendar.SHORT, java.util.Locale.ENGLISH));
    }

    /** The part of the day now, from Open-Meteo's sunrise/sunset for today when the weather has them. */
    private SkyBackground.Phase sunPhase() {
        Calendar now = Calendar.getInstance();
        Weather w = Weather.saved(this);
        String today = String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", now.get(Calendar.YEAR),
                now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH));
        return Sun.phase(now, w == null ? -1 : Weather.minuteOn(w.sunrise, today),
                w == null ? -1 : Weather.minuteOn(w.sunset, today));
    }

    /** Cross-fades the wallpaper when the sun has moved into another part of the day, and checks again in a minute. */
    private void followSun() {
        main.removeCallbacks(followSun);
        main.postDelayed(followSun, 60_000L);
        SkyBackground.Phase next = sunPhase();
        if (next == phase) {
            return;
        }
        Drawable from = root.getBackground() instanceof TransitionDrawable
                ? ((TransitionDrawable) root.getBackground()).getDrawable(1) : root.getBackground();
        TransitionDrawable fade = new TransitionDrawable(new Drawable[] {from, new SkyBackground(next)});
        fade.setCrossFadeEnabled(true);
        root.setBackground(fade);
        fade.startTransition(1500);
        phase = next;
    }

    @Override
    protected void onDestroy() {
        if (bar != null) {
            bar.stop();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // this is Home: Back only closes an open room panel, otherwise it has nowhere to go
        if (bar != null) {
            bar.back();
        }
    }

    /** Rebuilds the row and returns the tile to focus: the app opened last, else the first one. */
    private View fillRow() {
        Map<String, ResolveInfo> apps = installedApps();
        // only rebuild when an app was installed, removed or updated (its APK path changes): every rebuild decodes all
        // the icons again and uploads them to the GPU, and the old copies linger until a garbage collection
        StringBuilder signature = new StringBuilder();
        for (ResolveInfo ri : apps.values()) {
            signature.append(ri.activityInfo.applicationInfo.sourceDir).append('|');
        }
        if (!signature.toString().equals(rowApps)) {
            rowApps = signature.toString();
            row.removeAllViews();
            for (Map.Entry<String, ResolveInfo> app : apps.entrySet()) {
                View tile = tile(app.getKey(), app.getValue());
                tile.setTag(app.getKey());
                row.addView(tile);
            }
        }
        if (apps.isEmpty()) {
            show(null, "No apps yet", "Open the Appstore below to get some.");
        }
        View again = lastOpened == null ? null : row.findViewWithTag(lastOpened);
        return again != null ? again : row.getChildAt(0);
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
        // the focused tile casts a soft shadow, as on tvOS (or glows in the app's colour, see COLOURED_GLOW)
        GlowTile tile = new GlowTile(this, COLOURED_GLOW ? look[2] : 0xFF000000, px(22), px(14));
        if (!COLOURED_GLOW) {
            tile.setShadow(px(16), 0.6f);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(184), px(104));
        lp.setMargins(px(8), 0, px(8), 0);
        tile.setLayoutParams(lp);
        // full-bleed artwork, no rim, as on tvOS
        tile.setCameraDistance(px(1400) * 8f);

        tile.addView(logoTile(ri, look), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return finishTile(tile, pkg, ri);
    }

    /** The app's logo, large, on a gradient in its colours (see LOGO_TILES). */
    private View logoTile(ResolveInfo ri, int[] colours) {
        FrameLayout art = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {colours[0], colours[1]});
        bg.setCornerRadius(px(14));
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

    private View finishTile(GlowTile tile, String pkg, ResolveInfo ri) {
        String name = label(ri);
        String[] info = ABOUT.containsKey(pkg) ? ABOUT.get(pkg) : new String[] {"OPEN", "Press select to open."};
        tile.setFocusable(true);
        tile.setOnClickListener(v -> open(pkg));
        tile.setOnFocusChangeListener((v, focused) -> {
            tile.setGlowing(focused);
            if (focused) {
                // tvOS-style: the tile springs up, tips slightly as if pushed in the direction you moved, settles
                // flat, and a sheen of light crosses it
                int index = row.indexOfChild(v);
                v.setRotationY(lastFocusedTile < 0 || index == lastFocusedTile ? 0f : index > lastFocusedTile ? -6f : 6f);
                lastFocusedTile = index;
                v.animate().scaleX(1.14f).scaleY(1.14f).rotationY(0f).translationZ(px(12)).setDuration(420)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();
                tile.sweep();
            } else {
                v.animate().scaleX(1f).scaleY(1f).rotationY(0f).translationZ(0).setDuration(220)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            }
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

    /**
     * A round glass button with a line icon, the same as the Home Assistant bar's room buttons, that like an app tile
     * describes itself above the row while focused.
     */
    private View iconButton(BarIcon.Kind kind, String label, String captionText, String aboutText,
            View.OnClickListener click) {
        BarIcon icon = new BarIcon(kind);
        icon.set(false, 0f, 0);
        return iconButton(icon, 7, label, captionText, aboutText, click);
    }

    private View iconButton(Drawable icon, int padDp, String label, String captionText, String aboutText,
            View.OnClickListener click) {
        FrameLayout button = new FrameLayout(this);
        ImageView image = new ImageView(this);
        image.setImageDrawable(icon);
        int pad = px(padDp);
        image.setPadding(pad, pad, pad, pad);
        button.addView(image, new FrameLayout.LayoutParams(px(ICON_BUTTON_DP), px(ICON_BUTTON_DP)));
        button.setContentDescription(label);
        button.setBackground(roundGlass(false));
        button.setFocusable(true);
        button.setOnClickListener(click);
        button.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(roundGlass(focused));
            v.animate().scaleX(focused ? 1.15f : 1f).scaleY(focused ? 1.15f : 1f).setDuration(150).start();
            if (focused) {
                show(captionText, label, aboutText);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(ICON_BUTTON_DP), px(ICON_BUTTON_DP));
        lp.setMargins(px(5), 0, px(5), 0);
        button.setLayoutParams(lp);
        return button;
    }

    /**
     * The white "fire tv" logo from Fire OS's setup screens (read from the system at run time, not shipped with this
     * app), or a drawn TV if it can't be found. The launcher's own icon is a profile picture, not the logo.
     */
    private Drawable fireTvLogo() {
        try {
            android.content.res.Resources oobe = getPackageManager().getResourcesForApplication(OOBE_RESOURCES);
            int id = oobe.getIdentifier("oobe_firetv_logo", "drawable", OOBE_RESOURCES);
            if (id != 0) {
                return oobe.getDrawable(id, null);
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            // fall back to the drawn TV
        }
        BarIcon tv = new BarIcon(BarIcon.Kind.TV);
        tv.set(false, 0f, 0);
        return tv;
    }

    /**
     * The Appstore's own "apps" logo, lifted off its white square for the dark glass: the white goes, the black
     * lettering turns white and the orange smile stays orange. The drawn app grid if the Appstore can't be read.
     */
    private Drawable appstoreLogo() {
        Drawable icon;
        try {
            icon = sharpIcon(getPackageManager().getApplicationInfo(APPSTORE, 0));
        } catch (PackageManager.NameNotFoundException e) {
            BarIcon grid = new BarIcon(BarIcon.Kind.STORE);
            grid.set(false, 0f, 0);
            return grid;
        }
        int n = 192;
        Bitmap bitmap = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        icon.setBounds(0, 0, n, n);
        icon.draw(new Canvas(bitmap));
        int[] pixels = new int[n * n];
        bitmap.getPixels(pixels, 0, n, 0, 0, n, n);
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i];
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            int least = Math.min(r, Math.min(g, b));
            int most = Math.max(r, Math.max(g, b));
            int alpha = (255 - least) * (c >>> 24) / 255;   // white paper → clear, ink → solid
            pixels[i] = (alpha << 24) | (most - least > 60 ? c & 0xFFFFFF : 0xFFFFFF);   // colour stays, grey turns white
        }
        bitmap.setPixels(pixels, 0, n, 0, 0, n, n);
        return new BitmapDrawable(getResources(), bitmap);
    }

    /** The room buttons' glass: brighter, with a white ring, when focused. */
    private GradientDrawable roundGlass(boolean focused) {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                focused ? new int[] {0x66FFFFFF, 0x33FFFFFF} : new int[] {0x33FFFFFF, 0x17FFFFFF});
        bg.setCornerRadius(px(ICON_BUTTON_DP) / 2f);
        bg.setStroke(Math.max(1, px(focused ? 2 : 1)), focused ? 0xF2FFFFFF : 0x40FFFFFF);
        return bg;
    }

    /** Asks before handing the screen to Amazon's home, which then stays for 10 minutes. Cancel has the focus. */
    private void confirmAmazonHome() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(28), px(22), px(28), px(20));
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(0xF20B2530);
        panel.setCornerRadius(px(20));
        panel.setStroke(Math.max(1, px(1)), 0x40FFFFFF);
        box.setBackground(panel);
        TextView question = new TextView(this);
        style(question, 22, 0xFFFFFFFF, "sans-serif-medium");
        question.setText("Open Amazon's home?");
        box.addView(question);
        TextView detail = new TextView(this);
        style(detail, 14, 0xD9FFFFFF, "sans-serif");
        detail.setText("For the next 10 minutes, Home goes to Amazon's home instead of this screen.");
        detail.setPadding(0, px(8), 0, px(18));
        detail.setMaxWidth(px(340));
        box.addView(detail);
        LinearLayout choices = new LinearLayout(this);
        choices.setGravity(Gravity.END);
        TextView cancel = pill("Cancel", v -> dialog.dismiss());
        choices.addView(cancel);
        choices.addView(pill("Open", v -> {
            dialog.dismiss();
            openAmazonHome();
        }));
        box.addView(choices, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(box);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            dialog.getWindow().setDimAmount(0.6f);
        }
        dialog.show();
        cancel.requestFocus();
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
