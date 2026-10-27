package com.firehome;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One stop's next departures, for the top-right corner: a badge in the line's colour ("147", "U8") over a row per
 * direction with the minutes until the next few ("Hermannstr.  3 · 8 · 13 min"). Home Assistant publishes
 * {"line", "color", "walk", "deps": [{"ts": epoch seconds, "dir", "delay": seconds, "x": cancelled}]} to
 * firetv/state/bus and firetv/state/metro; the minutes are worked out here, so tick() keeps them counting down
 * between updates. Departures sooner than the walk to the stop, and cancelled ones, are left out.
 */
class Departures {
    private static final int MAX_DIRECTIONS = 2;
    private static final int MAX_TIMES = 3;
    private static final int LATE_S = 120;   // a departure this late shows its minutes in amber
    private static final int AMBER = 0xFFFFB13B;

    private final Context context;
    private final float dp;
    private final LinearLayout view;
    private final TextView badge;
    private final LinearLayout rows;
    private final String fallbackLine;
    private JSONObject data;

    Departures(Context context, float dp, String fallbackLine) {
        this.context = context;
        this.dp = dp;
        this.fallbackLine = fallbackLine;
        view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        badge = new TextView(context);
        badge.setTextSize(15);
        badge.setTextColor(0xFFFFFFFF);
        badge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        badge.setPadding(px(9), px(1), px(9), px(1));
        view.addView(badge, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowsLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowsLp.setMargins(0, px(6), 0, 0);
        view.addView(rows, rowsLp);
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
        badge.setText(line.isEmpty() ? fallbackLine : line);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(px(6));
        shape.setColor(colour(data.optString("color", ""), 0xFF7A3E9D));
        badge.setBackground(shape);

        long now = System.currentTimeMillis() / 1000;
        int walk = data.optInt("walk", 0);
        // minutes per direction, in the order the directions first come up
        Map<String, List<int[]>> byDirection = new LinkedHashMap<>();
        JSONArray deps = data.optJSONArray("deps");
        for (int i = 0; deps != null && i < deps.length(); i++) {
            JSONObject d = deps.optJSONObject(i);
            if (d == null || d.optBoolean("x")) {
                continue;
            }
            int minutes = (int) Math.floor((d.optLong("ts") - now) / 60.0);
            if (minutes < walk) {
                continue;
            }
            String direction = shorten(d.optString("dir", ""));
            List<int[]> times = byDirection.get(direction);
            if (times == null) {
                if (byDirection.size() >= MAX_DIRECTIONS) {
                    continue;
                }
                times = new ArrayList<>();
                byDirection.put(direction, times);
            }
            if (times.size() < MAX_TIMES) {
                times.add(new int[] {minutes, d.optInt("delay", 0) >= LATE_S ? 1 : 0});
            }
        }

        rows.removeAllViews();
        if (byDirection.isEmpty()) {
            rows.addView(row("No departures soon"));
        }
        for (Map.Entry<String, List<int[]>> e : byDirection.entrySet()) {
            SpannableStringBuilder text = new SpannableStringBuilder();
            int start = text.length();
            text.append(e.getKey()).append("  ");
            text.setSpan(new ForegroundColorSpan(0xB3FFFFFF), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new RelativeSizeSpan(0.85f), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            List<int[]> times = e.getValue();
            for (int i = 0; i < times.size(); i++) {
                if (i > 0) {
                    text.append(" · ");
                }
                int at = text.length();
                text.append(times.get(i)[0] == 0 ? "now" : String.valueOf(times.get(i)[0]));
                if (times.get(i)[1] == 1) {
                    text.setSpan(new ForegroundColorSpan(AMBER), at, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            int unit = text.length();
            text.append(" min");
            text.setSpan(new RelativeSizeSpan(0.7f), unit, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            rows.addView(row(text));
        }
        view.setVisibility(View.VISIBLE);
    }

    private TextView row(CharSequence text) {
        TextView t = new TextView(context);
        t.setTextSize(17);
        t.setTextColor(0xE6FFFFFF);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setShadowLayer(px(8), 0, px(1), 0x66000000);
        t.setGravity(Gravity.START);
        t.setText(text);
        return t;
    }

    /** "S+U Hermannstraße (Berlin)" → "Hermannstr.": short enough for one row. */
    static String shorten(String direction) {
        String d = direction.replaceAll("\\s*\\(Berlin\\)", "").replaceFirst("^(S\\+U|S|U)\\s+", "")
                .replace("straße", "str.").replace("Straße", "Str.").trim();
        return d.length() > 16 ? d.substring(0, 15).trim() + "…" : d;
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
