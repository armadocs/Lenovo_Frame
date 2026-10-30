package app.framedash;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Small view and launch helpers shared by the screens. */
final class Ui {
    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static void start(Context ctx, Intent i) {
        try {
            ctx.startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(ctx, "Couldn't open that app", Toast.LENGTH_SHORT).show();
        }
    }

    static void openAppInfo(Context ctx, Intent launchIntent) {
        if (launchIntent.getComponent() == null && launchIntent.getPackage() == null) return;
        String pkg = launchIntent.getComponent() != null
                ? launchIntent.getComponent().getPackageName() : launchIntent.getPackage();
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        start(ctx, i);
    }

    /**
     * Puts a fixed header (title + a large Close button) above a scrolling body, so a screen can
     * always be closed with the mouse, even when it listens to keys and gestures.
     */
    static LinearLayout withCloseBar(Activity a, String title, View body) {
        LinearLayout outer = new LinearLayout(a);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(a.getColor(R.color.bg));

        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(a, 12);
        bar.setPadding(dp(a, 20), p, p, p);
        TextView t = new TextView(a);
        t.setText(title);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        t.setTextColor(a.getColor(R.color.text));
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        bar.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(a);
        close.setText("\u2715  Close");
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        close.setTextColor(a.getColor(R.color.text));
        close.setGravity(Gravity.CENTER);
        close.setBackgroundResource(R.drawable.tile_primary);
        close.setPadding(dp(a, 20), 0, dp(a, 20), 0);
        close.setMinWidth(dp(a, 110));
        close.setClickable(true);
        close.setFocusable(true);
        close.setContentDescription("Close");
        close.setOnClickListener(v -> a.finish());
        bar.addView(close, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(a, 48)));

        outer.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        outer.addView(body, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return outer;
    }

    /** Icon above a label, used for both app grid cells and shortcut tiles. */
    static final class IconTile extends LinearLayout {
        final ImageView icon;
        final TextView label;

        IconTile(Context c, int iconDp, float textSp) {
            super(c);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            setBackgroundResource(R.drawable.tile);
            int pad = dp(c, 12);
            setPadding(pad, pad, pad, pad);
            setMinimumHeight(dp(c, iconDp + 64));

            icon = new ImageView(c);
            LayoutParams ip = new LayoutParams(dp(c, iconDp), dp(c, iconDp));
            addView(icon, ip);

            label = new TextView(c);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSp);
            label.setTextColor(c.getColor(R.color.text));
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(c, 8);
            addView(label, lp);
        }

        void bind(AppEntry e) {
            icon.setImageDrawable(e.icon);
            label.setText(e.label);
        }
    }
}
