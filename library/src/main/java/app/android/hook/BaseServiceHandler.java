package app.android.hook;

import android.content.Context;
import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

public class BaseServiceHandler implements InvocationHandler, Hook.IBinderHook {

    private static final String TAG = BaseServiceHandler.class.getSimpleName();
    protected final Map<String, ServiceManagerProxyHandler> mServiceManagerProxyHandlerList = new TreeMap<>();
    protected ServiceManagerProxyHandler mServiceManagerProxyHandler;
    private final Object mOrigin;
    private final Context mContext;

    public BaseServiceHandler(Context context, String stubClassName, IBinder iBinder) {
        this(context, Reflect.invokeStatic(Reflect.getClass(context.getClassLoader(), stubClassName), "asInterface", new Class[]{IBinder.class}, new Object[]{iBinder}));
    }

    public BaseServiceHandler(Context context, Object origin) {
        mContext = context;
        mOrigin = origin;
    }

    protected void registerAsBinder() {
        registerProxyHandler("asBinder", new ServiceManagerProxyHandler() {

            @Override
            public Object handle(Object proxy, Object origin, Method method, Object[] args) throws Throwable {
                Object iBinder = method.invoke(origin, args);
                return Proxy.newProxyInstance(mContext.getClassLoader(), new Class[]{IBinder.class}, new IBinderHandler(mContext, iBinder, proxy));
            }
        });
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Log.d(TAG, "invoke() called with: method = [" + method + "], args = [" + Arrays.toString(args) + "]");
        ServiceManagerProxyHandler handler = mServiceManagerProxyHandler;
        if (handler != null) {
            return handler.handle(proxy, mOrigin, method, args);
        }

        synchronized (this) {
            handler = mServiceManagerProxyHandlerList.get(method.getName());
        }

        if (handler != null) {
            return handler.handle(proxy, mOrigin, method, args);
        }

        return method.invoke(mOrigin, args);
    }

    protected void registerProxyHandler(String methodName, ServiceManagerProxyHandler handler) {
        if (methodName == null || handler == null) {
            throw new IllegalArgumentException("methodName and handler cannot be null");
        }

        synchronized (this) {
            mServiceManagerProxyHandlerList.put(methodName, handler);
        }
    }

    protected Context getContext() {
        return mContext;
    }

    protected Object getOrigin() {
        return mOrigin;
    }

    protected void setServiceManagerProxyHandler(ServiceManagerProxyHandler serviceManagerProxyHandler) {
        mServiceManagerProxyHandler = serviceManagerProxyHandler;
    }

    @Override
    public Object iBinderHook() {
        return Proxy.newProxyInstance(getContext().getClassLoader(), getOrigin().getClass().getInterfaces(), this);
    }
}
