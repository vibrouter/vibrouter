package io.github.vibrouter.managers;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;

import io.github.vibrouter.hardware.VibrationController;
import io.github.vibrouter.models.Coordinate;
import io.github.vibrouter.models.Route;
import io.github.vibrouter.utils.GpsUtil;

public class Navigator implements PositionManager.OnPositionChangeListener {
    public interface NavigationStatusListener {
        int NAVIGATING_FORWARD = 0;
        int NAVIGATING_LEFT = -1;
        int NAVIGATING_RIGHT = 1;

        void currentDirection(int direction);
        void arrived();
    }

    private static final double FORWARD_ERROR_THRESHOLD = 30; // deg
    private static final double GOAL_DISTANCE_THRESHOLD = 0.020; // km

    private PositionManager mPositionManager;
    private VibrationController mVibrationController;

    private Route mRoute;
    private NavigationStatusListener mNavigationStatusListener;

    private List<LatLng> mNavigationSubGoals;

    private static final int STATE_IDLE = 0;
    private static final int STATE_NAVIGATING = 1;
    private int mState = STATE_IDLE;

    public Navigator(PositionManager manager, VibrationController controller) {
        mPositionManager = manager;
        mVibrationController = controller;
    }

    public void setRoute(Route route) {
        if (route == null) {
            throw new IllegalArgumentException("Route should not be null!");
        }
        mRoute = route;
        mNavigationSubGoals = new ArrayList<>(mRoute.getWayPoints());
    }

    public boolean isRouteSet() {
        return mRoute != null;
    }

    public void startNavigation(NavigationStatusListener listener) {
        if (!isRouteSet()) {
            throw new IllegalStateException("Route is not set yet!");
        }
        mNavigationStatusListener = listener;
        mPositionManager.registerOnPositionChangeListener(this);
        mState = STATE_NAVIGATING;
    }

    public void pauseNavigation() {
        mState = STATE_IDLE;
        mNavigationStatusListener = null;
        mPositionManager.unregisterOnPositionChangeListener(this);
        mVibrationController.stopVibrate();
    }

    @Override
    public void onPositionChange(Coordinate position) {
        if (!isNavigating()) {
            return;
        }
        if (hasArrived(position)) {
            mVibrationController.startVibrate(VibrationController.PATTERN_ARRIVE);
            announceArrived();
            return;
        }
        vibrateAndNavigateUser(position);
    }

    public boolean isNavigating() {
        return mState == STATE_NAVIGATING;
    }

    private LatLng chooseNextSubGoalFrom(LatLng current) {
        if (mNavigationSubGoals.size() == 1) {
            // Last goal is destination
            return mNavigationSubGoals.get(0);
        }
        LatLng closest = mNavigationSubGoals.get(0);
        double distance = GpsUtil.computeDistanceBetween(current, closest);
        if (distance < GOAL_DISTANCE_THRESHOLD) {
            mNavigationSubGoals.remove(closest);
        }
        return mNavigationSubGoals.get(0);
    }

    private boolean hasArrived(Coordinate position) {
        LatLng current = position.getLocation();
        LatLng destination = mRoute.getDestination();
        double distance = GpsUtil.computeDistanceBetween(current, destination);
        return (distance < GOAL_DISTANCE_THRESHOLD);
    }

    private void vibrateAndNavigateUser(Coordinate position) {
        LatLng currentLocation = position.getLocation();
        LatLng nextSubGoal = chooseNextSubGoalFrom(currentLocation);
        double currentRotation = position.getRotation();
        double subGoalDirection = GpsUtil.computeGoalDirection(currentLocation, nextSubGoal);
        double error = computeOrientationError(currentRotation, subGoalDirection);
        if (Math.abs(error) < FORWARD_ERROR_THRESHOLD) {
            mVibrationController.startVibrate(VibrationController.PATTERN_FORWARD);
            announceNavigationStatus(NavigationStatusListener.NAVIGATING_FORWARD);
        } else if (error < 0.0) {
            mVibrationController.startVibrate(VibrationController.PATTERN_RIGHT);
            announceNavigationStatus(NavigationStatusListener.NAVIGATING_RIGHT);
        } else if (error > 0.0) {
            mVibrationController.startVibrate(VibrationController.PATTERN_LEFT);
            announceNavigationStatus(NavigationStatusListener.NAVIGATING_LEFT);
        }
    }

    private void announceArrived() {
        if (mNavigationStatusListener == null) {
            return;
        }
        mNavigationStatusListener.arrived();
    }

    private void announceNavigationStatus(int status) {
        if (mNavigationStatusListener == null) {
            return;
        }
        mNavigationStatusListener.currentDirection(status);
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
}
