package com.firehome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One stop's next departures, for the top-right corner: a line icon (a bus or an underground train) beside a small
 * line per direction with the line's name in a light shade of its colour, the direction and its next departure
 * ("U8 Wittenau 5 min"). Home Assistant publishes
 * {"line", "color", "walk", "deps": [{"ts": epoch seconds, "dir", "delay": seconds, "x": cancelled}]} to
 * firetv/state/bus and firetv/state/metro; the minutes are worked out here, so tick() keeps them counting down
 * between updates. Departures sooner than the walk to the stop, and cancelled ones, are left out.
 */
class Departures {
    private static final int MAX_DIRECTIONS = 2;
    private static final int LATE_S = 120;   // a departure this late shows its minutes in amber
    private static final int AMBER = 0xFFFFB13B;

    private final float dp;
    private final LinearLayout view;
    private final TextView details;
    private final String fallbackLine;
    private JSONObject data;

    Departures(Context context, float dp, BarIcon.Kind kind, String fallbackLine, float textSp, int iconDp) {
        this.dp = dp;
        this.fallbackLine = fallbackLine;
        view = new LinearLayout(context);
        view.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(context);
        BarIcon drawable = new BarIcon(kind);
        drawable.set(false, 0f, 0);
        icon.setImageDrawable(drawable);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(px(iconDp), px(iconDp));
        iconLp.setMargins(0, 0, px(10), 0);
        view.addView(icon, iconLp);
        details = text(context, textSp, 0xD9FFFFFF, 8, 1);
        details.setGravity(Gravity.END);   // one direction per line, right-aligned against the screen's edge
        // the text takes the rest of the width, so stacked stops keep their icons in one column at the left
        view.addView(details, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        view.setVisibility(View.GONE);
    }

    View view() {
        return view;
    }

    /** A new payload from Home Assistant; anything unreadable is ignored and the old one stays. */
    void update(String payload) {
        try {
            data = new JSONObject(payload);
        } catch (Exception e) {
            return;
        }
        tick();
    }

    /** Redraws the minutes for now; call once a minute. */
    void tick() {
        if (data == null) {
            return;
        }
        String line = data.optString("line", fallbackLine);
        String name = line.isEmpty() ? fallbackLine : line;
        int shade = light(colour(data.optString("color", ""), 0xFF7A3E9D));

        long now = System.currentTimeMillis() / 1000;
        int walk = data.optInt("walk", 0);
        // the next departure per direction, in the order the directions first come up
        Map<String, int[]> next = new LinkedHashMap<>();
        JSONArray deps = data.optJSONArray("deps");
        for (int i = 0; deps != null && i < deps.length(); i++) {
            JSONObject d = deps.optJSONObject(i);
            if (d == null || d.optBoolean("x")) {
                continue;
            }
            int left = (int) Math.floor((d.optLong("ts") - now) / 60.0);
            if (left < walk) {
                continue;
            }
            int[] dep = {left, d.optInt("delay", 0) >= LATE_S ? 1 : 0};
            String direction = shorten(d.optString("dir", ""));
            if (!next.containsKey(direction) && next.size() < MAX_DIRECTIONS) {
                next.put(direction, dep);
            }
        }

        SpannableStringBuilder small = new SpannableStringBuilder();
        if (next.isEmpty()) {
            small.append("No departures soon");
        }
        for (Map.Entry<String, int[]> e : next.entrySet()) {
            if (small.length() > 0) {
                small.append('\n');
            }
            int at = small.length();
            small.append(name);
            small.setSpan(new ForegroundColorSpan(shade), at, small.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            small.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), at, small.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            small.append(' ').append(e.getKey()).append(' ');
            appendMinutes(small, e.getValue());
            appendUnit(small, 0.7f);
        }
        details.setText(small);
        view.setVisibility(View.VISIBLE);
    }

    private static void appendMinutes(SpannableStringBuilder text, int[] dep) {
        int at = text.length();
        text.append(dep[0] == 0 ? "now" : String.valueOf(dep[0]));
        if (dep[1] == 1) {
            text.setSpan(new ForegroundColorSpan(AMBER), at, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void appendUnit(SpannableStringBuilder text, float size) {
        int at = text.length();
        text.append(" min");
        text.setSpan(new RelativeSizeSpan(size), at, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private TextView text(Context context, float sp, int colour, int shadowDp, int shadowDy) {
        TextView t = new TextView(context);
        t.setTextSize(sp);
        t.setTextColor(colour);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setShadowLayer(px(shadowDp), 0, px(shadowDy), 0x66000000);
        return t;
    }

    /** "S+U Hermannstraße (Berlin)" → "Hermannstr.": short enough for one row. */
    static String shorten(String direction) {
        String d = direction.replaceAll("\\s*\\(Berlin\\)", "").replaceFirst("^(S\\+U|S|U)\\s+", "")
                .replace("straße", "str.").replace("Straße", "Str.").trim();
        return d.length() > 14 ? d.substring(0, 13).trim() + "…" : d;
    }

    /** The line colour mixed halfway to white, so a dark BVG colour still reads on the dark sky. */
    private static int light(int colour) {
        return Color.rgb((Color.red(colour) + 255) / 2, (Color.green(colour) + 255) / 2,
                (Color.blue(colour) + 255) / 2);
    }

    private static int colour(String hex, int fallback) {
        try {
            return hex.isEmpty() ? fallback : Color.parseColor(hex);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private int px(int dpValue) {
        return Math.round(dpValue * dp);
    }
}
