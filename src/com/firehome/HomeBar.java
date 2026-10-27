package com.firehome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
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
 * Home Assistant controls over MQTT, grouped by room. The bottom row holds one round glass button per room, with a
 * yellow dot while anything in it is on; selecting a room opens a glass panel above the row with that room's devices.
 * Home Assistant publishes each device's state to firetv/state/<name> (retained) and acts on firetv/cmd/<name>; the
 * broker lets this login use firetv/# only. The login, devices and rooms come from assets/homebar.properties, which is
 * not committed: with no such file the bar is simply not shown.
 */
class HomeBar {
    interface Focus {
        void show(String caption, String title, String about);
    }

    private static final Pattern STATE = Pattern.compile("['\"]state['\"]\\s*:\\s*['\"]([a-z_]+)['\"]");
    private static final Pattern POSITION = Pattern.compile("['\"]position['\"]\\s*:\\s*(-?\\d+)");
    // the same height as the Appstore and Settings pills next to it
    private static final int SIZE_DP = 33;

    private final Context context;
    private final float dp;
    private final Focus focus;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Room> rooms = new ArrayList<>();
    private final Map<String, Device> byName = new HashMap<>();
    private Mqtt mqtt;
    private boolean online;
    private LinearLayout panel;
    private View behind;
    private Room open;

    private static class Device {
        String name;
        String label;
        BarIcon.Kind kind;
        Room room;
        String state = "unknown";
        int position = -1;
        BarIcon icon;
        GlowTile button;
        boolean focused;
    }

    private static class Room {
        String label;
        BarIcon.Kind kind;
        final List<Device> devices = new ArrayList<>();
        GlowTile button;
        View dot;
        boolean focused;
    }

    private HomeBar(Context context, float dp, Focus focus) {
        this.context = context;
        this.dp = dp;
        this.focus = focus;
    }

    /**
     * The bar for this install, or null when assets/homebar.properties is missing or incomplete. `devices=` lists
     * name|label|icon entries; `rooms=` lists label|icon|name,name,... entries, both separated by ";".
     */
    static HomeBar load(Context context, float dp, Focus focus) {
        Properties p = new Properties();
        try (InputStream in = context.getAssets().open("homebar.properties")) {
            p.load(in);
        } catch (Exception e) {
            return null;
        }
        String host = p.getProperty("host");
        String devices = p.getProperty("devices");
        String rooms = p.getProperty("rooms");
        if (host == null || devices == null || rooms == null) {
            return null;
        }
        HomeBar bar = new HomeBar(context, dp, focus);
        for (String entry : devices.split(";")) {
            String[] f = entry.trim().split("\\|");
            if (f.length >= 3) {
                Device d = new Device();
                d.name = f[0];
                d.label = f[1];
                d.kind = kind(f[2]);
                bar.byName.put(d.name, d);
            }
        }
        for (String entry : rooms.split(";")) {
            String[] f = entry.trim().split("\\|");
            if (f.length < 3) {
                continue;
            }
            Room r = new Room();
            r.label = f[0];
            r.kind = kind(f[1]);
            for (String name : f[2].split(",")) {
                Device d = bar.byName.get(name.trim());
                if (d != null) {
                    d.room = r;
                    r.devices.add(d);
                }
            }
            if (!r.devices.isEmpty()) {
                bar.rooms.add(r);
            }
        }
        bar.mqtt = new Mqtt(host, Integer.parseInt(p.getProperty("port", "1883")), p.getProperty("user", ""),
                p.getProperty("password", ""), "firetv/state/+", bar.listener());
        return bar.rooms.isEmpty() ? null : bar;
    }

    private static BarIcon.Kind kind(String name) {
        switch (name) {
            case "ceiling": return BarIcon.Kind.CEILING;
            case "desk": return BarIcon.Kind.DESK;
            case "bed":
            case "bedside": return BarIcon.Kind.BED;
            case "fan": return BarIcon.Kind.FAN;
            case "blind": return BarIcon.Kind.BLIND;
            case "curtain": return BarIcon.Kind.CURTAIN;
            case "sofa": return BarIcon.Kind.SOFA;
            case "kitchen": return BarIcon.Kind.KITCHEN;
            case "bath": return BarIcon.Kind.BATH;
            case "door": return BarIcon.Kind.DOOR;
            default: return BarIcon.Kind.LIGHT;
        }
    }

    /** The room buttons, for the bottom row. */
    View view() {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        for (Room r : rooms) {
            r.button = circle(new BarIcon(r.kind));
            r.button.setId(View.generateViewId());
            ((BarIcon) ((ImageView) r.button.getChildAt(0)).getDrawable()).set(false, 0f, 0xFF4A3300);
            r.dot = new View(context);
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(0xFFFFC857);
            dot.setStroke(Math.max(1, px(1)), 0xFF1A2236);
            r.dot.setBackground(dot);
            r.dot.setVisibility(View.INVISIBLE);
            FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(px(9), px(9), Gravity.TOP | Gravity.END);
            dlp.setMargins(0, px(1), px(1), 0);
            r.button.addView(r.dot, dlp);
            r.button.setOnClickListener(v -> openRoom(r));
            r.button.setOnFocusChangeListener((v, has) -> {
                r.focused = has;
                v.animate().scaleX(has ? 1.15f : 1f).scaleY(has ? 1.15f : 1f).setDuration(150).start();
                paint(r);
                if (has) {
                    describe(r);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(SIZE_DP), px(SIZE_DP));
            lp.setMargins(px(5), 0, px(5), 0);
            row.addView(r.button, lp);
            paint(r);
        }
        return row;
    }

    /** The glass panel a room's devices open in; the activity places it above the bottom row. Hidden until used. */
    View panel() {
        panel = new LinearLayout(context);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        int pad = px(10);
        panel.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xF0182338, 0xF00B1222});
        bg.setCornerRadius(px(SIZE_DP / 2 + 10));
        bg.setStroke(Math.max(1, px(1)), 0x4DFFFFFF);
        panel.setBackground(bg);
        panel.setElevation(px(24));   // above the app tiles, which lift when focused
        panel.setVisibility(View.GONE);
        // leaving the panel with the remote (up to the apps, down past it) closes it
        panel.getViewTreeObserver().addOnGlobalFocusChangeListener((was, now) -> {
            if (open != null && now != null && !isInside(now, panel)) {
                closeRoom(false);
            }
        });
        return panel;
    }

    /** A view to dim while a room's panel is open (the app row it covers). */
    void dimWhileOpen(View view) {
        behind = view;
    }

    /** Back pressed: closes an open room and returns true, or returns false when there was nothing to close. */
    boolean back() {
        if (open == null) {
            return false;
        }
        closeRoom(true);
        return true;
    }

    void start() {
        mqtt.start();
    }

    void stop() {
        mqtt.stop();
    }

    private void openRoom(Room r) {
        if (open == r) {
            closeRoom(true);
            return;
        }
        panel.removeAllViews();
        for (Device d : r.devices) {
            if (d.button == null) {
                d.icon = new BarIcon(d.kind);
                d.button = circle(d.icon);
                d.button.setOnClickListener(v -> mqtt.publish("firetv/cmd/" + d.name, "toggle"));
                d.button.setOnFocusChangeListener((v, has) -> {
                    d.focused = has;
                    v.animate().scaleX(has ? 1.15f : 1f).scaleY(has ? 1.15f : 1f).setDuration(150).start();
                    paint(d);
                    if (has) {
                        describe(d);
                    }
                });
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(SIZE_DP), px(SIZE_DP));
            lp.setMargins(px(6), 0, px(6), 0);
            panel.addView(d.button, lp);
            paint(d);
        }
        // pin left/right to the neighbouring buttons: Android's nearest-in-direction search would otherwise pick a
        // dimmed app tile behind the panel, focus would leave it, and the panel would close
        for (int i = 0; i < r.devices.size(); i++) {
            GlowTile button = r.devices.get(i).button;
            if (button.getId() == View.NO_ID) {
                button.setId(View.generateViewId());
            }
        }
        for (int i = 0; i < r.devices.size(); i++) {
            GlowTile button = r.devices.get(i).button;
            button.setNextFocusLeftId(r.devices.get(Math.max(0, i - 1)).button.getId());
            button.setNextFocusRightId(r.devices.get(Math.min(r.devices.size() - 1, i + 1)).button.getId());
            button.setNextFocusDownId(r.button.getId() == View.NO_ID ? View.NO_ID : r.button.getId());
        }
        open = r;
        if (behind != null) {
            behind.animate().alpha(0.3f).setDuration(180).start();
        }
        panel.setVisibility(View.VISIBLE);
        panel.setAlpha(0f);
        panel.setTranslationY(px(12));
        panel.animate().alpha(1f).translationY(0).setDuration(180).start();
        r.devices.get(0).button.requestFocus();
    }

    private void closeRoom(boolean refocus) {
        Room r = open;
        open = null;
        if (behind != null) {
            behind.animate().alpha(1f).setDuration(180).start();
        }
        panel.setVisibility(View.GONE);
        panel.removeAllViews();
        if (r != null && refocus) {
            r.button.requestFocus();
        }
    }

    private GlowTile circle(BarIcon icon) {
        GlowTile button = new GlowTile(context, 0xFFFFC857, px(8), px(SIZE_DP / 2));
        ImageView image = new ImageView(context);
        image.setImageDrawable(icon);
        int pad = px(7);
        image.setPadding(pad, pad, pad, pad);
        button.addView(image, new FrameLayout.LayoutParams(px(SIZE_DP), px(SIZE_DP)));
        button.setFocusable(true);
        button.setClipChildren(false);
        return button;
    }

    private static boolean isInside(View v, View parent) {
        for (Object p = v; p instanceof View; p = ((View) p).getParent()) {
            if (p == parent) {
                return true;
            }
        }
        return false;
    }

    private Mqtt.Listener listener() {
        return new Mqtt.Listener() {
            @Override
            public void connected() {
                main.post(() -> {
                    online = true;
                    for (Room r : rooms) {
                        paint(r);
                    }
                });
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
                    if (d.room != null) {
                        paint(d.room);
                        if (d.room.focused) {
                            describe(d.room);
                        }
                    }
                    if (d.focused) {
                        describe(d);
                    }
                });
            }

            @Override
            public void disconnected() {
                main.post(() -> {
                    online = false;
                    for (Room r : rooms) {
                        paint(r);
                        for (Device d : r.devices) {
                            paint(d);
                        }
                    }
                });
            }
        };
    }

    private boolean active(Device d) {
        if ((d.kind == BarIcon.Kind.BLIND || d.kind == BarIcon.Kind.CURTAIN) && d.position >= 0) {
            // trust the position, not the state: a group of covers reports "open" as soon as one member sits at
            // 1 %, so closed blackouts would show as open. Same 2 % rule as the HA automation.
            return d.position > 2;
        }
        return d.state.equals("on") || d.state.equals("open") || d.state.equals("opening");
    }

    private int activeCount(Room r) {
        int n = 0;
        for (Device d : r.devices) {
            if (online && active(d)) {
                n++;
            }
        }
        return n;
    }

    private GradientDrawable glass(boolean on, boolean focused) {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                on ? new int[] {0xFFFFE08A, 0xFFFFC23D}
                        : focused ? new int[] {0x66FFFFFF, 0x33FFFFFF} : new int[] {0x33FFFFFF, 0x17FFFFFF});
        bg.setCornerRadius(px(SIZE_DP) / 2f);
        bg.setStroke(Math.max(1, px(focused ? 2 : 1)), focused ? 0xF2FFFFFF : on ? 0x00000000 : 0x40FFFFFF);
        return bg;
    }

    private void paint(Room r) {
        if (r.button == null) {
            return;
        }
        r.button.setBackground(glass(false, r.focused || open == r));
        r.dot.setVisibility(activeCount(r) > 0 ? View.VISIBLE : View.INVISIBLE);
        r.button.setAlpha(online ? 1f : 0.45f);
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
        d.button.setBackground(glass(on, d.focused));
        d.button.setAlpha(usable ? 1f : 0.45f);
        d.button.setGlowing(on);
    }

    private void describe(Room r) {
        String what;
        if (!online) {
            what = "Not connected to Home Assistant.";
        } else {
            int n = activeCount(r);
            what = (n == 0 ? "Everything off" : n + " on") + " · press select to show controls.";
        }
        focus.show("ROOMS", r.label, what);
    }

    private void describe(Device d) {
        String what;
        if (!online) {
            what = "Not connected to Home Assistant.";
        } else if (d.state.equals("unavailable") || d.state.equals("unknown")) {
            what = "Unavailable right now.";
        } else if (d.kind == BarIcon.Kind.BLIND || d.kind == BarIcon.Kind.CURTAIN) {
            String now = d.position > 2 && d.position < 98 ? d.position + "% open" : active(d) ? "Open" : "Closed";
            what = now + " · press select to " + (active(d) ? "close" : "open") + ".";
        } else {
            what = (active(d) ? "On" : "Off") + " · press select to turn " + (active(d) ? "off" : "on") + ".";
        }
        focus.show(d.room != null ? d.room.label.toUpperCase() : "", d.label, what);
    }

    private int px(int v) {
        return Math.round(v * dp);
    }
}
