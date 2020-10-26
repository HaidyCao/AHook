package app.android.hook;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.TestLooperManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

public class AppComponentFactoryCompat {

    private static final String TAG = AppComponentFactoryCompat.class.getSimpleName();
    private final Callback mCallback;

    private Object mCurrentActivityThread;

    private static final int TYPE_SERVICE = 0;
    private static final int TYPE_CONTENT_PROVIDER = 1;
    private AppComponentFactoryClassLoader mServiceClassLoaderWrap;
    private AppComponentFactoryClassLoader mContextImplClassLoaderWrap;
    private InstrumentationWrap mInstrumentationWrap;

    private Application mApplication;

    @IntDef({TYPE_SERVICE, TYPE_CONTENT_PROVIDER})
    private @interface ClassLoaderCheckType {
    }

    public AppComponentFactoryCompat(Callback callback) {
        mCallback = callback;
    }

    public void onAttach(@NonNull Application application, @NonNull Context context) {
        mApplication = application;
        try {
            @SuppressLint("PrivateApi")
            Class<?> activityThreadClass = context.getClassLoader().loadClass("android.app.ActivityThread");
            Object currentActivityThread;

            currentActivityThread = Reflect.invokeStatic(activityThreadClass, "currentActivityThread");
            mCurrentActivityThread = currentActivityThread;

            // for activity
            Instrumentation instrumentation = (Instrumentation) Reflect.getField(currentActivityThread, "mInstrumentation");

            mInstrumentationWrap = new InstrumentationWrap(instrumentation, mCallback);
            Reflect.setField(currentActivityThread, "mInstrumentation", mInstrumentationWrap);

            // for content provider
            Object base = Reflect.getField(Application.class.getSuperclass(), application, "mBase");
            if (base == null) {
                throw new NoSuchFieldException(Application.class.getSuperclass() + ": mBase");
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ClassLoader classLoader = (ClassLoader) Reflect.getField(base, "mClassLoader");
                if (classLoader != null) {
                    mContextImplClassLoaderWrap = new AppComponentFactoryClassLoader(classLoader, TYPE_CONTENT_PROVIDER, mCallback);
                    Reflect.setField(base, "mClassLoader", mContextImplClassLoaderWrap);
                }
            }

            Object packageInfo = Reflect.getField(base, "mPackageInfo");

            ClassLoader classLoader = (ClassLoader) Reflect.getField(packageInfo, "mClassLoader");
            if (classLoader != null) {
                mContextImplClassLoaderWrap = new AppComponentFactoryClassLoader(classLoader, TYPE_CONTENT_PROVIDER, mCallback);
                Reflect.setField(packageInfo, "mClassLoader", mContextImplClassLoaderWrap);
            }
        } catch (ClassNotFoundException | NoSuchFieldException e) {
            Log.e(TAG, "onAttach: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public void onCreate(Application application) {
        Class<?> activityThreadClass = mCurrentActivityThread.getClass();

        // for service
        Object packageInfo = Reflect.invoke(mCurrentActivityThread, "peekPackageInfo",
                new Class[]{String.class, boolean.class},
                new Object[]{application.getPackageName(), true});
        Log.d(TAG, "onAttach: packageInfo = " + packageInfo);
        if (packageInfo != null) {
            ClassLoader classLoader = (ClassLoader) Reflect.getField(packageInfo, "mClassLoader");
            if (!(classLoader instanceof AppComponentFactoryClassLoader)) {
                mServiceClassLoaderWrap = new AppComponentFactoryClassLoader(classLoader, TYPE_SERVICE, mCallback);
                Reflect.setField(packageInfo, "mClassLoader", mContextImplClassLoaderWrap);
            }
        }

    }

    public static class DefaultCallback implements Callback {

        @Override
        public Activity instantiateActivity(ClassLoader cl, String className, Intent intent) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
            return (Activity) cl.loadClass(className).newInstance();
        }

        @Override
        public Class<?> loadServiceClass(ClassLoader cl, String className) throws ClassNotFoundException {
            return cl.loadClass(className);
        }

        @Override
        public Class<?> loadBroadcastReceiverClass(ClassLoader cl, String className) throws ClassNotFoundException {
            return cl.loadClass(className);
        }

        @Override
        public Class<?> loadContentProviderClass(ClassLoader cl, String className) throws ClassNotFoundException {
            return cl.loadClass(className);
        }
    }

    public interface Callback {

        Activity instantiateActivity(ClassLoader cl, String className, Intent intent) throws ClassNotFoundException, InstantiationException, IllegalAccessException;

        Class<?> loadServiceClass(ClassLoader cl, String className) throws ClassNotFoundException;

        Class<?> loadBroadcastReceiverClass(ClassLoader cl, String className) throws ClassNotFoundException;

        Class<?> loadContentProviderClass(ClassLoader cl, String className) throws ClassNotFoundException;
    }

    private static class AppComponentFactoryClassLoader extends ClassLoader {

        @ClassLoaderCheckType
        private int mCheckType;
        private Callback mCallback;

        public AppComponentFactoryClassLoader(ClassLoader parent, int checkType, Callback callback) {
            super(parent);
            mCheckType = checkType;
            mCallback = callback;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {

            {
                StackTraceElement[] elements = Thread.currentThread().getStackTrace();
                for (StackTraceElement element : elements) {
//                    Log.d(TAG, "loadClass: method = " + element.getMethodName());
                }
            } // */

            if (mCallback != null) {
                StackTraceElement[] elements = Thread.currentThread().getStackTrace();
                if (elements.length > 4) {
                    String method = elements[3].getMethodName();
                    if (method != null) {
                        switch (method) {
                            case "handleCreateService":
                                Log.d(TAG, "loadServiceClass: name = [" + name + "]");
                                return mCallback.loadServiceClass(getParent(), name);
                            case "installProvider":
                                Log.d(TAG, "loadContentProviderClass: name = [" + name + "]");
                                return mCallback.loadContentProviderClass(getParent(), name);
                            case "handleReceiver":
                                Log.d(TAG, "loadBroadcastReceiverClass: name = [" + name + "]");
                                return mCallback.loadBroadcastReceiverClass(getParent(), name);
                        }
                    }
                }
            }
            return super.loadClass(name);
        }
    }

    private static class InstrumentationWrap extends Instrumentation {

        Instrumentation mWrap;
        Callback mCallback;

        public InstrumentationWrap(Instrumentation wrap, Callback callback) {
            super();
            mWrap = wrap;
            mCallback = callback;
        }

        @RequiresApi(api = Build.VERSION_CODES.R)
        @Override
        public void callActivityOnPictureInPictureRequested(@NonNull Activity activity) {
            mWrap.callActivityOnPictureInPictureRequested(activity);
        }

        @Override
        public void onCreate(Bundle arguments) {
            mWrap.onCreate(arguments);
        }

        @Override
        public void start() {
            mWrap.start();
        }

        @Override
        public void onStart() {
            mWrap.onStart();
        }

        @Override
        public boolean onException(Object obj, Throwable e) {
            return mWrap.onException(obj, e);
        }

        @Override
        public void sendStatus(int resultCode, Bundle results) {
            mWrap.sendStatus(resultCode, results);
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void addResults(Bundle results) {
            mWrap.addResults(results);
        }

        @Override
        public void finish(int resultCode, Bundle results) {
            mWrap.finish(resultCode, results);
        }

        @Override
        public void setAutomaticPerformanceSnapshots() {
            mWrap.setAutomaticPerformanceSnapshots();
        }

        @Override
        public void startPerformanceSnapshot() {
            mWrap.startPerformanceSnapshot();
        }

        @Override
        public void endPerformanceSnapshot() {
            mWrap.endPerformanceSnapshot();
        }

        @Override
        public void onDestroy() {
            mWrap.onDestroy();
        }

        @Override
        public Context getContext() {
            return mWrap.getContext();
        }

        @Override
        public ComponentName getComponentName() {
            return mWrap.getComponentName();
        }

        @Override
        public Context getTargetContext() {
            return mWrap.getTargetContext();
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public String getProcessName() {
            return mWrap.getProcessName();
        }

        @Override
        public boolean isProfiling() {
            return mWrap.isProfiling();
        }

        @Override
        public void startProfiling() {
            mWrap.startProfiling();
        }

        @Override
        public void stopProfiling() {
            mWrap.stopProfiling();
        }

        @Override
        public void setInTouchMode(boolean inTouch) {
            mWrap.setInTouchMode(inTouch);
        }

        @Override
        public void waitForIdle(Runnable recipient) {
            mWrap.waitForIdle(recipient);
        }

        @Override
        public void waitForIdleSync() {
            mWrap.waitForIdleSync();
        }

        @Override
        public void runOnMainSync(Runnable runner) {
            mWrap.runOnMainSync(runner);
        }

        @Override
        public Activity startActivitySync(Intent intent) {
            return mWrap.startActivitySync(intent);
        }

        @Override
        public void addMonitor(ActivityMonitor monitor) {
            mWrap.addMonitor(monitor);
        }

        @Override
        public ActivityMonitor addMonitor(IntentFilter filter, ActivityResult result, boolean block) {
            return mWrap.addMonitor(filter, result, block);
        }

        @Override
        public ActivityMonitor addMonitor(String cls, ActivityResult result, boolean block) {
            return mWrap.addMonitor(cls, result, block);
        }

        @Override
        public boolean checkMonitorHit(ActivityMonitor monitor, int minHits) {
            return mWrap.checkMonitorHit(monitor, minHits);
        }

        @Override
        public Activity waitForMonitor(ActivityMonitor monitor) {
            return mWrap.waitForMonitor(monitor);
        }

        @Override
        public Activity waitForMonitorWithTimeout(ActivityMonitor monitor, long timeOut) {
            return mWrap.waitForMonitorWithTimeout(monitor, timeOut);
        }

        @Override
        public void removeMonitor(ActivityMonitor monitor) {
            mWrap.removeMonitor(monitor);
        }

        @Override
        public boolean invokeMenuActionSync(Activity targetActivity, int id, int flag) {
            return mWrap.invokeMenuActionSync(targetActivity, id, flag);
        }

        @Override
        public boolean invokeContextMenuAction(Activity targetActivity, int id, int flag) {
            return mWrap.invokeContextMenuAction(targetActivity, id, flag);
        }

        @Override
        public void sendStringSync(String text) {
            mWrap.sendStringSync(text);
        }

        @Override
        public void sendKeySync(KeyEvent event) {
            mWrap.sendKeySync(event);
        }

        @Override
        public void sendKeyDownUpSync(int key) {
            mWrap.sendKeyDownUpSync(key);
        }

        @Override
        public void sendCharacterSync(int keyCode) {
            mWrap.sendCharacterSync(keyCode);
        }

        @Override
        public void sendPointerSync(MotionEvent event) {
            mWrap.sendPointerSync(event);
        }

        @Override
        public void sendTrackballEventSync(MotionEvent event) {
            mWrap.sendTrackballEventSync(event);
        }

        @Override
        public Application newApplication(ClassLoader cl, String className, Context context) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
            return mWrap.newApplication(cl, className, context);
        }

        @Override
        public void callApplicationOnCreate(Application app) {
            mWrap.callApplicationOnCreate(app);
        }

        @Override
        public Activity newActivity(Class<?> clazz, Context context, IBinder token, Application application, Intent intent, ActivityInfo info, CharSequence title, Activity parent, String id, Object lastNonConfigurationInstance) throws InstantiationException, IllegalAccessException {
            Log.d(TAG, "newActivity() called with: clazz = [" + clazz + "], context = [" + context + "], token = [" + token + "], application = [" + application + "], intent = [" + intent + "], info = [" + info + "], title = [" + title + "], parent = [" + parent + "], id = [" + id + "], lastNonConfigurationInstance = [" + lastNonConfigurationInstance + "]");
            return mWrap.newActivity(clazz, context, token, application, intent, info, title, parent, id, lastNonConfigurationInstance);
        }

        @Override
        public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
            Log.d(TAG, "newActivity() called with: cl = [" + cl + "], className = [" + className + "], intent = [" + intent + "]");
            if (mCallback != null) {
                return mCallback.instantiateActivity(cl, className, intent);
            }
            return mWrap.newActivity(cl, className, intent);
        }

        @Override
        public void callActivityOnCreate(Activity activity, Bundle icicle) {
            mWrap.callActivityOnCreate(activity, icicle);
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void callActivityOnCreate(Activity activity, Bundle icicle, PersistableBundle persistentState) {
            mWrap.callActivityOnCreate(activity, icicle, persistentState);
        }

        @Override
        public void callActivityOnDestroy(Activity activity) {
            mWrap.callActivityOnDestroy(activity);
        }

        @Override
        public void callActivityOnRestoreInstanceState(Activity activity, Bundle savedInstanceState) {
            mWrap.callActivityOnRestoreInstanceState(activity, savedInstanceState);
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void callActivityOnRestoreInstanceState(Activity activity, Bundle savedInstanceState, PersistableBundle persistentState) {
            mWrap.callActivityOnRestoreInstanceState(activity, savedInstanceState, persistentState);
        }

        @Override
        public void callActivityOnPostCreate(Activity activity, Bundle icicle) {
            mWrap.callActivityOnPostCreate(activity, icicle);
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void callActivityOnPostCreate(Activity activity, Bundle icicle, PersistableBundle persistentState) {
            mWrap.callActivityOnPostCreate(activity, icicle, persistentState);
        }

        @Override
        public void callActivityOnNewIntent(Activity activity, Intent intent) {
            mWrap.callActivityOnNewIntent(activity, intent);
        }

        @Override
        public void callActivityOnStart(Activity activity) {
            mWrap.callActivityOnStart(activity);
        }

        @Override
        public void callActivityOnRestart(Activity activity) {
            mWrap.callActivityOnRestart(activity);
        }

        @Override
        public void callActivityOnResume(Activity activity) {
            mWrap.callActivityOnResume(activity);
        }

        @Override
        public void callActivityOnStop(Activity activity) {
            mWrap.callActivityOnStop(activity);
        }

        @Override
        public void callActivityOnSaveInstanceState(Activity activity, Bundle outState) {
            mWrap.callActivityOnSaveInstanceState(activity, outState);
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void callActivityOnSaveInstanceState(Activity activity, Bundle outState, PersistableBundle outPersistentState) {
            mWrap.callActivityOnSaveInstanceState(activity, outState, outPersistentState);
        }

        @Override
        public void callActivityOnPause(Activity activity) {
            mWrap.callActivityOnPause(activity);
        }

        @Override
        public void callActivityOnUserLeaving(Activity activity) {
            mWrap.callActivityOnUserLeaving(activity);
        }

        @Override
        public void startAllocCounting() {
            mWrap.startAllocCounting();
        }

        @Override
        public void stopAllocCounting() {
            mWrap.stopAllocCounting();
        }

        @Override
        public Bundle getAllocCounts() {
            return mWrap.getAllocCounts();
        }

        @Override
        public Bundle getBinderCounts() {
            return mWrap.getBinderCounts();
        }

        @Override
        public UiAutomation getUiAutomation() {
            return mWrap.getUiAutomation();
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public UiAutomation getUiAutomation(int flags) {
            return mWrap.getUiAutomation(flags);
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public TestLooperManager acquireLooperManager(Looper looper) {
            return mWrap.acquireLooperManager(looper);
        }

        @RequiresApi(api = Build.VERSION_CODES.P)
        @NonNull
        @Override
        public Activity startActivitySync(@NonNull Intent intent, @Nullable Bundle options) {
            return mWrap.startActivitySync(intent, options);
        }
    }
}
