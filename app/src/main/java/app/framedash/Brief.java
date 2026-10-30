package app.framedash;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * The daily brief: a small JSON file that Grok writes (e.g. brief.json in a
 * private GitHub repo) and the frame reads. Check-offs are stored on the
 * device, per brief date.
 *
 * Format:
 * {
 *   "date": "2026-10-29",
 *   "title": "Armada daily brief",
 *   "generated_at": "2026-10-29T08:25:00-07:00",
 *   "sections": [
 *     {"id": "needs",   "title": "Needs you",   "kind": "tasks",  "items": [ ... ]},
 *     {"id": "moving",  "title": "Moving",      "kind": "info",   "items": [ ... ]},
 *     {"id": "next",    "title": "Coming up",   "kind": "events", "items": [ ... ]},
 *     {"id": "parking", "title": "Parking lot", "kind": "later",  "items": [ ... ]}
 *   ]
 * }
 * item: {"id": "short-stable-id", "title": "...", "detail": "...", "when": "2026-10-30T09:00"}
 */
final class Brief {


    static final class Item {
        String id, title, detail, when;
        String pageId = "";   // set when the item lives in Notion
        boolean done;         // Notion's Done checkbox (Notion items only)
    }

    static final class Section {
        String id, title, kind;
        final List<Item> items = new ArrayList<>();
    }

    String date = "", title = "Daily brief", generatedAt = "";
    boolean live;   // true = from Notion (check-offs sync), false = from a file (check-offs stay on this frame)
    final List<Section> sections = new ArrayList<>();

    // ------------------------------------------------------------------ parsing

    static Brief parse(String json) throws IOException {
        try {
            JSONObject o = new JSONObject(json);
            Brief b = new Brief();
            b.date = o.optString("date");
            b.title = o.optString("title", "Daily brief");
            b.generatedAt = o.optString("generated_at");
            b.live = o.optBoolean("live", false);
            JSONArray secs = o.optJSONArray("sections");
            if (secs == null) throw new IOException("No \"sections\" in the brief");
            for (int i = 0; i < secs.length(); i++) {
                JSONObject so = secs.getJSONObject(i);
                Section s = new Section();
                s.id = so.optString("id", "s" + i);
                s.title = so.optString("title", "");
                s.kind = so.optString("kind", guessKind(s.id, s.title));
                JSONArray items = so.optJSONArray("items");
                if (items != null) {
                    for (int j = 0; j < items.length(); j++) {
                        Object raw = items.get(j);
                        Item it = new Item();
                        if (raw instanceof JSONObject) {
                            JSONObject io = (JSONObject) raw;
                            it.title = io.optString("title", "");
                            it.detail = io.optString("detail", "");
                            it.when = io.optString("when", "");
                            it.id = io.optString("id", "");
                            it.pageId = io.optString("page_id", "");
                            it.done = io.optBoolean("done", false);
                        } else {
                            it.pageId = "";
                            it.title = String.valueOf(raw);
                            it.detail = "";
                            it.when = "";
                            it.id = "";
                        }
                        if (it.id.isEmpty()) it.id = s.id + "-" + Integer.toHexString(it.title.hashCode());
                        if (!it.title.isEmpty()) s.items.add(it);
                    }
                }
                if (!s.items.isEmpty()) b.sections.add(s);
            }
            return b;
        } catch (org.json.JSONException e) {
            throw new IOException("The brief file isn't valid JSON: " + e.getMessage());
        }
    }

    private static String guessKind(String id, String title) {
        String t = (id + " " + title).toLowerCase();
        if (t.contains("need") || t.contains("todo") || t.contains("task")) return "tasks";
        if (t.contains("park") || t.contains("later")) return "later";
        if (t.contains("next") || t.contains("tomorrow") || t.contains("coming") || t.contains("calendar")) return "events";
        return "info";
    }

    int taskCount() {
        int n = 0;
        for (Section s : sections) if ("tasks".equals(s.kind)) n += s.items.size();
        return n;
    }

    // ------------------------------------------------------------------ check-offs (on this frame)

    private static SharedPreferences done(Context c) {
        return c.getSharedPreferences("brief_done", Context.MODE_PRIVATE);
    }

    boolean isDone(Context c, Item it) {
        if (!it.pageId.isEmpty()) return it.done;
        return done(c).getBoolean(date + "/" + it.id, false);
    }

    /** Local part of ticking an item; Notion items are written back by the caller. */
    void setDone(Context c, Item it, boolean v) {
        if (!it.pageId.isEmpty()) it.done = v;
        else done(c).edit().putBoolean(date + "/" + it.id, v).apply();
    }

    /** Same JSON format as brief.json, plus Notion fields, for the offline copy. */
    String toJson() {
        try {
            JSONObject o = new JSONObject().put("date", date).put("title", title)
                    .put("generated_at", generatedAt).put("live", live);
            JSONArray secs = new JSONArray();
            for (Section s : sections) {
                JSONArray items = new JSONArray();
                for (Item it : s.items) {
                    items.put(new JSONObject().put("id", it.id).put("title", it.title)
                            .put("detail", it.detail == null ? "" : it.detail)
                            .put("when", it.when == null ? "" : it.when)
                            .put("page_id", it.pageId).put("done", it.done));
                }
                secs.put(new JSONObject().put("id", s.id).put("title", s.title).put("kind", s.kind).put("items", items));
            }
            return o.put("sections", secs).toString();
        } catch (org.json.JSONException e) {
            return "{}";
        }
    }

    int doneCount(Context c) {
        int n = 0;
        for (Section s : sections) {
            if (!"tasks".equals(s.kind)) continue;
            for (Item it : s.items) if (isDone(c, it)) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ fetch + cache

    static File cacheFile(Context c) { return new File(c.getFilesDir(), "brief.json"); }

    /** Last brief saved on this frame, or null. */
    static Brief cached(Context c) {
        File f = cacheFile(c);
        if (!f.exists()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return parse(new String(readAll(in, 512 * 1024), "UTF-8"));
        } catch (Exception e) {
            return null;
        }
    }

    static boolean configured(Context c) {
        return Notion.configured(c);
    }

    /** Gets the current brief from Notion and keeps a copy for offline use. */
    static Brief load(Context c) throws IOException {
        if (!Notion.configured(c)) throw new IOException("Notion isn't connected (Settings → Daily brief)");
        Brief b = Notion.fetch(c);
        try (FileOutputStream out = new FileOutputStream(cacheFile(c))) {
            out.write(b.toJson().getBytes("UTF-8"));
        }
        return b;
    }

    private static byte[] readAll(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
            if (bos.size() > limit) throw new IOException("Brief file too large");
        }
        return bos.toByteArray();
    }
}
