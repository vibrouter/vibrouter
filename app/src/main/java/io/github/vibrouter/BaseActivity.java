package io.github.vibrouter;

import android.Manifest;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;

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

import io.github.vibrouter.databinding.MainActivityBinding;

public abstract class BaseActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, OnMapReadyCallback {
    private static final String CURRENT_LOCATION_TITLE = "CurrentPosition";
    private static final String DESTINATION_LOCATION_TITLE = "DestinationPosition";

    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.VIBRATE
    };
    private static final int PERMISSION_REQUEST_CODE = 1;

    private static final String TAG = BaseActivity.class.getSimpleName();

    private final int UI_VISIBILITY_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

    private GoogleMap mMap;
    private Marker mCurrentLocationMarker;
    private Marker mDestinationMarker;

    private List<Polyline> mPolylinesOnMap = new ArrayList<>();

    private boolean mResetCameraPositionEnabled = true;

    MainActivityBinding mBinding;

    private MainService.CurrentLocationListener mCurrentLocationListener = new MainService.CurrentLocationListener() {
        @Override
        public void onLocationChanged(LatLng location) {
            if (resetCameraPositionEnabled()) {
                resetCameraPosition();
            }
            setOrigin(location);
        }

        @Override
        public void onRotationChanged(double rotation) {
            setRotation(rotation);
        }
    };

    private MainService mService;
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = ((MainService.LocalBinder) service).getService();
            mService.setLocationListener(mCurrentLocationListener);
            Toast.makeText(BaseActivity.this, "Connected to service", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.main_activity);

        getWindow().getDecorView().setSystemUiVisibility(UI_VISIBILITY_FLAGS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, mBinding.drawerLayout, null, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        mBinding.drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        mBinding.navView.setNavigationItemSelectedListener(this);
    }

    @Override
    protected void onDestroy() {
        mBinding = null;
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (needPermissions()) {
            Log.i(TAG, "Requesting permissions!");
            requestNecessaryPermissions();
        } else {
            Log.i(TAG, "Map creating!");
            createMap();
            Intent intent = new Intent(this, MainService.class);
            startService(intent);
            bindService(intent, mServiceConnection, BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onPause() {
        if (mService != null) {
            mService.unsetLocationListener();
            unbindService(mServiceConnection);
            mService = null;
        }
        clearLocation();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                // There are nothing to do
                break;
            default:
                // Do nothing
                break;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            this.getWindow().getDecorView().setSystemUiVisibility(UI_VISIBILITY_FLAGS);
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = mBinding.drawerLayout;
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
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
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    private void removeRoutes() {
        Iterator<Polyline> iterator = mPolylinesOnMap.iterator();
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
        options.color(ContextCompat.getColor(this, R.color.colorPolyline));
        options.width(getResources().getDimension(R.dimen.polyline_width));
        mPolylinesOnMap.add(mMap.addPolyline(options));
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

    private boolean isDestinationSet() {
        return mDestinationMarker != null;
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

    private float initialZoom() {
        final float ZOOM = 15f;
        if (ZOOM < mMap.getMinZoomLevel()) {
            return mMap.getMinZoomLevel();
        } else if (mMap.getMaxZoomLevel() < ZOOM) {
            return mMap.getMaxZoomLevel();
        }
        return ZOOM;
    }

    private boolean needPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Permission: " + permission + " is not granted yet by user");
                return true;
            }
        }
        return false;
    }

    private void requestNecessaryPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        requestPermissions(REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
    }

    void toggleNavigation(MenuItem item) {
        if (item.getTitle().equals(getResources().getString(R.string.start_navigation))) {
            startNavigation();
            item.setTitle(R.string.stop_navigation);
        } else {
            removeBanner();
            stopNavigation();
            item.setTitle(R.string.start_navigation);
        }
    }

    private void startNavigation() {
        if (!isDestinationSet()) {
            new AlertDialog.Builder(this)
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
        if (mService != null) {
            mService.startNavigation(new MainService.NavigationStatusListener() {
                @Override
                public void currentDirection(int direction, boolean arrived) {
                    showBanner(selectBannerToDisplay(direction, arrived));
                }
            });
        }
    }

    private void stopNavigation() {
        if (mService != null) {
            mService.stopNavigation();
            removeBanner();
        }
    }

    private boolean resetCameraPositionEnabled() {
        return mResetCameraPositionEnabled;
    }

    void toggleCameraReset(MenuItem item) {
        if (item.getTitle().equals(getResources().getString(R.string.enable_camera_position_reset))) {
            mResetCameraPositionEnabled = true;
            item.setTitle(R.string.disable_camera_position_reset);
        } else {
            mResetCameraPositionEnabled = false;
            item.setTitle(R.string.enable_camera_position_reset);
        }
    }

    private void showBanner(int imageId) {
        if (imageId < 0) {
            removeBanner();
            return;
        }
        if (mBinding.contentMain.banner.getTag() != null &&
                mBinding.contentMain.banner.getTag().equals(imageId)) {
            return;
        }
        mBinding.contentMain.banner.setTag(imageId);
        mBinding.contentMain.banner.setAlpha(0.0f);
        mBinding.contentMain.banner.setImageResource(imageId);
        mBinding.contentMain.banner.setVisibility(View.VISIBLE);
        mBinding.contentMain.banner.animate().alpha(1.0f).setDuration(300).start();
    }

    private void removeBanner() {
        mBinding.contentMain.banner.setVisibility(View.INVISIBLE);
        mBinding.contentMain.banner.setAlpha(0.0f);
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
}
