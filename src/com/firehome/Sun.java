package com.firehome;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Which part of the day it is, from sunrise and sunset. Open-Meteo's times (fetched with the weather) are used when
 * there are some for today; otherwise they are worked out on the device (the standard almanac approximation, within
 * a minute of Open-Meteo), so the wallpaper still follows the day offline. Same rounded coordinates as Weather.
 */
final class Sun {
    private static final double LATITUDE = 52.52;
    private static final double LONGITUDE = 13.40;
    private static final double ZENITH = 90.833;   // the sun's upper edge on the horizon, refraction included

    private Sun() {
    }

    /** Which part of the day it is now: morning around sunrise, evening around sunset, night and day in between. */
    static SkyBackground.Phase phase(Calendar now) {
        return phase(now, -1, -1);
    }

    /** As phase(now), with sunrise/sunset minutes from elsewhere (Open-Meteo); -1 means work it out here. */
    static SkyBackground.Phase phase(Calendar now, int rise, int set) {
        int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (rise < 0 || set < 0) {
            rise = minuteOfDay(now, true);
            set = minuteOfDay(now, false);
        }
        if (minute >= rise - 45 && minute < rise + 90) {
            return SkyBackground.Phase.MORNING;
        }
        if (minute >= rise + 90 && minute < set - 60) {
            return SkyBackground.Phase.DAY;
        }
        if (minute >= set - 60 && minute < set + 45) {
            return SkyBackground.Phase.EVENING;
        }
        return SkyBackground.Phase.NIGHT;
    }

    /** Local minute of the day of sunrise (or sunset) on the day of `day`. */
    static int minuteOfDay(Calendar day, boolean rising) {
        int n = day.get(Calendar.DAY_OF_YEAR);
        double lngHour = LONGITUDE / 15;
        double t = n + ((rising ? 6 : 18) - lngHour) / 24;
        double m = 0.9856 * t - 3.289;
        double l = norm(m + 1.916 * sin(m) + 0.020 * sin(2 * m) + 282.634, 360);
        double ra = norm(Math.toDegrees(Math.atan(0.91764 * Math.tan(Math.toRadians(l)))), 360);
        ra = (ra + (Math.floor(l / 90) * 90 - Math.floor(ra / 90) * 90)) / 15;
        double sinDec = 0.39782 * sin(l);
        double cosDec = Math.cos(Math.asin(sinDec));
        double cosH = (cos(ZENITH) - sinDec * sin(LATITUDE)) / (cosDec * cos(LATITUDE));
        cosH = Math.max(-1, Math.min(1, cosH));
        double h = Math.toDegrees(Math.acos(cosH));
        h = (rising ? 360 - h : h) / 15;
        double ut = norm(h + ra - 0.06571 * t - 6.622 - lngHour, 24);
        TimeZone zone = day.getTimeZone();
        double local = norm(ut + zone.getOffset(day.getTimeInMillis()) / 3_600_000.0, 24);
        return (int) Math.round(local * 60);
    }

    private static double sin(double degrees) {
        return Math.sin(Math.toRadians(degrees));
    }

    private static double cos(double degrees) {
        return Math.cos(Math.toRadians(degrees));
    }

    private static double norm(double v, double range) {
        return ((v % range) + range) % range;
    }
}
