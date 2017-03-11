package io.github.vibrouter.models;

import com.google.android.gms.maps.model.LatLng;

public class Coordinate {
    public static final Coordinate INVALID_COORDINATE = new Coordinate(null, Float.NaN);

    private LatLng mLocation;
    private float mRotation;

    public Coordinate(LatLng location, float rotation) {
        mLocation = location;
        mRotation = rotation;
    }

    public LatLng getLocation() {
        return mLocation;
    }

    public float getRotation() {
        return mRotation;
    }
}
