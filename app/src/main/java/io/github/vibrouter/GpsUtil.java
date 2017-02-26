package io.github.vibrouter;

import com.google.android.gms.maps.model.LatLng;

/**
 * Created by yuishihara on 2017/02/04.
 */

public class GpsUtil {
    public static double computeDistanceBetween(LatLng from, LatLng to) {
         if (from == null || to == null) {
            return Double.MAX_VALUE;
        }
        final double EARTH_RADIUS = 6378.137; //km
        double x1 = Math.toRadians(from.longitude);
        double y1 = Math.toRadians(from.latitude);
        double x2 = Math.toRadians(to.longitude);
        double y2 = Math.toRadians(to.latitude);

        double dx = x2 - x1;
        double distance = EARTH_RADIUS *
                Math.acos(Math.sin(y1) * Math.sin(y2)
                        + Math.cos(y1) * Math.cos(y2) * Math.cos(dx));
        return Math.abs(distance);
    }

    public static double computeGoalDirection(LatLng from, LatLng to) {
        if (from == null || to == null) {
            return 0.0;
        }
        double x1 = Math.toRadians(from.longitude);
        double y1 = Math.toRadians(from.latitude);
        double x2 = Math.toRadians(to.longitude);
        double y2 = Math.toRadians(to.latitude);

        double dx = x2 - x1;
        double direction = Math.toDegrees(Math.atan2(Math.sin(dx), Math.cos(y1) * Math.tan(y2) - Math.sin(y1) * Math.cos(dx)));
        return (0.0 <= direction) ? direction : 360 + direction;
    }

    private GpsUtil() {
        // Do not instantiate
    }
}
