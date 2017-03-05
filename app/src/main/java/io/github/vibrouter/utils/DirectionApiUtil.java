package io.github.vibrouter.utils;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;

public class DirectionApiUtil {
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
}
