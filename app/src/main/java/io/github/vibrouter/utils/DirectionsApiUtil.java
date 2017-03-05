package io.github.vibrouter.utils;

import android.net.Uri;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DirectionsApiUtil {
    public static final String TRAVEL_MODE_WALKING = "walking";
    public static final String TRAVEL_MODE_DRIVING = "driving";

    public static String buildUrl(LatLng origin, LatLng destination, String mode) {
        if (!mode.equals(TRAVEL_MODE_WALKING) && !mode.equals(TRAVEL_MODE_DRIVING)) {
            return null;
        }
        final String PROTOCOL = "https";
        final String TARGET_HOST = "maps.googleapis.com";
        final String OUTPUT_TYPE = "json";
        final String PATH = "/maps/api/directions/" + OUTPUT_TYPE;
        final String KEY_ORIGIN = "origin";
        final String KEY_DESTINATION = "destination";
        final String KEY_SENSOR = "sensor";
        final String KEY_LANGUAGE = "language";
        final String KEY_MODE = "mode";
        return new Uri.Builder().scheme(PROTOCOL)
                .authority(TARGET_HOST)
                .path(PATH)
                .appendQueryParameter(KEY_ORIGIN, formatLatLngAsPair(origin))
                .appendQueryParameter(KEY_DESTINATION, formatLatLngAsPair(destination))
                .appendQueryParameter(KEY_SENSOR, String.valueOf(false))
                .appendQueryParameter(KEY_LANGUAGE, Locale.getDefault().getLanguage())
                .appendQueryParameter(KEY_MODE, mode)
                .build()
                .toString();
    }

    public static List<LatLng> decodePolyline(String encoded) {
        StringBuffer buffer = new StringBuffer(encoded);
        List<LatLng> polyline = new ArrayList<>();

        LatLng previous = new LatLng(0, 0);
        while (0 < buffer.length()) {
            // Diff from previous point is encoded.
            LatLng diff = decodeLatLng(buffer);
            LatLng location = new LatLng(diff.latitude + previous.latitude,
                    diff.longitude + previous.longitude);
            polyline.add(location);
            previous = location;
        }

        return polyline;
    }

    private static LatLng decodeLatLng(StringBuffer buffer) {
        int lat = decodeValue(buffer);
        int lng = decodeValue(buffer);
        return new LatLng((((double) lat / 1E5)), (((double) lng / 1E5)));
    }

    private static int decodeValue(StringBuffer buffer) {
        int binary;
        int shift = 0;
        int result = 0;
        do {
            binary = buffer.charAt(0) - 63;
            buffer = buffer.deleteCharAt(0);
            result |= (binary & 0x1f) << shift;
            shift += 5;
        } while (binary >= 0x20);
        return ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
    }

    private static String formatLatLngAsPair(LatLng latLng) {
        return String.format("%s,%s", latLng.latitude, latLng.longitude);
    }
}
