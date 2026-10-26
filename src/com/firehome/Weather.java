package com.firehome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The current weather from Open-Meteo (free, no account or key) for central Berlin (rounded on purpose; set your own
 * coordinates here). The last answer is kept in preferences, so the home screen has something to show straight after a
 * reboot or while the network is down; on any failure the old answer simply stays.
 */
class Weather {
    private static final String URL = "https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.40"
            + "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min"
            + "&timezone=Europe%2FBerlin&forecast_days=1";
    static final long REFRESH_MS = 15 * 60 * 1000L;
    private static final String PREFS = "weather";

    final double temperature;
    final double high;
    final double low;
    final int code;
    final boolean day;
    final long fetched;

    private Weather(JSONObject json, long fetched) throws org.json.JSONException {
        JSONObject current = json.getJSONObject("current");
        JSONObject daily = json.getJSONObject("daily");
        temperature = current.getDouble("temperature_2m");
        code = current.getInt("weather_code");
        day = current.getInt("is_day") == 1;
        high = daily.getJSONArray("temperature_2m_max").getDouble(0);
        low = daily.getJSONArray("temperature_2m_min").getDouble(0);
        this.fetched = fetched;
    }

    /** The last answer saved, or null. */
    static Weather saved(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            String json = prefs.getString("json", null);
            return json == null ? null : new Weather(new JSONObject(json), prefs.getLong("fetched", 0));
        } catch (Exception e) {
            return null;
        }
    }

    /** Asks Open-Meteo; blocks, so call it off the main thread. Returns null on any failure. */
    static Weather fetch(Context context) {
        HttpURLConnection http = null;
        try {
            http = (HttpURLConnection) new URL(URL).openConnection();
            http.setConnectTimeout(10000);
            http.setReadTimeout(10000);
            if (http.getResponseCode() != 200) {
                return null;
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(http.getInputStream(), "UTF-8"))) {
                for (String line; (line = in.readLine()) != null; ) {
                    body.append(line);
                }
            }
            long now = System.currentTimeMillis();
            Weather weather = new Weather(new JSONObject(body.toString()), now);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("json", body.toString()).putLong("fetched", now).apply();
            return weather;
        } catch (Exception e) {
            return null;
        } finally {
            if (http != null) {
                http.disconnect();
            }
        }
    }

    /** WMO weather code as a few words. */
    String condition() {
        if (code == 0) return day ? "Sunny" : "Clear";
        if (code == 1) return "Mainly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code == 66 || code == 67) return "Freezing rain";
        if (code >= 61 && code <= 65) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "";
    }

    WeatherIcon.Kind kind() {
        if (code == 0) return WeatherIcon.Kind.CLEAR;
        if (code == 1 || code == 2) return WeatherIcon.Kind.PARTLY;
        if (code == 3) return WeatherIcon.Kind.CLOUDY;
        if (code == 45 || code == 48) return WeatherIcon.Kind.FOG;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return WeatherIcon.Kind.SNOW;
        if (code >= 95) return WeatherIcon.Kind.THUNDER;
        if (code >= 51) return WeatherIcon.Kind.RAIN;
        return WeatherIcon.Kind.CLOUDY;
    }

    static String degrees(double value) {
        return Math.round(value) + "°";
    }
}
