package io.github.vibrouter.fragments;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.databinding.DataBindingUtil;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.PermissionChecker;
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

import io.github.vibrouter.managers.MainService;
import io.github.vibrouter.R;
import io.github.vibrouter.databinding.FragmentNavigationBinding;
import io.github.vibrouter.managers.Navigator;
import io.github.vibrouter.managers.PositionManager;
import io.github.vibrouter.models.Coordinate;
import io.github.vibrouter.models.Route;
import io.github.vibrouter.network.DirectionsApi;
import io.github.vibrouter.network.RouteFinder;

import static android.content.Context.BIND_AUTO_CREATE;

public class NavigationFragment extends Fragment implements OnMapReadyCallback {
    private static final String TAG = NavigationFragment.class.getSimpleName();

    private static final String CURRENT_LOCATION_TITLE = "CurrentPosition";
    private static final String DESTINATION_LOCATION_TITLE = "DestinationPosition";

    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private FragmentNavigationBinding mBinding;

    private Marker mCurrentLocationMarker;
    private Marker mDestinationMarker;

    private List<Polyline> mRoutePolyline = new ArrayList<>();

    private GoogleMap mMap;
    private MainService mService;
    private RouteFinder mRouteFinder;
    private PositionManager mPositionManager;
    private Navigator mNavigator;

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "onServiceConnected");
            mService = ((MainService.LocalBinder) service).getService();
            mPositionManager = mService.getPositionManager();
            mPositionManager.registerOnPositionChangeListener(mOnPositionChangeListener);
            mNavigator = mService.getNavigator();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mNavigator = null;
            mPositionManager.unregisterOnPositionChangeListener(mOnPositionChangeListener);
            mPositionManager = null;
            mService = null;
        }
    };

    private GoogleMap.OnMapClickListener mMapClickListener = new GoogleMap.OnMapClickListener() {
        @Override
        public void onMapClick(LatLng destination) {
            if (mService == null) {
                return;
            }
            removeRoutes();
            markDestination(destination);
            mRouteFinder.findRoute(mCurrentLocationMarker.getPosition(), destination,
                    new RouteFinder.OnRouteFoundCallback() {
                        @Override
                        public void onRouteFound(List<LatLng> route) {
                            drawRoute(route);
                            mNavigator.setRoute(new Route(route));
                        }
                    });
        }
    };

    private PositionManager.OnPositionChangeListener mOnPositionChangeListener =
            new PositionManager.OnPositionChangeListener() {
                @Override
                public void onPositionChange(Coordinate position) {
                    if (!isMapReady()) {
                        return;
                    }
                    setCurrentPosition(position);
                }
            };

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mRouteFinder = new DirectionsApi(this.getContext());
        mBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_navigation, container, false);
        mBinding.navigationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleNavigationButton();
            }
        });
        return mBinding.getRoot();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!hasPermissionGranted()) {
            requestPermissions();
            return;
        }
        Intent intent = new Intent(this.getContext(), MainService.class);
        getActivity().bindService(intent, mServiceConnection, BIND_AUTO_CREATE);
        createMap();
    }

    @Override
    public void onPause() {
        if (mService != null) {
            mPositionManager.unregisterOnPositionChangeListener(mOnPositionChangeListener);
            getActivity().unbindService(mServiceConnection);
            mService = null;
        }
        removeCurrentLocationMarker();
        super.onPause();
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onDestroyView() {
        if (mMap != null) {
            mMap.clear();
        }
        mMap = null;
        mRouteFinder = null;
        super.onDestroyView();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                if (0 < grantResults.length) {
                    createMap();
                }
                break;
            default:
                break;
        }
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
        mMap = googleMap;
        mMap.setIndoorEnabled(true);
        mMap.setOnMapClickListener(mMapClickListener);
    }

    private boolean isMapReady() {
        return mMap != null;
    }

    private void createMap() {
        if (isMapReady()) {
            return;
        }
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    private void startNavigation() {
        if (!isDestinationSet()) {
            new AlertDialog.Builder(this.getContext())
                    .setTitle(getResources().getString(R.string.alert_title))
                    .setMessage(getResources().getString(R.string.alert_body))
                    .setPositiveButton(android.R.string.yes, null)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
            return;
        }
        if (!mNavigator.isRouteSet()) {
            return;
        }
        Log.i(TAG, "Start navigation!!");
        mNavigator.startNavigation(new Navigator.NavigationStatusListener() {
            @Override
            public void currentDirection(int direction) {
                int bannerId = selectBanner(direction);
                showBanner(bannerId);
            }

            @Override
            public void arrived() {
                showBanner(R.drawable.banner_goal);
            }
        });
    }

    private void stopNavigation() {
        mNavigator.pauseNavigation();
        removeBanner();
    }

    private boolean isDestinationSet() {
        return mDestinationMarker != null;
    }

    private float initialZoom() {
        final float ZOOM = getResources().getInteger(R.integer.map_default_zoom);
        if (ZOOM < mMap.getMinZoomLevel()) {
            return mMap.getMinZoomLevel();
        } else if (mMap.getMaxZoomLevel() < ZOOM) {
            return mMap.getMaxZoomLevel();
        }
        return ZOOM;
    }

    private void removeRoutes() {
        Iterator<Polyline> iterator = mRoutePolyline.iterator();
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
        mRoutePolyline.add(mMap.addPolyline(options));
    }

    private void markDestination(LatLng position) {
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

    private void setCurrentPosition(Coordinate position) {
        LatLng location = position.getLocation();
        double rotation = position.getRotation();
        MarkerOptions markerOptions = new MarkerOptions();
        // zoom to current position on initialization
        if (mCurrentLocationMarker == null) {
            markerOptions.position(location);
            markerOptions.title(CURRENT_LOCATION_TITLE);
            markerOptions.icon(BitmapDescriptorFactory.fromResource(R.mipmap.marker));
            markerOptions.anchor(0.5f, 0.5f);

            mCurrentLocationMarker = mMap.addMarker(markerOptions);
            CameraPosition cameraPosition = new CameraPosition.Builder()
                    .target(location).zoom(initialZoom()).build();
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        } else {
            mCurrentLocationMarker.setPosition(location);
            mCurrentLocationMarker.setRotation((float) rotation);
        }
    }

    private void removeCurrentLocationMarker() {
        if (mCurrentLocationMarker == null) {
            return;
        }
        mCurrentLocationMarker.remove();
        mCurrentLocationMarker = null;
    }

    private void toggleNavigationButton() {
        if (mService == null) {
            return;
        }
        if (mNavigator.isNavigating()) {
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
        mBinding.banner.setTag(null);
        mBinding.banner.setVisibility(View.INVISIBLE);
        mBinding.banner.setAlpha(0.0f);
    }

    private int selectBanner(int direction) {
        switch (direction) {
            case Navigator.NavigationStatusListener.NAVIGATING_FORWARD:
                return R.drawable.banner_forward;
            case Navigator.NavigationStatusListener.NAVIGATING_LEFT:
                return R.drawable.banner_left;
            case Navigator.NavigationStatusListener.NAVIGATING_RIGHT:
                return R.drawable.banner_right;
            default:
                return -1;
        }
    }

    private boolean hasPermissionGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (PermissionChecker.checkSelfPermission(this.getContext(), permission)
                    == PermissionChecker.PERMISSION_DENIED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        requestPermissions(REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
    }
}
