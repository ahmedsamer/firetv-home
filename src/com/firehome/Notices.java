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
    private static final int NEWS_DATE = 0xFF9EDDE2;   // the clock's pale cyan day (only shown when not today); the time takes the dot's blue
    private static final int NEWS_TEXT_MAX_DP = 310;   // with the date, the pill stays inside the ~476 dp between the corners
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
    private final Runnable newsMidnight;
    private String lastNews;
    private final LinearLayout top;
    private java.util.function.Consumer<Boolean> newsListener;

    Notices(Context context, float dp) {
        this.context = context;
        this.dp = dp;
        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        alert = new Pill(column);
        warning = new Pill(column);
        vacuum = new Pill(column);
        top = new LinearLayout(context);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        news = new Pill(top);
        // clear of the departures in the top-right corner: a long headline is cut, the date never is
        news.text.setMaxWidth(px(NEWS_TEXT_MAX_DP));
        alertOver = () -> alert.hide();
        warningOver = () -> warning.hide();
        newsOver = () -> showNews(null, null);
        newsMidnight = () -> {
            if (lastNews != null) {
                news(lastNews);
            }
        };
    }

    View view() {
        return column;
    }

    /** The news pill's own place, above the clock. */
    View newsView() {
        return top;
    }

    /** Told true while the news pill is up, so the clock and everything under it can make room. */
    void onNews(java.util.function.Consumer<Boolean> listener) {
        newsListener = listener;
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
     * for the district (berlin.de, translated by Home Assistant), shown above the clock with its date and time until end.
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
        main.removeCallbacks(newsMidnight);
        long left = end * 1000 - System.currentTimeMillis();
        if (text.isEmpty() || (end > 0 && left <= 0)) {
            showNews(null, null);
            return;
        }
        CharSequence when = null;
        if (ts > 0) {
            java.util.Date at = new java.util.Date(ts * 1000);
            String time = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH).format(at);
            if (sameDay(ts)) {
                // today's: the time alone; at midnight it is drawn again and gains its date
                android.text.SpannableString t = new android.text.SpannableString(time);
                t.setSpan(new android.text.style.ForegroundColorSpan(NEWS), 0, time.length(), 0);
                when = t;
                lastNews = payload;
                main.postDelayed(newsMidnight, untilMidnight());
            } else {
                String day = new java.text.SimpleDateFormat("d MMM", java.util.Locale.ENGLISH).format(at);
                android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(day + "  " + time);
                b.setSpan(new android.text.style.ForegroundColorSpan(NEWS_DATE), 0, day.length(), 0);
                b.setSpan(new android.text.style.ForegroundColorSpan(NEWS), day.length() + 2, b.length(), 0);
                when = b;
            }
        }
        if (end > 0) {
            main.postDelayed(newsOver, left);
        }
        showNews(text, when);
    }

    /** null hides the news pill. */
    private void showNews(String text, CharSequence when) {
        if (text == null) {
            news.hide();
        } else {
            news.show(NEWS, text, when);
        }
        if (newsListener != null) {
            newsListener.accept(text != null);
        }
    }

    private static long untilMidnight() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.add(java.util.Calendar.DAY_OF_YEAR, 1);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 5);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis() - System.currentTimeMillis();
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
        private final TextView detail;

        Pill(LinearLayout parent) {
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
            text.setEllipsize(android.text.TextUtils.TruncateAt.END);
            view.addView(text);
            detail = new TextView(context);
            detail.setTextSize(12);   // the news date and time: smaller than the text, coloured per part
            detail.setTextColor(0xB3FFFFFF);
            detail.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            detail.setSingleLine();
            detail.setVisibility(View.GONE);
            LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            detailLp.setMargins(px(10), 0, 0, 0);
            view.addView(detail, detailLp);
            view.setVisibility(View.GONE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, px(8));
            parent.addView(view, lp);
        }

        void show(int colour, String line) {
            show(colour, line, null);
        }

        /** detail: a dimmer label after the text that is never cut off (the text is, at its max width). */
        void show(int colour, String line, CharSequence extra) {
            dot.setColor(colour);
            text.setText(line);
            detail.setText(extra == null ? "" : extra);
            detail.setVisibility(extra == null ? View.GONE : View.VISIBLE);
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
