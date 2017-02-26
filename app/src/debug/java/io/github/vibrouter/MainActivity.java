package io.github.vibrouter;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.view.GravityCompat;
import android.view.MenuItem;

import java.util.Locale;

public class MainActivity extends BaseActivity {
    private Handler mHandler;
    private final long UPDATE_MILLIS = 10;
    private final Runnable mMillisUpdater = new Runnable() {
        @Override
        public void run() {
            updateTimeMillis();
        }
    };

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        switch (id) {
            case R.id.start_stop_chronometer:
                toggleChronometer(item);
                break;
            case R.id.start_stop_navigation:
                toggleNavigation(item);
                break;
            case R.id.go_to_current_position:
                resetCameraPosition();
                break;
            case R.id.enable_camera_reset:
                toggleCameraReset(item);
                break;
            case R.id.nav_share:
            case R.id.nav_send:
            default:
                break;
        }

        mBinding.drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void onPause() {
        stopChronometer();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mHandler = null;
        super.onDestroy();
    }

    private void toggleChronometer(MenuItem item) {
        if (item.getTitle().equals(getResources().getString(R.string.start_chronometer))) {
            startChronometer();
            item.setTitle(R.string.stop_chronometer);
        } else {
            stopChronometer();
            item.setTitle(R.string.start_chronometer);
        }
    }

    private void startChronometer() {
        mBinding.contentMain.chronometer.setBase(SystemClock.elapsedRealtime());
        mBinding.contentMain.chronometer.start();
        startUpdatingMillis();
    }

    private void stopChronometer() {
        stopUpdatingMillis();
        mBinding.contentMain.chronometer.stop();
    }

    private void startUpdatingMillis() {
        mBinding.contentMain.millis.setText("000");
        mHandler.postDelayed(mMillisUpdater, UPDATE_MILLIS);
    }

    private void stopUpdatingMillis() {
        mHandler.removeCallbacks(mMillisUpdater);
    }

    private void updateTimeMillis() {
        String millisString = mBinding.contentMain.millis.getText().toString();
        long nextMillis = (Long.parseLong(millisString) + UPDATE_MILLIS) % 1000;
        mBinding.contentMain.millis.setText(String.format(Locale.JAPAN, "%03d", nextMillis));
        mHandler.postDelayed(mMillisUpdater, UPDATE_MILLIS);
    }
}
