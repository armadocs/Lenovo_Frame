package app.framedash;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Lets the frame's gestures switch desktops while another app (Fotoo) is open, and brings the
 * dashboard back when someone walks up. Uses Android's accessibility key filter: it only looks at
 * key presses and which app is in front, never at screen content.
 */
public class GestureService extends AccessibilityService {

    private String front = "";
    private Presence presence;

    static boolean enabled(Context c) {
        String list = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String me = new ComponentName(c, GestureService.class).flattenToString();
        return list != null && (list.contains(me) || list.contains(c.getPackageName() + "/.GestureService"));
    }

    @Override
    protected void onServiceConnected() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            setServiceInfo(info);
        }
        presence = new Presence(this, this::someoneNear);
        presence.start(true);
    }

    @Override
    public void onDestroy() {
        if (presence != null) presence.stop();
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (pkg.equals("com.android.systemui")) return;
        front = pkg;
        String left = Desks.leftPackage(this);
        if (!left.isEmpty() && pkg.equals(left)) Desks.current = Desks.LEFT_APP;
    }

    /** Our own screens handle keys themselves; here we only act while another app is in front. */
    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (front.isEmpty() || front.equals(getPackageName())) return false;
        String a = Bindings.actionForKey(this, e);
        if (a == null || Bindings.UP.equals(a) || Bindings.DOWN.equals(a)) return false; // up/down: the app's own
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            if (Bindings.GO_LEFT.equals(a)) Desks.left(this);
            else if (Bindings.GO_RIGHT.equals(a)) Desks.right(this);
            else if (Bindings.DASHBOARD.equals(a)) { Desks.human(); Desks.show(this, Desks.DASHBOARD); }
        }
        return true;
    }

    private void someoneNear() {
        // Only for other apps; FrameDash's own screens watch the sensors themselves.
        if (front.isEmpty() || front.equals(getPackageName())) return;
        if (Desks.current == Desks.LEFT_APP && Desks.presenceShouldReturn(this)) Desks.show(this, Desks.DASHBOARD);
    }

    @Override public void onInterrupt() {}
}
