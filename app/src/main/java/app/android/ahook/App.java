package app.android.ahook;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import app.android.hook.AppComponentFactoryCompat;
import app.android.hook.Hook;

public class App extends Application {

    private static final String TAG = App.class.getSimpleName();

    private AppComponentFactoryCompat mAppComponentFactoryCompat;

    @Override
    public void onCreate() {
        super.onCreate();
        if (mAppComponentFactoryCompat != null) {
            mAppComponentFactoryCompat.onCreate(this);
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            mAppComponentFactoryCompat = new AppComponentFactoryCompat(new AppComponentFactoryCompat.DefaultCallback());

            mAppComponentFactoryCompat.onAttach(this, base);
        }

        try {
            Hook.hookAll(this);
        } catch (Exception e) {
            Log.e(TAG, "attachBaseContext: " + e.getMessage(), e);
        }
    }
}
