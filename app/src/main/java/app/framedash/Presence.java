package app.framedash;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Someone is near": the proximity sensor going near, or (when asked) any reaction at all from the
 * sensors that learned gestures use, such as the Lenovo frame's gesture sensor next to the screen.
 */
final class Presence implements SensorEventListener {

    interface Listener { void onSomeoneNear(); }

    private final Context ctx;
    private final SensorManager sm;
    private final Sensor proximity;
    private final Listener listener;
    private final List<Sensor> registered = new ArrayList<>();
    private final Map<Sensor, String> last = new HashMap<>();
    private final Sensor light;
    private float luxBase = Float.NaN;   // slow average of the room's light
    private long lastShadow;
    /** What set off the latest "someone near": "proximity", "light" or "gesture sensor". */
    String lastReason = "";

    Presence(Context c, Listener l) {
        ctx = c;
        sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        proximity = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        light = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_LIGHT);
        listener = l;
    }

    static String describe(Context c) {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        Sensor s = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        return s == null ? "" : s.getName();
    }

    /** Proximity only. */
    void start() { start(false); }

    /** @param gestureSensors also treat any reading from the gesture sensors as "someone is here". */
    void start(boolean gestureSensors) {
        stop();
        if (sm == null) return;
        if (proximity != null) add(proximity);
        if (gestureSensors) for (Sensor s : Bindings.boundSensors(ctx)) add(s);
        if (light != null && Prefs.shadowWake(ctx) > 0) add(light);
    }

    private void add(Sensor s) {
        if (registered.contains(s)) return;
        sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME);
        registered.add(s);
    }

    void stop() {
        if (sm != null && !registered.isEmpty()) sm.unregisterListener(this);
        registered.clear();
        last.clear();
        luxBase = Float.NaN;
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e.sensor == light) { onLight(e.values[0]); return; }
        String v = Bindings.valueKey(e);
        String before = last.put(e.sensor, v);
        if (before == null || before.equals(v)) return; // first reading is the resting state
        if (e.sensor == proximity) {
            float d = e.values[0];
            if (d < proximity.getMaximumRange() && d < 5f) fire("proximity");
        } else {
            fire("gesture sensor");
        }
    }

    /**
     * Someone walking up casts a shadow on the light sensor next to the screen, from further away
     * than the proximity sensor reaches. A quick drop below the room's usual level counts as presence.
     */
    private void onLight(float lux) {
        if (Float.isNaN(luxBase)) { luxBase = lux; return; }
        int level = Prefs.shadowWake(ctx);              // 1 low, 2 medium, 3 high sensitivity
        float drop = level >= 3 ? 0.15f : level == 2 ? 0.25f : 0.40f;
        long now = System.currentTimeMillis();
        if (luxBase >= 3f && lux < luxBase * (1f - drop) && now - lastShadow > 3000) {
            lastShadow = now;
            fire("light");
        }
        // Follow the room slowly (lamps, daylight) but not the quick dip of a passing shadow.
        luxBase += (lux - luxBase) * (lux < luxBase ? 0.05f : 0.3f);
    }

    private void fire(String reason) {
        lastReason = reason;
        listener.onSomeoneNear();
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}
}
