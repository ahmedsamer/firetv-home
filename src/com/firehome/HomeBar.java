package com.firehome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A row of round glass buttons for a few Home Assistant devices, over MQTT. Home Assistant publishes each device's
 * state to firetv/state/<name> (retained) and toggles it on firetv/cmd/<name>; the broker lets this login use
 * firetv/# only. The login and the device list come from assets/homebar.properties, which is not committed: with no
 * such file the bar is simply not shown.
 */
class HomeBar {
    interface Focus {
        void show(String caption, String title, String about);
    }

    private static final Pattern STATE = Pattern.compile("['\"]state['\"]\\s*:\\s*['\"]([a-z_]+)['\"]");
    private static final Pattern POSITION = Pattern.compile("['\"]position['\"]\\s*:\\s*(-?\\d+)");

    private final Context context;
    private final float dp;
    private final Focus focus;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Device> devices = new ArrayList<>();
    private final Map<String, Device> byName = new HashMap<>();
    // the same height as the Appstore and Settings pills next to it
    private static final int SIZE_DP = 33;
    private Mqtt mqtt;
    private boolean online;

    private static class Device {
        String name;
        String label;
        BarIcon.Kind kind;
        String state = "unknown";
        int position = -1;
        BarIcon icon;
        GlowTile button;
        boolean focused;
    }

    private HomeBar(Context context, float dp, Focus focus) {
        this.context = context;
        this.dp = dp;
        this.focus = focus;
    }

    /** The bar for this install, or null when assets/homebar.properties is missing or incomplete. */
    static HomeBar load(Context context, float dp, Focus focus) {
        Properties p = new Properties();
        try (InputStream in = context.getAssets().open("homebar.properties")) {
            p.load(in);
        } catch (Exception e) {
            return null;
        }
        String host = p.getProperty("host");
        String list = p.getProperty("devices");
        if (host == null || list == null) {
            return null;
        }
        HomeBar bar = new HomeBar(context, dp, focus);
        for (String entry : list.split(";")) {
            String[] f = entry.trim().split("\\|");
            if (f.length < 3) {
                continue;
            }
            Device d = new Device();
            d.name = f[0];
            d.label = f[1];
            d.kind = f[2].equals("fan") ? BarIcon.Kind.FAN : f[2].equals("blind") ? BarIcon.Kind.BLIND
                    : f[2].equals("curtain") ? BarIcon.Kind.CURTAIN : BarIcon.Kind.LIGHT;
            bar.devices.add(d);
            bar.byName.put(d.name, d);
        }
        bar.mqtt = new Mqtt(host, Integer.parseInt(p.getProperty("port", "1883")), p.getProperty("user", ""),
                p.getProperty("password", ""), "firetv/state/+", bar.listener());
        return bar.devices.isEmpty() ? null : bar;
    }

    View view() {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        for (Device d : devices) {
            d.icon = new BarIcon(d.kind);
            d.button = new GlowTile(context, 0xFFFFC857, px(8), px(SIZE_DP / 2));
            ImageView image = new ImageView(context);
            image.setImageDrawable(d.icon);
            int pad = px(7);
            image.setPadding(pad, pad, pad, pad);
            d.button.addView(image, new android.widget.FrameLayout.LayoutParams(px(SIZE_DP), px(SIZE_DP)));
            d.button.setFocusable(true);
            d.button.setOnClickListener(v -> mqtt.publish("firetv/cmd/" + d.name, "toggle"));
            d.button.setOnFocusChangeListener((v, has) -> {
                d.focused = has;
                v.animate().scaleX(has ? 1.15f : 1f).scaleY(has ? 1.15f : 1f).setDuration(150).start();
                paint(d);
                if (has) {
                    describe(d);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(SIZE_DP), px(SIZE_DP));
            lp.setMargins(px(4), 0, px(4), 0);
            row.addView(d.button, lp);
            paint(d);
        }
        return row;
    }

    void start() {
        mqtt.start();
    }

    void stop() {
        mqtt.stop();
    }

    private Mqtt.Listener listener() {
        return new Mqtt.Listener() {
            @Override
            public void connected() {
                main.post(() -> online = true);
                mqtt.publish("firetv/sync", "1");
            }

            @Override
            public void message(String topic, String payload) {
                Device d = byName.get(topic.substring(topic.lastIndexOf('/') + 1));
                if (d == null) {
                    return;
                }
                Matcher s = STATE.matcher(payload);
                Matcher p = POSITION.matcher(payload);
                String state = s.find() ? s.group(1) : "unknown";
                int position = p.find() ? Integer.parseInt(p.group(1)) : -1;
                main.post(() -> {
                    d.state = state;
                    d.position = position;
                    paint(d);
                    if (d.focused) {
                        describe(d);
                    }
                });
            }

            @Override
            public void disconnected() {
                main.post(() -> {
                    online = false;
                    for (Device d : devices) {
                        paint(d);
                    }
                });
            }
        };
    }

    private boolean active(Device d) {
        return d.state.equals("on") || d.state.equals("open") || d.state.equals("opening");
    }

    private void paint(Device d) {
        if (d.button == null) {
            return;
        }
        boolean on = online && active(d);
        float level = d.position >= 0 ? d.position / 100f : (on ? 1f : 0f);
        // like Control Center: an "on" button is solid warm yellow with a dark icon, an off one is glass
        d.icon.set(on, level, 0xFF4A3300);
        boolean usable = online && !d.state.equals("unavailable") && !d.state.equals("unknown");
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                on ? new int[] {0xFFFFE08A, 0xFFFFC23D}
                        : d.focused ? new int[] {0x66FFFFFF, 0x33FFFFFF} : new int[] {0x33FFFFFF, 0x17FFFFFF});
        bg.setCornerRadius(px(SIZE_DP) / 2f);
        bg.setStroke(Math.max(1, px(d.focused ? 2 : 1)), d.focused ? 0xF2FFFFFF : on ? 0x00000000 : 0x40FFFFFF);
        d.button.setBackground(bg);
        d.button.setAlpha(usable ? 1f : 0.45f);
        d.button.setGlowing(on);
    }

    private void describe(Device d) {
        String what;
        if (!online) {
            what = "Not connected to Home Assistant.";
        } else if (d.state.equals("unavailable") || d.state.equals("unknown")) {
            what = "Unavailable right now.";
        } else if (d.kind == BarIcon.Kind.BLIND || d.kind == BarIcon.Kind.CURTAIN) {
            String now = d.position > 0 && d.position < 100 ? d.position + "% open" : active(d) ? "Open" : "Closed";
            what = now + " · press select to " + (active(d) ? "close" : "open") + ".";
        } else {
            what = (active(d) ? "On" : "Off") + " · press select to turn " + (active(d) ? "off" : "on") + ".";
        }
        focus.show("LIVING ROOM", d.label, what);
    }

    private int px(int v) {
        return Math.round(v * dp);
    }
}
