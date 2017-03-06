package io.github.vibrouter.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.maps.model.LatLng;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Collections;

import io.github.vibrouter.models.DirectionsApiResult;
import io.github.vibrouter.utils.DirectionsApiUtil;

public class DirectionsApi implements RouteFinder {
    private final Gson mGson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private RequestQueue mRequestQueue;

    public DirectionsApi(Context context) {
        mRequestQueue = Volley.newRequestQueue(context);
    }

    @Override
    public void findRoute(LatLng origin, LatLng destination, final OnRouteFoundCallback callback) {
        if (origin == null || destination == null) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    callback.onRouteFound(Collections.<LatLng>emptyList());
                }
            });
            return;
        }
        cancelPendingRequests();

        sendRouteSearchRequestBetween(origin, destination, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                DirectionsApiResult result = mGson.fromJson(response, DirectionsApiResult.class);
                if (callback != null) {
                    callback.onRouteFound(result.getWaypoints());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                callback.onRouteFound(Collections.<LatLng>emptyList());
            }
        });
    }

    private void cancelPendingRequests() {
        mRequestQueue.cancelAll(new RequestQueue.RequestFilter() {
            @Override
            public boolean apply(Request<?> request) {
                return true;
            }
        });
    }

    private void sendRouteSearchRequestBetween(LatLng origin, LatLng destination,
                                               Response.Listener<String> listener,
                                               Response.ErrorListener errorListener) {
        String url = DirectionsApiUtil.buildUrl(origin, destination, DirectionsApiUtil.TRAVEL_MODE_WALKING);
        StringRequest request = new StringRequest(Request.Method.GET, url, listener, errorListener);
        mRequestQueue.add(request);
    }
}
