package app.framedash;

import android.content.Context;
import android.content.SharedPreferences;

/** App settings, stored privately on this frame (never in the APK or on GitHub). */
final class Prefs {
    private Prefs() {}

    static final int[] UI_WIDTHS = {480, 540, 600, 660};

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("fridgedash", Context.MODE_PRIVATE);
    }

    static boolean dailyWallpaper(Context c) { return p(c).getBoolean("daily_wallpaper", true); }
    static void setDailyWallpaper(Context c, boolean v) { p(c).edit().putBoolean("daily_wallpaper", v).apply(); }

    /** Width of the virtual layout canvas in dp; smaller = everything bigger. */
    static int uiWidthDp(Context c) { return p(c).getInt("ui_width_dp", 540); }
    static void setUiWidthDp(Context c, int v) { p(c).edit().putInt("ui_width_dp", v).apply(); }

    /** Where the daily brief JSON lives, and the read-only key for it (GitHub fine-grained token). */
    static String briefUrl(Context c) { return p(c).getString("brief_url", "").trim(); }
    static void setBriefUrl(Context c, String v) { p(c).edit().putString("brief_url", v == null ? "" : v.trim()).apply(); }
    static String briefToken(Context c) { return p(c).getString("brief_token", "").trim(); }
    static void setBriefToken(Context c, String v) { p(c).edit().putString("brief_token", v == null ? "" : v.trim()).apply(); }

    /** Notion internal-integration key and the "Daily Brief" database id. */
    static String notionToken(Context c) { return p(c).getString("notion_token", "").trim(); }
    static void setNotionToken(Context c, String v) { p(c).edit().putString("notion_token", v == null ? "" : v.trim()).apply(); }
    static String notionDb(Context c) { return p(c).getString("notion_db", "").trim(); }
    static void setNotionDb(Context c, String v) { p(c).edit().putString("notion_db", v == null ? "" : v.trim()).apply(); }

    /** Home city for weather and local answers; empty = worked out from the internet address. */
    static String homeCity(Context c) { return p(c).getString("home_city", "").trim(); }
    static void setHomeCity(Context c, String v) { p(c).edit().putString("home_city", v == null ? "" : v.trim()).apply(); }

    /** Art mode: minutes of no activity before paintings appear (0 = off), and how often they change. */
    static final int[] ART_IDLE = {0, 1, 2, 5, 10, 30};
    static final int[] ART_INTERVAL = {1, 5, 15, 60};
    static int artIdleMin(Context c) { return p(c).getInt("art_idle_min", 0); }
    static void setArtIdleMin(Context c, int v) { p(c).edit().putInt("art_idle_min", v).apply(); }
    static int artIntervalMin(Context c) { return p(c).getInt("art_interval_min", 5); }
    static void setArtIntervalMin(Context c, int v) { p(c).edit().putInt("art_interval_min", v).apply(); }

    /** "auto" (follow the device), "portrait" or "landscape": for frames that don't rotate on their own. */
    static String orientation(Context c) { return p(c).getString("orientation", "auto"); }
    static void setOrientation(Context c, String v) { p(c).edit().putString("orientation", v).apply(); }

    /** Google Photos shared-album links, one per line. */
    static String photoAlbums(Context c) { return p(c).getString("photo_albums", "").trim(); }
    static void setPhotoAlbums(Context c, String v) { p(c).edit().putString("photo_albums", v == null ? "" : v.trim()).apply(); }

    /** What art mode shows: "paintings" or an album's share link. */
    static String artCollection(Context c) { return p(c).getString("art_collection", ""); }
    static void setArtCollection(Context c, String v) { p(c).edit().putString("art_collection", v).apply(); }

    static SharedPreferences raw(Context c) { return p(c); }
}
