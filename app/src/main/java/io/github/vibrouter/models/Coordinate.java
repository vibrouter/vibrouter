package io.github.vibrouter.models;

import com.google.android.gms.maps.model.LatLng;

public class Coordinate {
    private LatLng mPosition;
    private double mRotation;

    public Coordinate(LatLng position, double rotation) {
        mPosition = position;
        mRotation = rotation;
    }

    public LatLng getPosition() {
        return mPosition;
    }

    public double getRotation() {
        return mRotation;
    }
}
