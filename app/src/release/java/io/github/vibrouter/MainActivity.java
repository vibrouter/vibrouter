package io.github.vibrouter;

import android.support.v4.view.GravityCompat;
import android.view.MenuItem;

public class MainActivity extends BaseActivity {
    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        switch (id) {
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
}
