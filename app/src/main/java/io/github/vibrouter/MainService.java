package io.github.vibrouter;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Collections;
import java.util.List;

import io.github.vibrouter.utils.GpsUtil;

public class MainService extends Service {
    public interface CurrentLocationListener {
        void onLocationChanged(LatLng location);

        void onRotationChanged(double rotation);
    }

    public interface RouteSearchFinishCallback {
        void onRouteSearchFinish(List<LatLng> fromCurrentWayPoint,
                                 List<LatLng> trainWayPoint,
                                 List<LatLng> toDestinationWayPoint);
    }

    public interface NavigationStatusListener {
        int NAVIGATING_FORWARD = 0;
        int NAVIGATING_LEFT = -1;
        int NAVIGATING_RIGHT = 1;

        void currentDirection(int direction, boolean arrived);
    }

    private static final String TAG = MainService.class.getSimpleName();

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    private SensorManager mSensorManager;
    private Sensor mRotationSensor;
    private float[] mRotationVectorReading = new float[3];
    private final float[] mRotationMatrix = new float[9];

    private GoogleApiClient mApiClient;
    private LatLng mDestinationLocation;
    private LatLng mCurrentLocation;
    private double mCurrentRotation;
    private double mGoalDirection;

    private final double FORWARD_ERROR_THRESHOLD = 30; // deg
    private final double GOAL_DISTANCE_THRESHOLD = 0.020; // km

    private VibrationController mVibrationController;

    private static final int STATE_IDLE = 0;
    private static final int STATE_NAVIGATING = 1;
    private int mState = STATE_IDLE;

    private NavigationStatusListener mNavigationListener;

    private Gson mGson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

    private static class Route {
        private List<LatLng> mFromCurrentWayPoints = Collections.emptyList();

        private Route() {
        }

        private void setFromCurrentWayPoints(List<LatLng> fromCurrent) {
            mFromCurrentWayPoints = fromCurrent;
        }

        private LatLng getNextWayPoint() {
            if (!mFromCurrentWayPoints.isEmpty()) {
                return mFromCurrentWayPoints.get(0);
            }
            return null;
        }

        private void removeWayPoint(LatLng pointToRemove) {
            if (mFromCurrentWayPoints.contains(pointToRemove)) {
                mFromCurrentWayPoints.remove(pointToRemove);
            }
        }
    }

    private Route mRouteWayPoints = new Route();

    private CurrentLocationListener mCurrentLocationListener;

    private RequestQueue mRequestQueue;

    private SensorEventListener mLocationSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                System.arraycopy(event.values, 0,
                        mRotationVectorReading, 0, mRotationVectorReading.length);
                mCurrentRotation = computeDeviceOrientation(mRotationVectorReading);
                announceNewRotation(mCurrentRotation);
                if (isNavigating()) {
                    vibrateAndNavigateUser();
                } else {
                    mVibrationController.stopVibrate();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    private GoogleApiClient.ConnectionCallbacks mConnectionCallback = new GoogleApiClient.ConnectionCallbacks() {
        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.i(TAG, "Connected to google api client!");
            try {
                Location location = LocationServices.FusedLocationApi.getLastLocation(mApiClient);
                if (location != null) {
                    updateCurrentLocation(new LatLng(location.getLatitude(), location.getLongitude()));
                }
            } catch (SecurityException failedGettingLocationServices) {
                Log.e(TAG, "Could not obtain location services");
                return;
            }
            startLocationUpdate();
        }

        @Override
        public void onConnectionSuspended(int i) {

        }
    };

    private GoogleApiClient.OnConnectionFailedListener mConnectionFailedListener = new GoogleApiClient.OnConnectionFailedListener() {
        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.e(TAG, "Failed connecting to google map api");
        }
    };

    private LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            updateCurrentLocation(new LatLng(location.getLatitude(), location.getLongitude()));
        }
    };

    public class LocalBinder extends Binder {
        MainService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MainService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mRequestQueue = Volley.newRequestQueue(this);
        mVibrationController = new VibrationController(this);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mRotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        startSamplingSensors();
        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(mConnectionCallback)
                .addOnConnectionFailedListener(mConnectionFailedListener)
                .build();
        mApiClient.connect();
    }

    @Override
    public void onDestroy() {
        stopSamplingSensors();
        if (mApiClient != null) {
            if (mApiClient.isConnected()) {
                mApiClient.disconnect();
            }
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    public void startNavigation(NavigationStatusListener listener) {
        mNavigationListener = listener;
        mState = STATE_NAVIGATING;
    }

    public void stopNavigation() {
        mNavigationListener = null;
        mState = STATE_IDLE;
    }

    public void setDestination(LatLng position, RouteSearchFinishCallback callback) {
        mDestinationLocation = position;
        getRoute(mCurrentLocation, mDestinationLocation, callback);
    }

    public void setLocationListener(CurrentLocationListener listener) {
        mCurrentLocationListener = listener;
    }

    public void unsetLocationListener() {
        mCurrentLocationListener = null;
    }

    public boolean isNavigating() {
        return (mState == STATE_NAVIGATING)
                && (mCurrentLocation != null)
                && (mDestinationLocation != null);
    }

    private void getRoute(LatLng origin, LatLng destination, final RouteSearchFinishCallback callback) {
        if (origin == null || destination == null) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onRouteSearchFinish(null, null, null);
                }
            });
            return;
        }
        mRequestQueue.cancelAll(new RequestQueue.RequestFilter() {
            @Override
            public boolean apply(Request<?> request) {
                return true;
            }
        });

        sendRouteSearchRequestBetween(origin, destination, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                DirectionsApiResult result = mGson.fromJson(response, DirectionsApiResult.class);
                if (callback != null) {
                    callback.onRouteSearchFinish(result.getWaypoints(), null, null);
                    mRouteWayPoints.setFromCurrentWayPoints(result.getWaypoints());
                }
            }
        });
    }

    private void sendRouteSearchRequestBetween(LatLng origin, LatLng destination, Response.Listener<String> listener) {
        boolean useSensor = false;
        String language = "ja";
        String travelMode = "walking";

        String parameters = String.format("origin=%s,%s&destination=%s,%s&sensor=%s&language=%s&mode=%s",
                origin.latitude, origin.longitude,
                destination.latitude, destination.longitude,
                String.valueOf(useSensor),
                language,
                travelMode);

        String output = "json";
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;

        StringRequest request = new StringRequest(Request.Method.GET, url, listener, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });
        mRequestQueue.add(request);
    }

    private void startSamplingSensors() {
        mSensorManager.registerListener(mLocationSensorListener, mRotationSensor, SensorManager.SENSOR_DELAY_UI);
    }

    private void stopSamplingSensors() {
        mSensorManager.unregisterListener(mLocationSensorListener);
    }

    private void startLocationUpdate() {
        final long INTERVAL_MILLIS = 1000;
        LocationRequest request = new LocationRequest();
        request.setInterval(INTERVAL_MILLIS);
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        try {
            LocationServices.FusedLocationApi.requestLocationUpdates(mApiClient, request, mLocationListener);
        } catch (SecurityException noPermission) {
            // TODO: handle permission errors
        }
    }

    private double computeOrientationError(double current, double goal) {
        return fitInRange(current - goal);
    }

    private float computeDeviceOrientation(float[] values) {
        final float[] orientationAngles = new float[3];
        SensorManager.getRotationMatrixFromVector(mRotationMatrix, values);
        SensorManager.getOrientation(mRotationMatrix, orientationAngles);
        for (int i = 0; i < orientationAngles.length; ++i) {
            orientationAngles[i] = (float) radianToDegrees(orientationAngles[i]);
        }
        // Log.d(TAG, String.format("Vector Azimuth %f [deg], Pitch %f [deg], Roll %f [deg]", orientationAngles[0], orientationAngles[1], orientationAngles[2]));

        return orientationAngles[0];
    }

    private double fitInRange(double diff) {
        while (180.0 < Math.abs(diff)) {
            if (diff < 0) {
                diff += 360.0;
            } else {
                diff -= 360.0;
            }
        }
        return diff;
    }

    private double radianToDegrees(double radian) {
        double degree = radian / 2.0 / Math.PI * 360.0;
        while (degree < 0.0 || 360.0 < degree) {
            if (degree < 0.0) {
                degree += 360.0;
            } else {
                degree -= 360.0;
            }
        }
        return degree;
    }

    private void updateCurrentLocation(LatLng latLng) {
        mCurrentLocation = latLng;
        announceNewLocation(latLng);
    }

    private void announceNewLocation(LatLng latLng) {
        if (mCurrentLocationListener == null) {
            return;
        }
        mCurrentLocationListener.onLocationChanged(latLng);
    }

    private void announceNewRotation(double rotation) {
        if (mCurrentLocationListener == null) {
            return;
        }
        mCurrentLocationListener.onRotationChanged(rotation);
    }

    private void vibrateAndNavigateUser() {
        Log.i(TAG, "vibrate and navigate user!!");
        LatLng nextSubGoal = mRouteWayPoints.getNextWayPoint();
        double distance = GpsUtil.computeDistanceBetween(mCurrentLocation, nextSubGoal);
        boolean arrived = (nextSubGoal == null)
                || (nextSubGoal.equals(mDestinationLocation) && (distance < GOAL_DISTANCE_THRESHOLD));

        Log.i(TAG, "Distance to next subgoal!!" + distance);
        if (arrived) {
            mVibrationController.startVibrate(VibrationController.PATTERN_ARRIVE);
            announceNavigationStatus(NavigationStatusListener.NAVIGATING_FORWARD, arrived);
            return;
        }
        Log.i(TAG, "dist: " + distance + " thre: " + GOAL_DISTANCE_THRESHOLD);
        if (distance < GOAL_DISTANCE_THRESHOLD) {
            mRouteWayPoints.removeWayPoint(nextSubGoal);
        }

        double subGoalDirection = GpsUtil.computeGoalDirection(mCurrentLocation, nextSubGoal);
        double error = computeOrientationError(mCurrentRotation, subGoalDirection);
        if (Math.abs(error) < FORWARD_ERROR_THRESHOLD) {
            mVibrationController.startVibrate(VibrationController.PATTERN_FORWARD);
            announceNavigationStatus(NavigationStatusListener.NAVIGATING_FORWARD, arrived);
        } else if (error < 0.0) {
            mVibrationController.startVibrate(VibrationController.PATTERN_RIGHT);
            announceNavigationStatus(NavigationStatusListener.NAVIGATING_RIGHT, arrived);
        } else if (error > 0.0) {
            mVibrationController.startVibrate(VibrationController.PATTERN_LEFT);
            announceNavigationStatus(NavigationStatusListener.NAVIGATING_LEFT, arrived);
        }
    }

    private void announceNavigationStatus(int direction, boolean arrived) {
        if (mNavigationListener == null) {
            return;
        }
        mNavigationListener.currentDirection(direction, arrived);
    }
}
