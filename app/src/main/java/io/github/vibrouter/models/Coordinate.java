package io.github.vibrouter.models;

import com.google.android.gms.maps.model.LatLng;

public class Coordinate {
    public static final Coordinate INVALID_COORDINATE = new Coordinate(null, Double.NaN);

    private LatLng mLocation;
    private double mRotation;

    public Coordinate(LatLng location, double rotation) {
        mLocation = location;
        mRotation = rotation;
    }

    public LatLng getLocation() {
        return mLocation;
    }

    public double getRotation() {
        return mRotation;
    }
}
