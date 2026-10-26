package com.firehome;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.ImageView;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Fire OS sends Home to com.amazon.tv.launcher whatever else is installed, and silently drops any accessibility
 * service ADB tries to switch on. It does let ADB grant READ_LOGS, and ActivityManager logs every Home press as
 * "START u0 {... cat=[android.intent.category.HOME] ... cmp=com.amazon.tv.launcher/...}". This service follows
 * the log and puts HomeActivity on top each time.
 *
 * After a Home press Android holds back activity starts from apps for 5 seconds, so HomeActivity only arrives
 * then. Windows aren't held back: an overlay with HomeActivity's last picture of itself covers Amazon's home at
 * once and goes when HomeActivity is back (or after OVERLAY_MAX_MS, so it can never trap the screen).
 * The overlay needs SYSTEM_ALERT_WINDOW, granted over ADB: appops set com.firehome SYSTEM_ALERT_WINDOW allow.
 */
public class HomeWatcher extends Service {
    private static long pausedUntil;
    private static long lastLaunch;
    private static final long OVERLAY_MAX_MS = 8000L;
    private static HomeWatcher running;
    private static Bitmap snapshot;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable hide = this::removeOverlay;
    private Thread reader;
    private ImageView overlay;

    /** Leave Amazon's home alone for a while, for the "Fire TV home" button. */
    static void pause(long millis) {
        pausedUntil = SystemClock.elapsedRealtime() + millis;
    }

    /** HomeActivity's picture of itself as it was left, for the overlay. */
    static void snapshot(Bitmap picture) {
        snapshot = picture;
    }

    /** HomeActivity is on screen again: the overlay can go. */
    static void hideOverlay() {
        HomeWatcher watcher = running;
        if (watcher != null) {
            watcher.main.post(watcher.hide);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        running = this;
        if (reader == null) {
            // foreground, so the low-memory killer on a 1 GB stick leaves the watcher running
            startForeground(1, new Notification.Builder(this)
                    .setSmallIcon(R.drawable.icon)
                    .setContentTitle(getString(R.string.app_name))
                    .setPriority(Notification.PRIORITY_MIN)
                    .build());
            reader = new Thread(this::follow, "home-watcher");
            reader.setDaemon(true);
            reader.start();
        }
        return START_STICKY;
    }

    private void follow() {
        catchUp();
        while (true) {
            Process logcat = null;
            try {
                // -T 1 starts at the newest line, so Home presses already in the buffer are not replayed
                logcat = new ProcessBuilder("logcat", "-v", "brief", "-T", "1", "ActivityManager:I", "*:S")
                        .redirectErrorStream(true).start();
                BufferedReader lines = new BufferedReader(new InputStreamReader(logcat.getInputStream()));
                for (String line; (line = lines.readLine()) != null; ) {
                    if (isAmazonHome(line)) {
                        amazonHomeStarted();
                    }
                }
            } catch (Exception e) {
                // logcat ended or could not start; try again below
            } finally {
                if (logcat != null) {
                    logcat.destroy();
                }
            }
            SystemClock.sleep(2000);
        }
    }

    /**
     * At boot, or when the system restarts this service, Amazon's home may already be on screen. Only take over
     * if it was the last thing started, so a video someone opened in the meantime is left alone.
     */
    private void catchUp() {
        String last = null;
        try {
            Process dump = new ProcessBuilder("logcat", "-d", "-v", "brief", "ActivityManager:I", "*:S")
                    .redirectErrorStream(true).start();
            BufferedReader lines = new BufferedReader(new InputStreamReader(dump.getInputStream()));
            for (String line; (line = lines.readLine()) != null; ) {
                if (line.contains("START u0")) {
                    last = line;
                }
            }
            dump.waitFor();
        } catch (Exception e) {
            return;
        }
        if (last != null && isAmazonHome(last)) {
            amazonHomeStarted();
        }
    }

    private static boolean isAmazonHome(String line) {
        return line.contains("android.intent.category.HOME")
                && line.contains("cmp=" + HomeActivity.AMAZON_HOME + "/");
    }

    private void amazonHomeStarted() {
        long now = SystemClock.elapsedRealtime();
        if (now < pausedUntil || now - lastLaunch < 1500) {
            return;
        }
        lastLaunch = now;
        main.post(this::showOverlay);
        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }

    private void showOverlay() {
        if (overlay != null || !Settings.canDrawOverlays(this)) {
            return;
        }
        ImageView cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.FIT_XY);
        if (snapshot != null) {
            cover.setImageBitmap(snapshot);
        } else {
            cover.setBackground(new SkyBackground());   // nothing to show yet (first Home since boot)
        }
        // focusable, so remote presses land here instead of on Amazon's home underneath; Back lets go early
        cover.setFocusable(true);
        cover.setFocusableInTouchMode(true);
        cover.setOnKeyListener((v, code, event) -> {
            if (code == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                removeOverlay();
            }
            return true;
        });
        @SuppressWarnings("deprecation")
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(cover, lp);
        } catch (RuntimeException e) {
            return;   // permission withdrawn or the window refused: HomeActivity still comes, just later
        }
        overlay = cover;
        cover.requestFocus();
        main.postDelayed(hide, OVERLAY_MAX_MS);
    }

    private void removeOverlay() {
        main.removeCallbacks(hide);
        if (overlay != null) {
            try {
                ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(overlay);
            } catch (RuntimeException e) {
                // already gone
            }
            overlay = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
