package app.framedash;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.widget.Toast;

import java.util.Locale;

/**
 * The three desktops, left to right: the left app (Fotoo by default), the dashboard, and art / photos.
 * Left/right gestures move between them; walking up returns to the dashboard unless someone
 * has been using the frame within the last few minutes.
 */
final class Desks {
    private Desks() {}

    static final int LEFT_APP = 0, DASHBOARD = 1, ART = 2;

    /** Which desktop is showing (kept up to date by the screens and the gesture service). */
    static volatile int current = DASHBOARD;
    /** Last time a person chose a desktop or used a gesture/button/touch. */
    static volatile long lastHuman;
    /** True when art appeared by itself after idle time (then walking up always wakes the dashboard). */
    static volatile boolean artFromIdle;

    static void human() { lastHuman = System.currentTimeMillis(); }

    /** Minutes of no use after which walking up brings back the dashboard. */
    static long quietMs(Context c) {
        return Math.max(1, Prefs.artIdleMin(c) > 0 ? Prefs.artIdleMin(c) : 1) * 60_000L;
    }

    /** Should walking up return to the dashboard now? */
    static boolean presenceShouldReturn(Context c) {
        if (current == DASHBOARD) return false;
        if (current == ART && artFromIdle) return true;
        return System.currentTimeMillis() - lastHuman > quietMs(c);
    }

    static void left(Context c) {
        human();
        if (current == ART) show(c, DASHBOARD);
        else if (current == DASHBOARD) show(c, LEFT_APP);
    }

    static void right(Context c) {
        human();
        if (current == LEFT_APP) show(c, DASHBOARD);
        else if (current == DASHBOARD) show(c, ART);
    }

    static void show(Context c, int desk) {
        switch (desk) {
            case LEFT_APP: {
                String pkg = leftPackage(c);
                Intent i = pkg.isEmpty() ? null : c.getPackageManager().getLaunchIntentForPackage(pkg);
                if (i == null) {
                    Toast.makeText(c, "No left desktop app. Choose one in Settings → Desktops.", Toast.LENGTH_LONG).show();
                    return;
                }
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                current = LEFT_APP;
                Ui.start(c, i);
                break;
            }
            case ART:
                artFromIdle = false;
                current = ART;
                c.startActivity(new Intent(c, ArtActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
            default:
                current = DASHBOARD;
                c.startActivity(new Intent(c, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
    }

    /** The app on the left desktop: the one chosen in Settings, else Fotoo if it is installed. */
    static String leftPackage(Context c) {
        String set = Prefs.raw(c).getString("left_app", "");
        if (!set.isEmpty()) return set;
        PackageManager pm = c.getPackageManager();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            String n = ai.packageName.toLowerCase(Locale.ROOT);
            CharSequence l = ai.loadLabel(pm);
            String label = l == null ? "" : l.toString().toLowerCase(Locale.ROOT);
            if ((label.contains("fotoo") || n.contains("fotoo")) && pm.getLaunchIntentForPackage(ai.packageName) != null) {
                return ai.packageName;
            }
        }
        return "";
    }

    static String leftLabel(Context c) {
        String pkg = leftPackage(c);
        if (pkg.isEmpty()) return "none";
        try {
            PackageManager pm = c.getPackageManager();
            return String.valueOf(pm.getApplicationInfo(pkg, 0).loadLabel(pm));
        } catch (Exception e) {
            return pkg;
        }
    }
}
