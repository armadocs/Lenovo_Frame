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

    Presence(Context c, Listener l) {
        ctx = c;
        sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        proximity = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
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
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        String v = Bindings.valueKey(e);
        String before = last.put(e.sensor, v);
        if (before == null || before.equals(v)) return; // first reading is the resting state
        if (e.sensor == proximity) {
            float d = e.values[0];
            if (d < proximity.getMaximumRange() && d < 5f) listener.onSomeoneNear();
        } else {
            listener.onSomeoneNear();
        }
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}
}
