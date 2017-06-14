package growing.com.recording;

import android.app.Application;
import android.util.Log;

import java.io.IOException;
import java.net.SocketException;

import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.plugins.RxJavaPlugins;
import growing.com.recording.data.data.AppData;
import growing.com.recording.data.data.local.PreferencesHelper;
import growing.com.recording.service.ForegroundService;

/**
 * File: BaseApplication.java
 * Author: ejiang
 * Version: V100R001C01
 * Create: 2017-06-14 10:27
 */

public class BaseApplication extends Application {
    private static BaseApplication sAppInstance;

    private AppData mAppData;
    private MainActivityViewModel mMainActivityViewModel;
    private PreferencesHelper mPreferencesHelper;

//    private RefWatcher refWatcher;

    @Override
    public void onCreate() {
        super.onCreate();
        sAppInstance = this;
//        if (LeakCanary.isInAnalyzerProcess(this)) {
//            // This process is dedicated to LeakCanary for heap analysis.
//            // You should not init your app in this process.
//            return;
//        }
//        refWatcher = LeakCanary.install(this);

        RxJavaPlugins.setErrorHandler(e -> {
            if (e instanceof UndeliverableException) {
                e = e.getCause();
            }
            if ((e instanceof IOException) || (e instanceof SocketException)) {
                // fine, irrelevant network problem or API that throws on cancellation
                return;
            }
            if (e instanceof InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return;
            }
            if ((e instanceof NullPointerException) || (e instanceof IllegalArgumentException)) {
                // that's likely a bug in the application
                Log.e(">>>>>> ", "likely a bug in the application", e);
                return;
            }
            if (e instanceof IllegalStateException) {
                // that's a bug in RxJava or in a custom operator
                Log.e(">>>>>>", "a bug in RxJava or in a custom operator", e);
                return;
            }
            Log.e(">>>>>>", "Undeliverable exception received, not sure what to do", e);
        });


        mAppData = new AppData(this);
        mMainActivityViewModel = new MainActivityViewModel(this);
        mPreferencesHelper = new PreferencesHelper(this);
        startService(ForegroundService.getStartIntent(this));
    }

    public static MainActivityViewModel getMainActivityViewModel() {
        return sAppInstance.mMainActivityViewModel;
    }

    public static AppData getAppData() {
        return sAppInstance.mAppData;
    }

    public static PreferencesHelper getAppPreference() {
        return sAppInstance.mPreferencesHelper;
    }

//    public static RefWatcher getRafWatcher() {
//        return sAppInstance.refWatcher;
//    }
}
