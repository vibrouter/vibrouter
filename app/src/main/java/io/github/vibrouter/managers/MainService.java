package io.github.vibrouter.managers;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;

import io.github.vibrouter.hardware.VibrationController;
import io.github.vibrouter.models.Route;

public class MainService extends Service {
    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    private PositionManager mPositionManager;
    private VibrationController mVibrationController;
    private Navigator mNavigator;

    public class LocalBinder extends Binder {
        public MainService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MainService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mPositionManager = new PositionManager(this);
        mVibrationController = new VibrationController(this);
        mNavigator = new Navigator(mPositionManager, mVibrationController);
    }

    @Override
    public void onDestroy() {
        mNavigator.stopNavigation();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    public PositionManager getPositionManager() {
        return mPositionManager;
    }

    public Navigator getNavigator() {
        return mNavigator;
    }
}
