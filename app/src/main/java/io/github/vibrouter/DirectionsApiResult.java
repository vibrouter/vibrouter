package io.github.vibrouter;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by yishihara on 17/01/29.
 */

public class DirectionsApiResult {
    public static class GeocodedWaypoint {
        public String geocoderStatus;
        public String placeId;
        public List<String> types;
    }

    public static class Duration {
        public String text;
        public int value;
    }

    public static class Distance {
        public String text;
        public int value;
    }

    public static class Location {
        public String lat;
        public String lng;
    }

    public static class Polyline {
        public String points;
    }

    public static class Bound {
        public Location northeast;
        public Location southwest;
    }

    public static class Step {
        public Distance distance;
        public Duration duration;
        public Location endLocation;
        public String htmlInstructions;
        public Polyline polyline;
        public Location startLocation;
        public String travelMode;
        public String maneuver;
    }

    public static class Leg {
        public Distance distance;
        public Duration duration;
        public String endAddress;
        public Location endLocation;
        public String startAddress;
        public Location startLocation;
        public List<Step> steps;
    }

    public static class Route {
        public Bound bounds;
        public String copyrights;
        public List<Leg> legs;
        public String summary;
        public Polyline overviewPolylilne;
        public List<String> warning;
    }

    public List<GeocodedWaypoint> geocodedWaypoints;
    public List<Route> routes;
    public String status;

    public List<LatLng> getWaypoints() {
        List<LatLng> waypoints = new ArrayList<>();
        List<Leg> legs = routes.get(0).legs;
        Location startLocation = legs.get(0).startLocation;
        LatLng startLatLng = new LatLng(
                Double.valueOf(startLocation.lat),
                Double.valueOf(startLocation.lng));
        waypoints.add(startLatLng);
        for (Step step : legs.get(0).steps) {
            for (LatLng polyline : decodePolyline(step.polyline.points)) {
                waypoints.add(polyline);
            }
        }
        Location endLocation = legs.get(legs.size() - 1).endLocation;
        LatLng endLatLng = new LatLng(
                Double.valueOf(endLocation.lat),
                Double.valueOf(endLocation.lng));
        waypoints.add(endLatLng);
        return waypoints;
    }

    public static List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0;
        int lat = 0;
        int lng = 0;

        while (index < encoded.length()) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);

            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);

            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)), (((double) lng / 1E5)));
            poly.add(p);
        }

        return poly;
    }
}
