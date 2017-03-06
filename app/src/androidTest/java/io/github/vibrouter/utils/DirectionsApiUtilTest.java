package io.github.vibrouter.utils;

import android.support.test.runner.AndroidJUnit4;

import com.google.android.gms.maps.model.LatLng;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DirectionsApiUtilTest {
    @Test
    public void buildUrl_walking() throws Exception {
        double originLat = 12.345;
        double originLng = 56.789;
        LatLng testOrigin = new LatLng(originLat, originLng);

        double destinationLat = 23.456;
        double destinationLng = 67.890;
        LatLng testDestination = new LatLng(destinationLat, destinationLng);

        String url = DirectionsApiUtil.buildUrl(testOrigin, testDestination,
                DirectionsApiUtil.TRAVEL_MODE_WALKING);

        final String EXPECTED = "https://maps.googleapis.com/maps/api/directions/json?origin=12.345%2C56.789&destination=23.456%2C67.89&sensor=false&language=ja&mode=walking";
        Assertions.assertThat(url).isEqualTo(EXPECTED);
    }

    @Test
    public void buildUrl_driving() throws Exception {
        double originLat = 23.456;
        double originLng = 67.890;
        LatLng testOrigin = new LatLng(originLat, originLng);

        double destinationLat = 12.345;
        double destinationLng = 56.789;
        LatLng testDestination = new LatLng(destinationLat, destinationLng);

        String url = DirectionsApiUtil.buildUrl(testOrigin, testDestination,
                DirectionsApiUtil.TRAVEL_MODE_DRIVING);

        final String EXPECTED = "https://maps.googleapis.com/maps/api/directions/json?origin=23.456%2C67.89&destination=12.345%2C56.789&sensor=false&language=ja&mode=driving";
        Assertions.assertThat(url).isEqualTo(EXPECTED);
    }
}