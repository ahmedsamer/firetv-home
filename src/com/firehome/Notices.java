package com.firehome;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Things worth a glance, centred under the clock as small frosted pills (tvOS's banners): a one-off alert from Home
 * Assistant (mail, rain on a tilted window, heating with a window open) that goes away on its own after a few
 * seconds, a DWD weather warning of level 2 or more for as long as it lasts, the robot vacuum while it is out, and
 * the latest police report for the district for a few hours.
 * Nothing shows when there is nothing to say.
 */
final class Notices {
    private static final long ALERT_MS = 12_000L;
    private static final long IN_MS = 300;
    private static final long OUT_MS = 400;
    private static final int MAIL = 0xFF9EDDE2;   // the day's pale cyan
    private static final int RAIN = 0xFF8CCBFF;   // the climate corner's "cooler" blue
    private static final int HEAT = 0xFFFF9A7A;   // and its "warmer" coral
    private static final int VACUUM = 0xFF8EE68A;   // the prayer countdown's soft green
    private static final int NEWS = 0xFF7FB2FF;     // police blue
    private static final int AMBER = 0xFFFFB13B;
    // DWD's own levels: 2 orange (markant), 3 red (unwetter), 4 violet (extremes unwetter)
    private static final int[] WARNING = {AMBER, AMBER, AMBER, 0xFFFF6B6B, 0xFFC77DFF};

    private final Context context;
    private final float dp;
    private final LinearLayout column;
    private final Pill alert;
    private final Pill warning;
    private final Pill vacuum;
    private final Pill news;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable alertOver;
    private final Runnable warningOver;
    private final Runnable newsOver;

    Notices(Context context, float dp) {
        this.context = context;
        this.dp = dp;
        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        alert = new Pill();
        warning = new Pill();
        vacuum = new Pill();
        news = new Pill();
        alertOver = () -> alert.hide();
        warningOver = () -> warning.hide();
        newsOver = () -> news.hide();
    }

    View view() {
        return column;
    }

    /** {"kind": "mail" | "rain" | "heat", "text": "..."}: shown for ALERT_MS, a newer one takes its place. */
    void alert(String payload) {
        String kind;
        String text;
        try {
            org.json.JSONObject json = new org.json.JSONObject(payload);
            kind = json.optString("kind");
            text = json.getString("text");
        } catch (org.json.JSONException e) {
            return;
        }
        int colour = kind.equals("mail") ? MAIL : kind.equals("rain") ? RAIN : kind.equals("heat") ? HEAT : AMBER;
        main.removeCallbacks(alertOver);
        alert.show(colour, text);
        main.postDelayed(alertOver, ALERT_MS);
    }

    /** {"level": 0-4, "name": "Sturmböen", "end": epoch seconds or 0}: shown from level 2 until it ends. */
    void warning(String payload) {
        int level;
        String name;
        long end;
        try {
            org.json.JSONObject json = new org.json.JSONObject(payload);
            level = json.getInt("level");
            name = json.optString("name");
            end = json.optLong("end");
        } catch (org.json.JSONException e) {
            return;
        }
        main.removeCallbacks(warningOver);
        long left = end * 1000 - System.currentTimeMillis();
        if (level < 2 || name.isEmpty() || (end > 0 && left <= 0)) {
            warning.hide();
            return;
        }
        String text = name;
        if (end > 0) {
            text += "  until " + new java.text.SimpleDateFormat(sameDay(end) ? "h:mm a" : "EEE h:mm a",
                    java.util.Locale.ENGLISH).format(new java.util.Date(end * 1000));
            main.postDelayed(warningOver, left);
        }
        warning.show(WARNING[Math.min(level, WARNING.length - 1)], text);
    }

    /** {"state": the vacuum entity's state, "room": "Kitchen"}: shown while it cleans, heads home or is stuck. */
    void vacuum(String payload) {
        String state;
        String room;
        try {
            org.json.JSONObject json = new org.json.JSONObject(payload);
            state = json.getString("state");
            room = json.optString("room");
        } catch (org.json.JSONException e) {
            return;
        }
        if (state.equals("cleaning")) {
            vacuum.show(VACUUM, room.isEmpty() || room.equals("unknown") || room.equals("unavailable")
                    ? "Vacuum cleaning" : "Cleaning the " + (room.equals("Living") ? "living room" : room.toLowerCase(
                    java.util.Locale.ENGLISH)));
        } else if (state.equals("returning")) {
            vacuum.show(VACUUM, "Vacuum heading back to its dock");
        } else if (state.equals("error")) {
            vacuum.show(AMBER, "Vacuum is stuck and needs help");
        } else {
            vacuum.hide();
        }
    }

    /**
     * {"text": "Cyclist badly injured in Mitte", "ts": epoch seconds, "end": epoch seconds}: the newest police report
     * for the district (berlin.de, translated by Home Assistant), shown with its time until end.
     */
    void news(String payload) {
        String text;
        long ts;
        long end;
        try {
            org.json.JSONObject json = new org.json.JSONObject(payload);
            text = json.optString("text").trim();
            ts = json.optLong("ts");
            end = json.optLong("end");
        } catch (org.json.JSONException e) {
            return;
        }
        main.removeCallbacks(newsOver);
        long left = end * 1000 - System.currentTimeMillis();
        if (text.isEmpty() || (end > 0 && left <= 0)) {
            news.hide();
            return;
        }
        if (ts > 0) {
            text += "  · " + new java.text.SimpleDateFormat(sameDay(ts) ? "h:mm a" : "EEE h:mm a",
                    java.util.Locale.ENGLISH).format(new java.util.Date(ts * 1000));
        }
        if (end > 0) {
            main.postDelayed(newsOver, left);
        }
        news.show(NEWS, text);
    }

    private static boolean sameDay(long epochSeconds) {
        java.util.Calendar a = java.util.Calendar.getInstance();
        java.util.Calendar b = java.util.Calendar.getInstance();
        b.setTimeInMillis(epochSeconds * 1000);
        return a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR)
                && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR);
    }

    private int px(float dpValue) {
        return Math.round(dpValue * dp);
    }

    /** One frosted capsule: a coloured dot, then the text. Fades and settles in, fades out and leaves the column. */
    private final class Pill {
        private final LinearLayout view;
        private final GradientDrawable dot;
        private final TextView text;

        Pill() {
            view = new LinearLayout(context);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setPadding(px(14), px(7), px(18), px(7));
            GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {0x33FFFFFF, 0x17FFFFFF});   // the bar's frosted glass, unfocused
            glass.setCornerRadius(px(100));
            glass.setStroke(Math.max(1, px(1)), 0x40FFFFFF);
            view.setBackground(glass);
            View mark = new View(context);
            dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            mark.setBackground(dot);
            LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(px(8), px(8));
            markLp.setMargins(0, 0, px(10), 0);
            view.addView(mark, markLp);
            text = new TextView(context);
            text.setTextSize(15);
            text.setTextColor(0xF2FFFFFF);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            text.setSingleLine();
            view.addView(text);
            view.setVisibility(View.GONE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, px(8));
            column.addView(view, lp);
        }

        void show(int colour, String line) {
            dot.setColor(colour);
            text.setText(line);
            if (view.getVisibility() == View.VISIBLE && view.getAlpha() == 1f) {
                return;
            }
            view.animate().cancel();
            if (view.getVisibility() != View.VISIBLE) {
                view.setAlpha(0f);
                view.setTranslationY(-px(6));
                view.setVisibility(View.VISIBLE);
            }
            view.animate().alpha(1f).translationY(0).setDuration(IN_MS)
                    .setInterpolator(new DecelerateInterpolator());
        }

        void hide() {
            if (view.getVisibility() != View.VISIBLE) {
                return;
            }
            view.animate().cancel();
            view.animate().alpha(0f).setDuration(OUT_MS).setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> view.setVisibility(View.GONE));
        }
    }
}
