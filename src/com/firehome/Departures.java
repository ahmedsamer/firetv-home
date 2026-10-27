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
 * One stop's next departures, for the top-right corner: a line icon (a bus or an underground train) beside a row per
 * direction in three aligned columns: the line's name in a light shade of its colour, the direction, and its next
 * departure ("U8 | Wittenau | 5 min"). Home Assistant publishes
 * {"line", "color", "walk", "deps": [{"ts": epoch seconds, "dir", "delay": seconds, "x": cancelled}]} to
 * firetv/state/bus and firetv/state/metro; the minutes are worked out here, so tick() keeps them counting down
 * between updates. Departures sooner than the walk to the stop, and cancelled ones, are left out.
 */
class Departures {
    private static final int MAX_DIRECTIONS = 2;
    private static final int NAME_DP = 34;      // wide enough for "147" in bold
    private static final int MINUTES_DP = 52;   // wide enough for "59 min"
    private static final int LATE_S = 120;   // a departure this late shows its minutes in amber
    private static final int AMBER = 0xFFFFB13B;

    private final float dp;
    private final LinearLayout view;
    private final Context context;
    private final float textSp;
    private final LinearLayout rows;
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
        this.context = context;
        this.textSp = textSp;
        rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        // the rows take the rest of the width, so stacked stops keep their icons in one column at the left
        view.addView(rows, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
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

        rows.removeAllViews();
        if (next.isEmpty()) {
            rows.addView(row(name, shade, "No departures soon", null));
        }
        for (Map.Entry<String, int[]> e : next.entrySet()) {
            SpannableStringBuilder minutes = new SpannableStringBuilder();
            appendMinutes(minutes, e.getValue());
            appendUnit(minutes, 0.7f);
            rows.addView(row(name, shade, e.getKey(), minutes));
        }
        view.setVisibility(View.VISIBLE);
    }

    /**
     * One row in three columns, so every row of both stops lines up: the line's name (left, fixed width), the
     * direction, and the minutes (right-aligned, fixed width).
     */
    private LinearLayout row(String name, int shade, String direction, CharSequence minutes) {
        LinearLayout row = new LinearLayout(context);   // baseline-aligned
        TextView line = text(context, textSp, shade, 8, 1);
        line.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        line.setText(name);
        line.setMinWidth(px(NAME_DP));
        row.addView(line);
        TextView towards = text(context, textSp, 0xD9FFFFFF, 8, 1);
        towards.setText(direction);
        towards.setPadding(px(6), 0, px(10), 0);
        row.addView(towards, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (minutes != null) {
            TextView when = text(context, textSp, 0xD9FFFFFF, 8, 1);
            when.setText(minutes);
            when.setGravity(Gravity.END);
            when.setMinWidth(px(MINUTES_DP));
            row.addView(when);
        }
        return row;
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
