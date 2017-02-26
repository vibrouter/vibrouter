package io.github.vibrouter.fragments;

import android.content.Context;
import android.content.DialogInterface;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.github.vibrouter.BaseActivity;
import io.github.vibrouter.MainService;
import io.github.vibrouter.R;
import io.github.vibrouter.databinding.FragmentNavigationBinding;

public class NavigationFragment extends Fragment implements OnMapReadyCallback {
    private static final String TAG = NavigationFragment.class.getSimpleName();

    private static final String CURRENT_LOCATION_TITLE = "CurrentPosition";
    private static final String DESTINATION_LOCATION_TITLE = "DestinationPosition";

    private FragmentNavigationBinding mBinding;
    private MainService mService;

    private Marker mCurrentLocationMarker;
    private Marker mDestinationMarker;

    private List<Polyline> mNavigationRoute = new ArrayList<>();

    private GoogleMap mMap;

    private GoogleMap.OnMapClickListener mMapClickListener = new GoogleMap.OnMapClickListener() {
        @Override
        public void onMapClick(LatLng latLng) {
            if (mService != null) {
                removeRoutes();
                setDestination(latLng);
                mService.setDestination(latLng, new MainService.RouteSearchFinishCallback() {
                    @Override
                    public void onRouteSearchFinish(List<LatLng> fromCurrentWayPoint, List<LatLng> trainWayPoint, List<LatLng> toDestinationWayPoint) {
                        drawRoute(fromCurrentWayPoint);
                        drawRoute(trainWayPoint);
                        drawRoute(toDestinationWayPoint);
                    }
                });
            }
        }
    };

    private MainService.CurrentLocationListener mCurrentLocationListener = new MainService.CurrentLocationListener() {
        @Override
        public void onLocationChanged(LatLng location) {
            setOrigin(location);
        }

        @Override
        public void onRotationChanged(double rotation) {
            setRotation(rotation);
        }
    };

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_navigation, container, false);
        mBinding.navigationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleNavigation();
            }
        });
        createMap();
        return mBinding.getRoot();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mService = getBaseActivity().getService();
    }

    @Override
    public void onResume() {
        super.onResume();
        mService.setLocationListener(mCurrentLocationListener);
    }

    @Override
    public void onPause() {
        mService.unsetLocationListener();
        clearLocation();
        super.onPause();
    }

    @Override
    public void onDetach() {
        mService = null;
        super.onDetach();
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.i(TAG, "Map created!");
        mMap = googleMap;

        mMap.setIndoorEnabled(true);
        mMap.setOnMapClickListener(mMapClickListener);
    }

    private void createMap() {
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    private void startNavigation() {
        if (!isDestinationSet()) {
            new AlertDialog.Builder(this.getContext())
                    .setTitle(getResources().getString(R.string.alert_title))
                    .setMessage(getResources().getString(R.string.alert_body))
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // continue with delete
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
            return;
        }
        Log.i(TAG, "Start navigation!!");
        mService.startNavigation(new MainService.NavigationStatusListener() {
            @Override
            public void currentDirection(int direction, boolean arrived) {
                Log.i(TAG, "Navigation status. direction: " + direction + " arrived: " + arrived);
                showBanner(selectBannerToDisplay(direction, arrived));
            }
        });
    }

    private void stopNavigation() {
        mService.stopNavigation();
        removeBanner();
    }

    private boolean isDestinationSet() {
        return mDestinationMarker != null;
    }

    private float initialZoom() {
        final float ZOOM = 16f;
        if (ZOOM < mMap.getMinZoomLevel()) {
            return mMap.getMinZoomLevel();
        } else if (mMap.getMaxZoomLevel() < ZOOM) {
            return mMap.getMaxZoomLevel();
        }
        return ZOOM;
    }

    private void removeRoutes() {
        Iterator<Polyline> iterator = mNavigationRoute.iterator();
        while (iterator.hasNext()) {
            iterator.next().remove();
            iterator.remove();
        }
    }

    private void drawRoute(List<LatLng> route) {
        if (mMap == null) {
            return;
        }
        if (route == null) {
            return;
        }
        PolylineOptions options = new PolylineOptions();
        for (LatLng latLng : route) {
            options.add(latLng);
        }

        options.color(ContextCompat.getColor(this.getContext(), R.color.colorPolyline));
        options.width(getResources().getDimension(R.dimen.polyline_width));
        mNavigationRoute.add(mMap.addPolyline(options));
    }

    private void setOrigin(LatLng position) {
        MarkerOptions markerOptions = new MarkerOptions();
        // zoom to current position:
        if (mCurrentLocationMarker == null) {
            markerOptions.position(position);
            markerOptions.title(CURRENT_LOCATION_TITLE);
            markerOptions.icon(BitmapDescriptorFactory.fromResource(R.mipmap.marker));
            markerOptions.anchor(0.5f, 0.5f);

            mCurrentLocationMarker = mMap.addMarker(markerOptions);
            CameraPosition cameraPosition = new CameraPosition.Builder()
                    .target(position).zoom(initialZoom()).build();
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        } else {
            mCurrentLocationMarker.setPosition(position);
        }
    }

    private void setDestination(LatLng position) {
        if (mDestinationMarker != null) {
            mDestinationMarker.remove();
        }
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(position);
        markerOptions.title(DESTINATION_LOCATION_TITLE);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
        markerOptions.anchor(0.5f, 0.5f);
        mDestinationMarker = mMap.addMarker(markerOptions);
    }

    void resetCameraPosition() {
        if (mCurrentLocationMarker == null) {
            return;
        }
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(mCurrentLocationMarker.getPosition()).zoom(initialZoom()).build();
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }

    private void setRotation(double rotation) {
        if (mCurrentLocationMarker == null) {
            return;
        }
        mCurrentLocationMarker.setRotation((float) rotation);
    }

    private void clearLocation() {
        if (mCurrentLocationMarker != null) {
            mCurrentLocationMarker.remove();
            mCurrentLocationMarker = null;
        }
    }

    void toggleNavigation() {
        if (mService.isNavigating()) {
            removeBanner();
            stopNavigation();
        } else {
            startNavigation();
        }
    }

    private void showBanner(int imageId) {
        if (imageId < 0) {
            removeBanner();
            return;
        }
        if (mBinding.banner.getTag() != null && mBinding.banner.getTag().equals(imageId)) {
            return;
        }
        mBinding.banner.setTag(imageId);
        mBinding.banner.setAlpha(0.0f);
        mBinding.banner.setImageResource(imageId);
        mBinding.banner.setVisibility(View.VISIBLE);
        mBinding.banner.animate().alpha(1.0f).setDuration(300).start();
    }

    private void removeBanner() {
        mBinding.banner.setVisibility(View.INVISIBLE);
        mBinding.banner.setAlpha(0.0f);
    }

    private int selectBannerToDisplay(int direction, boolean arrived) {
        if (arrived) {
            return R.mipmap.goal;
        }
        switch (direction) {
            case MainService.NavigationStatusListener.NAVIGATING_FORWARD:
                return R.mipmap.forward;
            case MainService.NavigationStatusListener.NAVIGATING_LEFT:
                return R.mipmap.left;
            case MainService.NavigationStatusListener.NAVIGATING_RIGHT:
                return R.mipmap.right;
            default:
                return -1;
        }
    }

    private BaseActivity getBaseActivity() {
        return (BaseActivity) getActivity();
    }
}
