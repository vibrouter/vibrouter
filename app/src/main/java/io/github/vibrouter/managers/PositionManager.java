package io.github.vibrouter.managers;

import android.content.Context;

import com.google.android.gms.maps.model.LatLng;

import java.util.HashSet;
import java.util.Set;

import io.github.vibrouter.hardware.RotationSensor;
import io.github.vibrouter.hardware.SelfLocalizer;
import io.github.vibrouter.models.Coordinate;

public class PositionManager implements RotationSensor.OnRotationChangeListener,
        SelfLocalizer.OnLocationChangeListener {
    public interface OnPositionChangeListener {
        void onPositionChange(Coordinate position);
    }

    private Coordinate mCurrentPosition = Coordinate.INVALID_COORDINATE;

    private RotationSensor mRotationSensor;
    private SelfLocalizer mLocalizer;

    private Set<OnPositionChangeListener> mListeners = new HashSet<>();

    public PositionManager(Context context) {
        mRotationSensor = new RotationSensor(context);
        mLocalizer = new SelfLocalizer(context);
    }

    public void registerOnPositionChangeListener(OnPositionChangeListener listener) {
        if (mListeners.isEmpty()) {
            mRotationSensor.registerOnRotationChangeListener(this);
            mRotationSensor.startUpdating();
            mLocalizer.registerOnLocationChangeListener(this);
            mLocalizer.startUpdating();
        }
        mListeners.add(listener);
    }

    public void unregisterOnPositionChangeListener(OnPositionChangeListener listener) {
        mListeners.remove(listener);
        if (mListeners.isEmpty()) {
            mRotationSensor.stopUpdating();
            mRotationSensor.unregisterOnRotationChangeListener(this);

            mLocalizer.stopUpdating();
            mLocalizer.unregisterOnLocationChangeListener(this);

            mCurrentPosition = Coordinate.INVALID_COORDINATE;
        }
    }

    @Override
    public void onRotationChange(double rotation) {
        updateCurrentPosition(mCurrentPosition.getLocation(), rotation);
    }

    @Override
    public void OnLocationChange(LatLng location) {
        updateCurrentPosition(location, mCurrentPosition.getRotation());
    }

    private void updateCurrentPosition(LatLng location, double rotation) {
        mCurrentPosition = new Coordinate(location, rotation);
        if (mCurrentPosition.getLocation() != null
                && mCurrentPosition.getRotation() != Double.NaN) {
            announcePositionChange(mCurrentPosition);
        }
    }

    private void announcePositionChange(Coordinate position) {
        for (OnPositionChangeListener listener : mListeners) {
            listener.onPositionChange(position);
        }
    }
}
