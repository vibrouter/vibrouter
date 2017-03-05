package io.github.vibrouter.managers;

import com.google.android.gms.maps.model.LatLng;

import io.github.vibrouter.hardware.VibrationController;
import io.github.vibrouter.models.Coordinate;
import io.github.vibrouter.models.Route;
import io.github.vibrouter.utils.GpsUtil;

public class Navigator implements PositionManager.OnPositionChangeListener {
    public interface NavigationStatusListener {
        int NAVIGATING_FORWARD = 0;
        int NAVIGATING_LEFT = -1;
        int NAVIGATING_RIGHT = 1;

        void currentDirection(int direction, boolean arrived);
    }

    private static final double FORWARD_ERROR_THRESHOLD = 30; // deg
    private static final double GOAL_DISTANCE_THRESHOLD = 0.020; // km

    private PositionManager mPositionManager;
    private VibrationController mVibrationController;

    private Route mRoute;
    private NavigationStatusListener mNavigationStatusListener;

    public Navigator(PositionManager manager, VibrationController controller) {
        mPositionManager = manager;
        mVibrationController = controller;
    }

    public void startNavigation(Route route, NavigationStatusListener listener) {
        if (route == null) {
            throw new IllegalArgumentException("Route should not be null!");
        }
        mRoute = route;
        mNavigationStatusListener = listener;
        mPositionManager.registerOnPositionChangeListener(this);
    }

    public void stopNavigation() {
        mRoute = null;
        mNavigationStatusListener = null;
        mPositionManager.unregisterOnPositionChangeListener(this);
        mVibrationController.stopVibrate();
    }

    @Override
    public void onPositionChange(Coordinate position) {
        if (isNavigating()) {
            vibrateAndNavigateUser(position);
        }
    }

    public boolean isNavigating() {
        return mRoute != null;
    }

    private void vibrateAndNavigateUser(Coordinate position) {
        double currentRotation = position.getRotation();
        LatLng currentLocation = position.getLocation();
        LatLng nextSubGoal = mRoute.getNextWayPoint();
        double distance = GpsUtil.computeDistanceBetween(currentLocation, nextSubGoal);
        boolean arrived = (nextSubGoal == null)
                || (nextSubGoal.equals(mRoute.getDestination()) && (distance < GOAL_DISTANCE_THRESHOLD));

        if (arrived) {
            mVibrationController.startVibrate(VibrationController.PATTERN_ARRIVE);
            announceNavigationStatus(NavigationStatusListener.NAVIGATING_FORWARD, arrived);
            return;
        }
        if (distance < GOAL_DISTANCE_THRESHOLD) {
            mRoute.removeWayPoint(nextSubGoal);
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

    private void announceNavigationStatus(int status, boolean arrived) {
        if (mNavigationStatusListener == null) {
            return;
        }
        mNavigationStatusListener.currentDirection(status, arrived);
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
