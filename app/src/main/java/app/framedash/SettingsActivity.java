package app.framedash;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** FrameDash settings. Everything here is stored only on this frame. */
public class SettingsActivity extends BaseActivity {

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private LinearLayout col;
    private EditText cityField;
    private EditText notionField;
    private TextView notionStatus;
    private TextView artIdleButton, artIntervalButton, orientButton, albumsStatus, collectionButton;
    private EditText albumsField;
    private TextView wallButton, sizeButton, updateStatus;
    private UpdateFlow updates;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.bg));
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        col.setPadding(pad, pad, pad, pad * 2);
        scroll.addView(col);
        setContentView(scroll);

        LinearLayout top = row();
        TextView title = text("Settings", 22, true);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(button("Done", v -> finish(), false), wrap());
        col.addView(top);

        // ---------------------------------------------------------- Weather
        section("Weather");
        col.addView(text("Your city", 12, false));
        cityField = new EditText(this);
        cityField.setSingleLine(true);
        cityField.setHint(Place.homeName(this).isEmpty() ? "e.g. Belgrade" : "Automatic: " + Place.homeName(this));
        cityField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        cityField.setTextColor(getColor(R.color.text));
        cityField.setHintTextColor(getColor(R.color.text_dim));
        cityField.setText(Prefs.homeCity(this));
        col.addView(cityField);
        col.addView(note("Leave empty to work it out automatically from the internet address."));

        // ---------------------------------------------------------- Daily brief
        section("Daily brief");
        col.addView(note("The frame shows the \"Daily Brief\" database from Notion, and ticking an item off "
                + "updates Notion (and so your phone and Grok). Paste the integration key (starts with ntn_), "
                + "then Connect."));
        notionField = new EditText(this);
        notionField.setSingleLine(true);
        notionField.setHint("ntn_…");
        notionField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        notionField.setTextColor(getColor(R.color.text));
        notionField.setHintTextColor(getColor(R.color.text_dim));
        notionField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        notionField.setTypeface(Typeface.MONOSPACE);
        notionField.setText(Prefs.notionToken(this));
        col.addView(notionField);
        LinearLayout notionRow = row();
        notionRow.addView(button("Paste key", v -> {
            String t = clipboardText();
            int at = t.indexOf("ntn_");
            if (at < 0) at = t.indexOf("secret_");
            if (at > 0) t = t.substring(at);
            int sp = t.indexOf(' ');
            if (sp > 0) t = t.substring(0, sp);
            notionField.setText(t.trim());
        }, true), weighted());
        notionRow.addView(button("Connect Notion", v -> connectNotion(), true), weighted());
        notionRow.addView(button("Disconnect", v -> {
            Prefs.setNotionDb(this, "");
            notionStatus.setText("Disconnected.");
        }, false), weighted());
        col.addView(notionRow);
        col.addView(button("Test Notion write (ticks & unticks one item)", v -> testNotionWrite(), false), full());
        notionStatus = note(Notion.configured(this) ? "✓ Connected to the Daily Brief database." : "Not connected.");
        col.addView(notionStatus);

        // ---------------------------------------------------------- Screen
        section("Screen");
        wallButton = button("", v -> {
            Prefs.setDailyWallpaper(this, !Prefs.dailyWallpaper(this));
            refreshWallpaperButton();
        }, false);
        col.addView(wallButton, full());
        refreshWallpaperButton();
        col.addView(button("Get today's photo now", v -> fetchPhotoNow(), false), full());

        orientButton = button("", v -> {
            String o = Prefs.orientation(this);
            Prefs.setOrientation(this, "auto".equals(o) ? "landscape" : "landscape".equals(o) ? "portrait" : "auto");
            refreshOrient();
            applyOrientation();
        }, false);
        col.addView(orientButton, full());
        refreshOrient();

        sizeButton = button("", v -> cycleSize(), false);
        col.addView(sizeButton, full());
        refreshSize();

        // ---------------------------------------------------------- Art mode
        section("Art mode");
        col.addView(note("When nobody uses the screen, famous paintings fill it (public-domain masterpieces from the "
                + "Art Institute of Chicago and the Met, a new one every few minutes). Touch, a key, the mouse or "
                + "someone coming close brings the dashboard back. Left/right arrow keys flip paintings. The screen stays on while this is enabled."));
        artIdleButton = button("", v -> {
            Prefs.setArtIdleMin(this, nextOf(Prefs.ART_IDLE, Prefs.artIdleMin(this)));
            refreshArt();
        }, false);
        col.addView(artIdleButton, full());
        artIntervalButton = button("", v -> {
            Prefs.setArtIntervalMin(this, nextOf(Prefs.ART_INTERVAL, Prefs.artIntervalMin(this)));
            refreshArt();
        }, false);
        col.addView(artIntervalButton, full());
        col.addView(text("Google Photos shared albums (one link per line)", 12, false));
        albumsField = new EditText(this);
        albumsField.setMinLines(2);
        albumsField.setMaxLines(6);
        albumsField.setHint("https://photos.app.goo.gl/…");
        albumsField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        albumsField.setTextColor(getColor(R.color.text));
        albumsField.setHintTextColor(getColor(R.color.text_dim));
        albumsField.setText(Prefs.photoAlbums(this));
        col.addView(albumsField);
        col.addView(note("In Google Photos: open the album → Share → Create link → copy. Paste it here (email it to "
                + "yourself and copy it in this device's browser). New photos in the album appear within a few hours. "
                + "Unofficial: Google could change their page and break this."));
        LinearLayout albumRow = row();
        albumRow.addView(button("Paste", v -> {
            String clip = clipboardText();
            if (clip.isEmpty()) return;
            String cur = albumsField.getText().toString().trim();
            albumsField.setText(cur.isEmpty() ? clip.trim() : cur + "\n" + clip.trim());
        }, false), weighted());
        albumRow.addView(button("Save & check albums", v -> checkAlbums(), true), weighted());
        col.addView(albumRow);
        albumsStatus = note("");
        col.addView(albumsStatus);
        collectionButton = button("", v -> {
            java.util.List<String> all = ArtActivity.collections(this);
            int i = all.indexOf(ArtActivity.currentCollection(this));
            Prefs.setArtCollection(this, all.get((i + 1) % all.size()));
            refreshArt();
        }, false);
        col.addView(collectionButton, full());
        col.addView(note("On the frame: ← → change the picture, ↑ ↓ switch between your albums and the paintings."));
        col.addView(button("Show art now", v -> startActivity(new Intent(this, ArtActivity.class)), true), full());
        col.addView(button("Gestures & buttons (teach what each one does)",
                v -> startActivity(new Intent(this, GestureSetupActivity.class)), true), full());
        col.addView(button("Sensor details (for troubleshooting)",
                v -> startActivity(new Intent(this, GestureTestActivity.class)), false), full());
        TextView wakeButton = button("", null, false);
        Runnable refreshWake = () -> wakeButton.setText(Prefs.artGesturesWake(this)
                ? "In art mode, gestures & buttons: show the dashboard"
                : "In art mode, gestures & buttons: change pictures / albums");
        wakeButton.setOnClickListener(v -> {
            Prefs.setArtGesturesWake(this, !Prefs.artGesturesWake(this));
            refreshWake.run();
        });
        refreshWake.run();
        col.addView(wakeButton, full());
        TextView shadowButton = button("", null, false);
        String[] shadowNames = {"off", "low", "medium", "high"};
        Runnable refreshShadow = () -> shadowButton.setText("Walk-up detection (light sensor): "
                + shadowNames[Prefs.shadowWake(this)]);
        shadowButton.setOnClickListener(v -> {
            Prefs.setShadowWake(this, (Prefs.shadowWake(this) + 1) % 4);
            refreshShadow.run();
        });
        refreshShadow.run();
        col.addView(shadowButton, full());
        col.addView(note("Notices the shadow of someone walking up, from further away than the proximity sensor. "
                + "Higher reacts sooner but may also react when a lamp is switched off."));
        String sensor = Presence.describe(this);
        int learned = Bindings.boundSensors(this).size();
        col.addView(note("Show the dashboard: someone coming close, any wave or any button ends art mode. "
                + "Change pictures: your gestures flip pictures and albums instead. Sensors watched: "
                + (sensor.isEmpty() ? "" : "proximity (" + sensor + ")")
                + (learned > 0 ? (sensor.isEmpty() ? "" : " + ") + learned + " gesture sensor(s)" : "")
                + (sensor.isEmpty() && learned == 0 ? "none yet (record a gesture first)" : "") + "."));
        refreshArt();

        // ---------------------------------------------------------- FrameDash
        section("FrameDash");
        TextView updateButton = button("", null, true);
        col.addView(updateButton, full());
        updateStatus = note("");
        updateStatus.setVisibility(View.GONE);
        col.addView(updateStatus);
        updates = new UpdateFlow(this, bg, updateStatus, null, updateButton);

        col.addView(button("Choose the Home app", v -> Ui.start(this,
                new Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)), false), full());
        col.addView(note("Pick \"FrameDash\" there so the frame starts on the dashboard."));
        col.addView(button("Diagnostics", v ->
                startActivity(new Intent(this, DiagnosticsActivity.class)), false), full());
        col.addView(button("All apps", v -> startActivity(new Intent(this, AppsActivity.class)), false), full());
        col.addView(button("Android settings", v -> Ui.start(this,
                new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)), false), full());
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Prefs.setPhotoAlbums(this, albumsField.getText().toString());
        Prefs.setHomeCity(this, cityField.getText().toString());
        Prefs.setNotionToken(this, notionField.getText().toString());
    }

    private String clipboardText() {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cb == null ? null : cb.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return "";
        CharSequence t = clip.getItemAt(0).coerceToText(this);
        return t == null ? "" : t.toString().trim();
    }

    private void testNotionWrite() {
        notionStatus.setText("Testing…");
        final Context app = getApplicationContext();
        bg.execute(() -> {
            StringBuilder sb = new StringBuilder();
            try {
                Brief b = Notion.fetch(app);
                Brief.Item target = null;
                for (Brief.Section sct : b.sections) for (Brief.Item it : sct.items)
                    if (target == null && !it.done) target = it;
                if (target == null) {
                    sb.append("No open item to test with.");
                } else {
                    sb.append("Item: ").append(target.title).append("\n");
                    Notion.setDone(app, target.pageId, true);
                    sb.append("✓ Ticked Done. ");
                    Brief after = Notion.fetch(app);
                    boolean seen = false;
                    for (Brief.Section sct : after.sections) for (Brief.Item it : sct.items)
                        if (it.pageId.equals(target.pageId) && it.done) seen = true;
                    sb.append(seen ? "Notion confirms it's Done. " : "But Notion still shows it open! ");
                    Notion.setDone(app, target.pageId, false);
                    sb.append("✓ Unticked again.");
                }
            } catch (Throwable e) {
                sb.append("✗ ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
            }
            final String m = sb.toString();
            main.post(() -> { if (!isDestroyed()) notionStatus.setText(m); });
        });
    }

    private void connectNotion() {
        final String token = notionField.getText().toString().trim();
        if (token.isEmpty()) {
            notionStatus.setText("Paste the Notion key first.");
            return;
        }
        Prefs.setNotionToken(this, token);
        notionStatus.setText("Connecting to Notion…");
        final Context app = getApplicationContext();
        bg.execute(() -> {
            String msg;
            try {
                Notion.ConnectResult r = Notion.connect(token);
                Notion.saveConnection(app, r);
                int imported = 0;
                if (r.created) {
                    Brief file = Brief.cached(app);
                    if (file != null && !file.live) imported = Notion.importBrief(token, r.dbId, file);
                }
                Brief b = Notion.fetch(app);
                int open = 0;
                for (Brief.Section sct : b.sections) for (Brief.Item it : sct.items) if (!it.done) open++;
                msg = "✓ Connected to \"" + r.dbTitle + "\""
                        + (r.created ? " (new, in \"" + r.parentTitle + "\""
                            + (imported > 0 ? ", imported " + imported + " items" : "") + ")" : "")
                        + ". " + open + " open items in " + b.sections.size() + " sections.\n"
                        + "Columns: " + r.cols.describe() + "."
                        + (r.addedDone ? "\nAdded a \"Done\" checkbox column; tell Grok to use it." : "");
            } catch (Exception e) {
                msg = "✗ " + e.getMessage();
            }
            final String m = msg;
            main.post(() -> { if (!isDestroyed()) notionStatus.setText(m); });
        });
    }

    protected void onDestroy() {
        super.onDestroy();
        bg.shutdownNow();
    }

    private void refreshOrient() {
        String o = Prefs.orientation(this);
        orientButton.setText("Screen orientation: " + ("landscape".equals(o) ? "Landscape" : "portrait".equals(o)
                ? "Portrait" : "Automatic") + "   (tap to change)");
    }

    private static int nextOf(int[] options, int cur) {
        for (int i = 0; i < options.length; i++) if (options[i] == cur) return options[(i + 1) % options.length];
        return options[0];
    }

    private void checkAlbums() {
        Prefs.setPhotoAlbums(this, albumsField.getText().toString());
        java.util.List<String> links = Photos.links(this);
        if (links.isEmpty()) {
            albumsStatus.setText(Prefs.photoAlbums(this).isEmpty() ? "No albums." 
                    : "✗ That doesn't look like a Google Photos share link (photos.app.goo.gl/… or photos.google.com/share/…).");
            refreshArt();
            return;
        }
        albumsStatus.setText("Checking " + links.size() + " album(s)…");
        final android.content.Context app = getApplicationContext();
        bg.execute(() -> {
            StringBuilder sb = new StringBuilder();
            for (String l : links) {
                try {
                    Photos.Album a = Photos.album(app, l, true);
                    sb.append("✓ ").append(a.name.isEmpty() ? "Album" : a.name).append(": ")
                            .append(a.items.size()).append(" photos\n");
                } catch (Exception e) {
                    sb.append("✗ ").append(l.length() > 40 ? l.substring(0, 40) + "…" : l).append(": ")
                            .append(e.getMessage()).append('\n');
                }
            }
            main.post(() -> {
                if (isDestroyed()) return;
                albumsStatus.setText(sb.toString().trim());
                refreshArt();
            });
        });
    }

    private void refreshArt() {
        int idle = Prefs.artIdleMin(this), every = Prefs.artIntervalMin(this);
        artIdleButton.setText("Show art when idle: " + (idle == 0 ? "Off" : "after " + idle + " min") + "   (tap to change)");
        artIntervalButton.setText("New picture every: " + every + " min   (tap to change)");
        collectionButton.setText("Showing: " + ArtActivity.collectionName(this, ArtActivity.currentCollection(this))
                + "   (tap to change)");
    }

    // ------------------------------------------------------------------ screen options

    private void refreshWallpaperButton() {
        wallButton.setText("Daily photo wallpaper (Bing): " + (Prefs.dailyWallpaper(this) ? "ON" : "OFF"));
    }

    private void fetchPhotoNow() {
        Toast.makeText(this, "Downloading today's photo…", Toast.LENGTH_SHORT).show();
        final Context app = getApplicationContext();
        bg.execute(() -> {
            String msg;
            try {
                Wallpaper.refresh(app);
                msg = "New photo saved. Go back to see it.";
            } catch (Exception e) {
                msg = "Couldn't get the photo: " + e.getMessage();
            }
            final String m = msg;
            main.post(() -> Toast.makeText(app, m, Toast.LENGTH_LONG).show());
        });
    }

    private void cycleSize() {
        int cur = Prefs.uiWidthDp(this);
        int next = Prefs.UI_WIDTHS[0];
        for (int i = 0; i < Prefs.UI_WIDTHS.length; i++) {
            if (Prefs.UI_WIDTHS[i] == cur) next = Prefs.UI_WIDTHS[(i + 1) % Prefs.UI_WIDTHS.length];
        }
        Prefs.setUiWidthDp(this, next);
        new AlertDialog.Builder(this)
                .setMessage("Screen size changed. FrameDash will restart to apply it.")
                .setPositiveButton("OK", (d, w) -> {
                    Intent i = new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                })
                .show();
        refreshSize();
    }

    private void refreshSize() {
        int w = Prefs.uiWidthDp(this);
        String label = w <= 480 ? "Largest" : w <= 540 ? "Large (default)" : w <= 600 ? "Medium" : "Small";
        sizeButton.setText("Text & button size: " + label + "   (tap to change)");
    }

    // ------------------------------------------------------------------ view helpers

    private void section(String s) {
        TextView t = text(s.toUpperCase(), 11, true);
        t.setTextColor(0xFFF5B14C);
        t.setLetterSpacing(0.1f);
        t.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 6));
        col.addView(t);
    }

    private TextView text(String s, float sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(getColor(R.color.text));
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView note(String s) {
        TextView t = text(s, 10, false);
        t.setTextColor(getColor(R.color.text_dim));
        t.setLineSpacing(0, 1.2f);
        t.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 6));
        return t;
    }

    private TextView button(String s, View.OnClickListener l, boolean primary) {
        TextView b = new TextView(this);
        b.setText(s);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setTextColor(getColor(R.color.text));
        b.setGravity(Gravity.CENTER);
        b.setBackgroundResource(primary ? R.drawable.tile_primary : R.drawable.tile);
        int p = Ui.dp(this, 10);
        b.setPadding(p, p, p, p);
        b.setMinHeight(Ui.dp(this, 44));
        b.setClickable(true);
        b.setFocusable(true);
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        return r;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(Ui.dp(this, 6));
        return lp;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams full() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 6);
        return lp;
    }
}
