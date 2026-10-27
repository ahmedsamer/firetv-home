package com.firehome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Today's weather from Open-Meteo (free, no account or key) for central Berlin (rounded on purpose; set your own
 * coordinates here). Its sunrise and sunset drive the wallpaper (see Sun); the corner shows the indoor climate from
 * Home Assistant, with the outdoor sky and how much colder or warmer it is outside next to it. The last answer is kept in preferences; on any failure it simply stays.
 */
class Weather {
    private static final String URL = "https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.40"
            + "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset"
            + "&timezone=Europe%2FBerlin&forecast_days=1";
    static final long REFRESH_MS = 15 * 60 * 1000L;
    private static final String PREFS = "weather";

    final double temperature;
    final double high;
    final double low;
    final int code;
    final boolean day;
    final long fetched;
    // today's sunrise and sunset as "yyyy-MM-ddTHH:mm" local time, or null from an older saved answer
    final String sunrise;
    final String sunset;

    private Weather(JSONObject json, long fetched) throws org.json.JSONException {
        JSONObject current = json.getJSONObject("current");
        JSONObject daily = json.getJSONObject("daily");
        temperature = current.getDouble("temperature_2m");
        code = current.getInt("weather_code");
        day = current.getInt("is_day") == 1;
        high = daily.getJSONArray("temperature_2m_max").getDouble(0);
        low = daily.getJSONArray("temperature_2m_min").getDouble(0);
        sunrise = daily.has("sunrise") ? daily.getJSONArray("sunrise").getString(0) : null;
        sunset = daily.has("sunset") ? daily.getJSONArray("sunset").getString(0) : null;
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

    /** Minute of the day of a "yyyy-MM-ddTHH:mm" time if it falls on `today` ("yyyy-MM-dd"), else -1. */
    static int minuteOn(String time, String today) {
        if (time == null || time.length() < 16 || !time.startsWith(today)) {
            return -1;
        }
        return Integer.parseInt(time.substring(11, 13)) * 60 + Integer.parseInt(time.substring(14, 16));
    }
}
