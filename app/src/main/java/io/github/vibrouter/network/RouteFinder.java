package io.github.vibrouter.network;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

public interface RouteFinder {
    interface OnRouteFoundCallback {
        void onRouteFound(List<LatLng> route);
    }

    void findRoute(LatLng origin, LatLng destination, OnRouteFoundCallback callback);
}
