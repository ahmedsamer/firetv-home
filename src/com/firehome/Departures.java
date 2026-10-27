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
 * One stop's next departures, for the top-right corner, laid out like the climate block (a step smaller): a line icon (a bus or an
 * underground train) and the minutes to the next departure in big digits, the line ("147", "U8") as a smaller tag in
 * a light shade of the line's colour, and below a small line per direction with its next departure
 * ("Hermannstr. 3 min" over "Wittenau 7 min"). Home Assistant publishes
 * {"line", "color", "walk", "deps": [{"ts": epoch seconds, "dir", "delay": seconds, "x": cancelled}]} to
 * firetv/state/bus and firetv/state/metro; the minutes are worked out here, so tick() keeps them counting down
 * between updates. Departures sooner than the walk to the stop, and cancelled ones, are left out.
 */
class Departures {
    /** Text sizes (sp) and icon size (dp), so the corner can match the climate block. */
    static final class Sizes {
        final float big;
        final float tag;
        final float line;
        final int icon;

        Sizes(float big, float tag, float line, int icon) {
            this.big = big;
            this.tag = tag;
            this.line = line;
            this.icon = icon;
        }
    }

    private static final int MAX_DIRECTIONS = 2;
    private static final int LATE_S = 120;   // a departure this late shows its minutes in amber
    private static final int AMBER = 0xFFFFB13B;

    private final float dp;
    private final LinearLayout view;
    private final TextView minutes;
    private final TextView tag;
    private final TextView details;
    private final String fallbackLine;
    private JSONObject data;

    Departures(Context context, float dp, BarIcon.Kind kind, String fallbackLine, Sizes sizes) {
        this.dp = dp;
        this.fallbackLine = fallbackLine;
        view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setGravity(Gravity.END);   // right-aligned, against the screen's edge
        LinearLayout now = new LinearLayout(context);
        now.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(context);
        BarIcon drawable = new BarIcon(kind);
        drawable.set(false, 0f, 0);
        icon.setImageDrawable(drawable);
        now.addView(icon, new LinearLayout.LayoutParams(px(sizes.icon), px(sizes.icon)));
        minutes = text(context, sizes.big, 0xFFFFFFFF, 12, 2);
        minutes.setPadding(px(10), 0, 0, 0);
        tag = text(context, sizes.tag, 0xFFFFFFFF, 8, 1);
        tag.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tag.setPadding(px(10), 0, 0, 0);
        LinearLayout reading = new LinearLayout(context);   // baseline-aligned, like the climate's reading
        reading.addView(minutes);
        reading.addView(tag);
        now.addView(reading);
        details = text(context, sizes.line, 0xD9FFFFFF, 8, 1);
        details.setGravity(Gravity.END);   // one direction per line, right-aligned
        // wrap_content: a vertical LinearLayout's default is match_parent, which defeats the right alignment
        view.addView(now, wrap());
        view.addView(details, wrap());
        view.setVisibility(View.GONE);
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
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
        tag.setText(line.isEmpty() ? fallbackLine : line);
        tag.setTextColor(light(colour(data.optString("color", ""), 0xFF7A3E9D)));

        long now = System.currentTimeMillis() / 1000;
        int walk = data.optInt("walk", 0);
        // the next departure per direction, in the order the directions first come up, and the soonest of all
        Map<String, int[]> next = new LinkedHashMap<>();
        int[] soonest = null;
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
            if (soonest == null || left < soonest[0]) {
                soonest = dep;
            }
            String direction = shorten(d.optString("dir", ""));
            if (!next.containsKey(direction) && next.size() < MAX_DIRECTIONS) {
                next.put(direction, dep);
            }
        }

        SpannableStringBuilder big = new SpannableStringBuilder();
        SpannableStringBuilder small = new SpannableStringBuilder();
        if (soonest == null) {
            big.append("–");
            small.append("No departures soon");
        } else {
            appendMinutes(big, soonest);
            appendUnit(big, 0.45f);
            for (Map.Entry<String, int[]> e : next.entrySet()) {
                if (small.length() > 0) {
                    small.append('\n');
                }
                small.append(e.getKey()).append(' ');
                appendMinutes(small, e.getValue());
                appendUnit(small, 0.7f);
            }
        }
        minutes.setText(big);
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
