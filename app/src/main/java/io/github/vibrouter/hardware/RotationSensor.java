package io.github.vibrouter.hardware;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.HashSet;
import java.util.Set;

public class RotationSensor implements SensorEventListener {
    public interface OnRotationChangeListener {
        void onRotationChange(float newRotation);
    }

    private SensorManager mSensorManager;
    private Set<OnRotationChangeListener> mListeners = new HashSet<>();

    public RotationSensor(Context context) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    public void startUpdating() {
        Sensor rotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        mSensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
    }

    public void stopUpdating() {
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) {
            return;
        }
        float[] rotationVectorReading = new float[3];
        System.arraycopy(event.values, 0,
                rotationVectorReading, 0, rotationVectorReading.length);
        float rotation = clipAngle(computeDeviceOrientation(rotationVectorReading));
        announceNewRotation(rotation);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do nothing for now
    }

    public void registerOnRotationChangeListener(OnRotationChangeListener listener) {
        mListeners.add(listener);
    }

    public void unregisterOnRotationChangeListener(OnRotationChangeListener listener) {
        mListeners.remove(listener);
    }

    private void announceNewRotation(float rotation) {
        for (OnRotationChangeListener listener : mListeners) {
            listener.onRotationChange(rotation);
        }
    }

    private float computeDeviceOrientation(float[] values) {
        final float[] orientationAngles = new float[3];
        float[] rotationMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values);
        SensorManager.getOrientation(rotationMatrix, orientationAngles);
        for (int i = 0; i < orientationAngles.length; ++i) {
            orientationAngles[i] = (float) Math.toDegrees(orientationAngles[i]);
        }
        return orientationAngles[0];
    }

    private float clipAngle(float rotation) {
        return (rotation < 0)
                ? rotation + 360.0f
                : rotation;
    }
}
