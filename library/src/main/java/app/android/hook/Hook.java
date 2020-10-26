package app.android.hook;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class Hook {

    private static final String TAG = Hook.class.getSimpleName();

    @SuppressLint("StaticFieldLeak")
    private static IActivityManagerHandler sIActivityManagerHandler;

    @SuppressLint("StaticFieldLeak")
    private static BaseServiceHandler sIPackageManagerHandler;

    @SuppressLint("StaticFieldLeak")
    private static IServiceManagerHandler sIServiceManagerHandler;

    @SuppressLint("StaticFieldLeak")
    private static IActivityTaskManagerHandler sIActivityTaskManagerHandler;

    @SuppressLint("StaticFieldLeak")
    private static IAutofillManagerHandler sIAutofillManagerHandler;

    static final Map<String, ServiceManagerProxyHandler> sIContentProviderProxyHandler = new HashMap<>();

    public static void hookAll(Application application) throws Exception {
        Log.d(TAG, "hookAll() called with: application = [" + application + "]");
        hookServiceManager(application);
        hookActivityManager(application);
        hookPackageManager(application);
        hookActivityTaskManager();
    }

    public static void hookServiceManager(final Application application) {
        Class<?> serviceManagerClass = Reflect.getClass(application.getClassLoader(), "android.os.ServiceManager");
        Object iServiceManager = Reflect.invokeStatic(serviceManagerClass, "getIServiceManager");
        if (iServiceManager == null) {
            throw new IllegalStateException("getIServiceManager failed");
        }

        IServiceManagerHandler proxy = new IServiceManagerHandler(application, iServiceManager);
        proxy.registerProxyHandler("getService", new ServiceManagerProxyHandler() {

            @Override
            public Object handle(Object proxy, Object origin, Method method, Object[] args) throws Throwable {
                Log.d(TAG, "handle: args = " + Arrays.toString(args));
                if (args.length > 0 && args[0] instanceof String) {
                    String name = args[0].toString();

                    Object iBinder = method.invoke(origin, args);
                    if (iBinder == null) {
                        return null;
                    }

                    IBinder binder = (IBinder) iBinder;
                    switch (name) {
                        case "activity":
                            return iBinderHook(application, binder, sIActivityManagerHandler = new IActivityManagerHandler(application, binder));
                        case "activity_task":
                            return iBinderHookActivityTaskManager(application, binder);
                        case "autofill":
                            return iBinderHook(application, binder, sIAutofillManagerHandler = new IAutofillManagerHandler(application, binder));
                        case "package":
                            return iBinderHook(application, binder, sIPackageManagerHandler = new IPackageManagerHandler(application, binder));
                        default:
                            return iBinder;
                    }
                }
                return method.invoke(origin, args);
            }
        });
        sIServiceManagerHandler = proxy;
        Object iServiceManagerProxy = Proxy.newProxyInstance(application.getClassLoader(), iServiceManager.getClass().getInterfaces(), proxy);
        Reflect.setStaticField(serviceManagerClass, "sServiceManager", iServiceManagerProxy);
    }

    public static void hookActivityManager(Application application) throws Exception {
        Log.d(TAG, "hookActivityManager() called with: application = [" + application + "]");

        Class<?> amClass = Reflect.getClass(application.getClassLoader(), "android.app.ActivityManager");
        Object iActivityManagerHolder;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Class<?> amnClass = Reflect.getClass(application.getClassLoader(), "android.app.ActivityManagerNative");
            iActivityManagerHolder = Reflect.getStaticField(amnClass, "gDefault");

            if (iActivityManagerHolder == null) {
                throw new Exception("android.app.ActivityManagerNative.gDefault is null");
            }
        } else {
            iActivityManagerHolder = Reflect.getStaticField(amClass, "IActivityManagerSingleton");

            if (iActivityManagerHolder == null) {
                throw new Exception("android.app.ActivityManager.IActivityManagerSingleton is null");
            }
        }

        Reflect.setField(iActivityManagerHolder, iActivityManagerHolder.getClass().getSuperclass(), "mInstance", null);
        Reflect.invokeStatic(amClass, "getService");
    }

    public static void hookActivityTaskManager() {
        if (sIActivityTaskManagerHandler != null) {
            return;
        }

        // clear ActivityTaskManager instance
        Class<?> activityTaskManagerClass = Reflect.getClass("android.app.ActivityTaskManager");
        if (activityTaskManagerClass == null) {
            Log.w(TAG, "hookActivityTaskManager: getClass android.app.ActivityTaskManager failed");
            return;
        }

        Object iActivityTaskManagerSingleton = Reflect.getStaticField(activityTaskManagerClass, "IActivityTaskManagerSingleton");
        Reflect.setField(iActivityTaskManagerSingleton, iActivityTaskManagerSingleton.getClass().getSuperclass(), "mInstance", null);

        Reflect.invokeStatic(Reflect.getClass("android.app.ActivityManager"), "getTaskService");
    }

    public static void hookPackageManager(Application application) throws Exception {
        Log.d(TAG, "hookPackageManager() called with: application = [" + application + "]");
        Class<?> activityThreadClass = Reflect.getClass(application.getClassLoader(), "android.app.ActivityThread");

        Reflect.setStaticField(activityThreadClass, "sPackageManager", null);

        Context contextImpl = application.getBaseContext();
        if (!"android.app.ContextImpl".equals(contextImpl.getClass().getName())) {
            throw new Exception("application context is changed: " + contextImpl.getClass().getName());
        }
        Reflect.setField(contextImpl, "mPackageManager", null);
        Object map = Reflect.getStaticField(Reflect.getClass("android.os.ServiceManager"), "sCache");
        if (map != null) {
            Log.d(TAG, "hookPackageManager: " + new ArrayList<>((Set<?>) Reflect.invoke(map, "keySet")));
            Reflect.invoke(map, "remove", new Class[]{Object.class}, new Object[]{"package"});
        }
        contextImpl.getPackageManager();
    }

    static IBinder iBinderHookActivityTaskManager(final Context context, final IBinder iBinder) {
        IBinderHandler handler = new IBinderHandler(context, iBinder, null);
        handler.registerProxyHandler("queryLocalInterface", new ServiceManagerProxyHandler() {

            @Override
            public Object handle(Object proxy, Object origin, Method method, Object[] args) throws Throwable {
                Object originProxy = method.invoke(origin, args);
                if (originProxy == null) {
                    return null;
                }

                Log.d(TAG, "iBinderHookActivityTaskManager() called with: origin = [" + origin + "], method = [" + method + "], args = [" + Arrays.toString(args) + "]");
                return Proxy.newProxyInstance(context.getClassLoader(), originProxy.getClass().getInterfaces(), sIActivityTaskManagerHandler = new IActivityTaskManagerHandler(context, originProxy));
            }
        });

        return (IBinder) Proxy.newProxyInstance(context.getClassLoader(), iBinder.getClass().getInterfaces(), handler);
    }

    static IBinder iBinderHook(Context context, final IBinder iBinder, IBinderHook hook) {
        final AtomicReference<Object> proxyHolder = new AtomicReference<>();

        IBinderHandler handler = new IBinderHandler(context, iBinder, null);
        handler.setQueryLocalInterface(new IBinderHandler.IQueryLocalInterface() {
            @Override
            public Object queryLocalInterface() {
                Log.d(TAG, "queryLocalInterface: " + iBinder.getClass());
                return proxyHolder.get();
            }
        });
        proxyHolder.set(hook.iBinderHook());

        return (IBinder) Proxy.newProxyInstance(context.getClassLoader(), iBinder.getClass().getInterfaces(), handler);
    }

    interface IBinderHook {

        Object iBinderHook();
    }

    private static void checkArgs(String methodName, ServiceManagerProxyHandler handler) {
        if (methodName == null) {
            throw new IllegalArgumentException("methodName cannot be null");
        }

        if (handler == null) {
            throw new IllegalArgumentException("handler cannot be null");
        }
    }

    public static void registerActivityManagerHandler(String methodName, ServiceManagerProxyHandler handler) {
        checkArgs(methodName, handler);

        if (sIActivityManagerHandler == null) {
            throw new IllegalStateException("you should call Hook.hookActivityManager first");
        }

        sIActivityManagerHandler.registerProxyHandler(methodName, handler);
    }

    public static void registerContentProviderHandler(String methodName, ServiceManagerProxyHandler handler) {
        checkArgs(methodName, handler);

        sIContentProviderProxyHandler.put(methodName, handler);
    }

    public static void registerPackageManagerHandler(String methodName, ServiceManagerProxyHandler handler) {
        checkArgs(methodName, handler);

        if (sIPackageManagerHandler == null) {
            throw new IllegalStateException("you should call Hook.hookActivityManager first");
        }

        sIPackageManagerHandler.registerProxyHandler(methodName, handler);
    }

    public static void registerActivityTaskManager(String methodName, ServiceManagerProxyHandler handler) {
        checkArgs(methodName, handler);

        if (sIActivityTaskManagerHandler == null) {
            throw new IllegalStateException("you should call Hook.hookActivityTaskManager first");
        }

        sIActivityTaskManagerHandler.registerProxyHandler(methodName, handler);
    }
}
