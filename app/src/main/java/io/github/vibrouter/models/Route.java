package io.github.vibrouter.models;

import com.google.android.gms.maps.model.LatLng;

import java.util.Collections;
import java.util.List;

public class Route {
    private LatLng mOrigin;
    private LatLng mDestination;
    private List<LatLng> mWayPoints;

    public Route(List<LatLng> wayPoints) {
        if (wayPoints.isEmpty()) {
            throw new IllegalArgumentException("way point must contain at least 1 point");
        }
        mOrigin = wayPoints.get(0);
        mDestination = wayPoints.get(wayPoints.size() - 1);
        mWayPoints = wayPoints;
    }

    public LatLng getOrigin() {
        return mOrigin;
    }

    public LatLng getDestination() {
        return mDestination;
    }

    public List<LatLng> getWayPoints() {
        return Collections.unmodifiableList(mWayPoints);
    }
}
