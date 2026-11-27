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
    private static final long NEWS_GLANCE_MS = 2 * 60_000L;   // how long the news shows after a Home press
    private static final int NEWS_TEXT_MAX_DP = 270;   // with date, time and counter the pill stays inside the ~476 dp between the corners
    private static final long NEWS_ROTATE_MS = 10_000L;   // each report's turn when there are several
    private static final int NEWS_COUNT = 0x99FFFFFF;     // the "1/3" counter, dim white
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
    private final Runnable newsRotate;
    private final java.util.List<org.json.JSONObject> newsItems = new java.util.ArrayList<>();
    private int newsIndex;
    private String lastNews;     // the latest firetv/state/news payload, shown again on each Home press
    private boolean front;       // the home screen is in front
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
        newsRotate = this::rotateNews;
        newsOver = () -> {
            main.removeCallbacks(newsRotate);
            showNews(null, null);
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
     * {"items": [{"text": "Mitte: Cyclist hurt in traffic accident", "ts": epoch s, "end": epoch s}, ...]}: the
     * district's police reports of the last 12 h, newest first (berlin.de, translated by Home Assistant). Shown above
     * the clock for two minutes after each Home press, one at a time when there are several.
     */
    void news(String payload) {
        lastNews = payload;
        if (front) {
            glance();
        }
    }

    /** The home screen came to the front (a Home press, or back from an app): show the news for a glance. */
    void homeShown() {
        front = true;
        glance();
    }

    void homeHidden() {
        front = false;
    }

    /**
     * The current reports for NEWS_GLANCE_MS, newest first, one at a time for NEWS_ROTATE_MS each with a small
     * "1/3" counter; then they go and the clock moves back. Payload {"items": [{text, ts, end}, ...]}; the older
     * single {text, ts, end} is read as a list of one. Reports past their end are left out.
     */
    private void glance() {
        main.removeCallbacks(newsOver);
        main.removeCallbacks(newsRotate);
        newsItems.clear();
        if (lastNews == null) {
            return;
        }
        try {
            org.json.JSONObject json = new org.json.JSONObject(lastNews);
            org.json.JSONArray items = json.optJSONArray("items");
            if (items == null) {
                items = new org.json.JSONArray().put(json);
            }
            long now = System.currentTimeMillis();
            for (int i = 0; i < items.length(); i++) {
                org.json.JSONObject item = items.optJSONObject(i);
                if (item == null || item.optString("text").trim().isEmpty()) {
                    continue;
                }
                long end = item.optLong("end");
                if (end > 0 && end * 1000 <= now) {
                    continue;
                }
                newsItems.add(item);
            }
        } catch (org.json.JSONException e) {
            return;
        }
        if (newsItems.isEmpty()) {
            showNews(null, null);
            return;
        }
        newsIndex = 0;
        showNews(newsText(0), newsWhen(0));
        if (newsItems.size() > 1) {
            main.postDelayed(newsRotate, NEWS_ROTATE_MS);
        }
        main.postDelayed(newsOver, NEWS_GLANCE_MS);
    }

    private void rotateNews() {
        if (newsItems.size() < 2) {
            return;
        }
        newsIndex = (newsIndex + 1) % newsItems.size();
        news.swap(NEWS, newsText(newsIndex), newsWhen(newsIndex));
        main.postDelayed(newsRotate, NEWS_ROTATE_MS);
    }

    private String newsText(int i) {
        return newsItems.get(i).optString("text").trim();
    }

    /** "11:44 AM" for today's, "26 Nov  8:06 PM" otherwise, then "1/3" when there are several. */
    private CharSequence newsWhen(int i) {
        long ts = newsItems.get(i).optLong("ts");
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        if (ts > 0) {
            java.util.Date at = new java.util.Date(ts * 1000);
            if (!sameDay(ts)) {
                String day = new java.text.SimpleDateFormat("d MMM", java.util.Locale.ENGLISH).format(at);
                sb.append(day, new android.text.style.ForegroundColorSpan(NEWS_DATE), 0).append("  ");
            }
            String time = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH).format(at);
            sb.append(time, new android.text.style.ForegroundColorSpan(NEWS), 0);
        }
        if (newsItems.size() > 1) {
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append((i + 1) + "/" + newsItems.size(), new android.text.style.ForegroundColorSpan(NEWS_COUNT), 0);
        }
        return sb.length() > 0 ? sb : null;
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
            text.animate().cancel();
            detail.animate().cancel();
            text.setAlpha(1f);
            detail.setAlpha(1f);
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

        /** A new line in a pill that is already up: the words fade out and the new ones in; the glass stays. */
        void swap(int colour, String line, CharSequence extra) {
            if (view.getVisibility() != View.VISIBLE) {
                show(colour, line, extra);
                return;
            }
            for (View v : new View[] {text, detail}) {
                v.animate().cancel();
            }
            text.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                dot.setColor(colour);
                text.setText(line);
                detail.setText(extra == null ? "" : extra);
                detail.setVisibility(extra == null ? View.GONE : View.VISIBLE);
                text.animate().alpha(1f).setDuration(300).setInterpolator(new DecelerateInterpolator());
                detail.animate().alpha(1f).setDuration(300).setInterpolator(new DecelerateInterpolator());
            });
            detail.animate().alpha(0f).setDuration(200);
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
