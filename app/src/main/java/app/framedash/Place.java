package app.framedash;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Home location (typed city or found from the internet address) and a small HTTP helper. */
final class Place {
    private Place() {}

    private static OkHttpClient http;

    private static synchronized OkHttpClient http() {
        if (http == null) {
            http = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(45, TimeUnit.SECONDS)
                    .build();
        }
        return http;
    }

    // ------------------------------------------------------------------ home location

    /** "City, Country" for instructions; looks it up once from the internet address if not set. */
    static String homeName(Context c) {
        String set = Prefs.homeCity(c);
        if (!set.isEmpty()) return set;
        return Prefs.raw(c).getString("home_auto_name", "");
    }

    /** {lat, lon, name} for the home location, or null. */
    static Object[] home(Context c) throws Exception {
        String set = Prefs.homeCity(c);
        if (!set.isEmpty()) {
            Object[] g = geocode(set);
            if (g != null) return g;
            throw new java.io.IOException("couldn't find the city \"" + set + "\". Check the spelling in Settings");
        }
        android.content.SharedPreferences p = Prefs.raw(c);
        if (p.contains("home_auto_lat")) {
            return new Object[]{(double) p.getFloat("home_auto_lat", 0), (double) p.getFloat("home_auto_lon", 0),
                    p.getString("home_auto_name", "")};
        }
        // Work out the city from the internet address; free services are flaky, so try several.
        String[][] services = {
                {"https://ipwho.is/", "country"},
                {"https://get.geojs.io/v1/ip/geo.json", "country"},
                {"https://ipapi.co/json/", "country_name"},
        };
        StringBuilder errors = new StringBuilder();
        for (String[] svc : services) {
            try {
                JSONObject j = new JSONObject(get(svc[0]));
                double lat = Double.parseDouble(j.optString("latitude", "x"));
                double lon = Double.parseDouble(j.optString("longitude", "x"));
                String nm = j.optString("city") + ", " + j.optString(svc[1]);
                p.edit().putFloat("home_auto_lat", (float) lat).putFloat("home_auto_lon", (float) lon)
                        .putString("home_auto_name", nm).apply();
                return new Object[]{lat, lon, nm};
            } catch (Exception e) {
                errors.append(errors.length() == 0 ? "" : ", ").append(Uri.parse(svc[0]).getHost())
                        .append(": ").append(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
        }
        throw new java.io.IOException("couldn't find your location automatically (" + errors
                + "). Type your city in Settings → Your city");
    }

    /** Looks up the home location in the background so weather is ready quickly. */
    static void warmUp(Context c) {
        if (!homeName(c).isEmpty()) return;
        final Context app = c.getApplicationContext();
        new Thread(() -> { try { home(app); } catch (Exception ignored) {} }, "home-location").start();
    }

    static Object[] geocode(String place) throws Exception {
        JSONObject j = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&name="
                + URLEncoder.encode(place.split(",")[0].trim(), "UTF-8")));
        JSONArray r = j.optJSONArray("results");
        if (r == null || r.length() == 0) return null;
        JSONObject f = r.getJSONObject(0);
        return new Object[]{f.getDouble("latitude"), f.getDouble("longitude"),
                f.optString("name") + ", " + f.optString("country")};
    }

    static String describe(int code) {
        if (code == 0) return "clear";
        if (code <= 2) return "partly cloudy";
        if (code == 3) return "overcast";
        if (code <= 48) return "fog";
        if (code <= 57) return "drizzle";
        if (code <= 67) return "rain";
        if (code <= 77) return "snow";
        if (code <= 82) return "rain showers";
        if (code <= 86) return "snow showers";
        return "thunderstorms";
    }

    static String get(String url) throws Exception {
        try (Response r = http().newCall(new Request.Builder().url(url).build()).execute()) {
            if (!r.isSuccessful()) throw new java.io.IOException("HTTP " + r.code());
            return r.body() == null ? "" : r.body().string();
        }
    }
}
