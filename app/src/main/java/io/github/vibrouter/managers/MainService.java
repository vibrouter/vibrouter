package io.github.vibrouter.managers;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.util.Collections;
import java.util.List;

import io.github.vibrouter.hardware.VibrationController;
import io.github.vibrouter.models.Coordinate;
import io.github.vibrouter.network.DirectionsApi;
import io.github.vibrouter.network.RouteFinder;
import io.github.vibrouter.utils.GpsUtil;

public class MainService extends Service {
    public interface CurrentPositionListener {
        void onPositionChanged(Coordinate position);
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

    private PositionManager mPositionManager;
    private RouteFinder mRouteFinder;

    private LatLng mDestinationLocation;

    private Coordinate mCurrentPosition = Coordinate.INVALID_COORDINATE;

    private double mGoalDirection;

    private final double FORWARD_ERROR_THRESHOLD = 30; // deg
    private final double GOAL_DISTANCE_THRESHOLD = 0.020; // km

    private VibrationController mVibrationController;

    private static final int STATE_IDLE = 0;
    private static final int STATE_NAVIGATING = 1;
    private int mState = STATE_IDLE;

    private NavigationStatusListener mNavigationListener;

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

    private CurrentPositionListener mCurrentPositionListener;
    private PositionManager.OnPositionChangeListener mOnPositionChangeListener = new PositionManager.OnPositionChangeListener() {
        @Override
        public void onPositionChange(Coordinate position) {
            mCurrentPosition = position;
            announceNewPosition(position);
            if (isNavigating()) {
                vibrateAndNavigateUser();
            } else {
                mVibrationController.stopVibrate();
            }
        }
    };

    public class LocalBinder extends Binder {
        public MainService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MainService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mPositionManager = new PositionManager(this);
        mRouteFinder = new DirectionsApi(this);
        mVibrationController = new VibrationController(this);
    }

    @Override
    public void onDestroy() {
        stopSamplingSensors();
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

    public void setDestination(LatLng position, RouteFinder.OnRouteFoundCallback callback) {
        mDestinationLocation = position;
        getRoute(mCurrentPosition.getLocation(), mDestinationLocation, callback);
    }

    public void setPositionListener(CurrentPositionListener listener) {
        mCurrentPositionListener = listener;
    }

    public void unsetPositionListener() {
        mCurrentPositionListener = null;
    }

    public boolean isNavigating() {
        return (mState == STATE_NAVIGATING)
                && (mCurrentPosition.getLocation() != null)
                && (mDestinationLocation != null);
    }

    public void startSamplingSensors() {
        mPositionManager.registerOnPositionChangeListener(mOnPositionChangeListener);
    }

    public void stopSamplingSensors() {
        mPositionManager.unregisterOnPositionChangeListener(mOnPositionChangeListener);
    }

    private void getRoute(LatLng origin, LatLng destination, final RouteFinder.OnRouteFoundCallback callback) {
        mRouteFinder.findRoute(origin, destination, new RouteFinder.OnRouteFoundCallback() {
            @Override
            public void onRouteFound(List<LatLng> route) {
                mRouteWayPoints.setFromCurrentWayPoints(route);
                callback.onRouteFound(route);
            }
        });

    }

    private double computeOrientationError(double current, double goal) {
        return fitInRange(current - goal);
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

    private void announceNewPosition(Coordinate position) {
        if (mCurrentPositionListener == null) {
            return;
        }
        mCurrentPositionListener.onPositionChanged(position);
    }

    private void vibrateAndNavigateUser() {
        Log.i(TAG, "vibrate and navigate user!!");
        double currentRotation = mCurrentPosition.getRotation();
        LatLng currentLocation = mCurrentPosition.getLocation();
        LatLng nextSubGoal = mRouteWayPoints.getNextWayPoint();
        double distance = GpsUtil.computeDistanceBetween(currentLocation, nextSubGoal);
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

        double subGoalDirection = GpsUtil.computeGoalDirection(currentLocation, nextSubGoal);
        double error = computeOrientationError(currentRotation, subGoalDirection);
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
