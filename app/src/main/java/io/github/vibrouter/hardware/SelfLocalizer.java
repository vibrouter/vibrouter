package io.github.vibrouter.hardware;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import java.util.HashSet;
import java.util.Set;

public class SelfLocalizer implements LocationListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    public interface OnLocationChangeListener {
        void OnLocationChange(LatLng location);
    }

    private GoogleApiClient mApiClient;
    private Set<OnLocationChangeListener> mListeners = new HashSet<>();

    public SelfLocalizer(Context context) {
        mApiClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addOnConnectionFailedListener(this)
                .build();
    }

    public void startUpdating() {
        mApiClient.registerConnectionCallbacks(this);
        if (!mApiClient.isConnected()) {
            mApiClient.connect();
        }
    }

    public void stopUpdating() {
        if (mApiClient.isConnected()) {
            mApiClient.disconnect();
        }
        mApiClient.unregisterConnectionCallbacks(this);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        try {
            Location location = LocationServices.FusedLocationApi.getLastLocation(mApiClient);
            if (location != null) {
                announceNewLocation(new LatLng(location.getLatitude(), location.getLongitude()));
            }
        } catch (SecurityException noPermission) {
            return;
        }
        startLocationUpdate();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onLocationChanged(Location location) {
        announceNewLocation(new LatLng(location.getLatitude(), location.getLongitude()));
    }

    public void registerOnLocationChangeListener(OnLocationChangeListener listener) {
        mListeners.add(listener);
    }

    public void unregisterOnLocationChangeListener(OnLocationChangeListener listener) {
        mListeners.remove(listener);
    }

    private void announceNewLocation(LatLng location) {
        for (OnLocationChangeListener listener : mListeners) {
            listener.OnLocationChange(location);
        }
    }

    private void startLocationUpdate() {
        final long INTERVAL_MILLIS = 1000;
        LocationRequest request = new LocationRequest();
        request.setInterval(INTERVAL_MILLIS);
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        try {
            LocationServices.FusedLocationApi.requestLocationUpdates(mApiClient, request, this);
        } catch (SecurityException noPermission) {
            // TODO: handle permission errors
        }
    }
}
