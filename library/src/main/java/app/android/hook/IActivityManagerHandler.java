package app.android.hook;

import android.content.Context;
import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

class IActivityManagerHandler extends BaseServiceHandler implements Hook.IBinderHook {

    private static final String TAG = IActivityManagerHandler.class.getSimpleName();

    public IActivityManagerHandler(Context context, IBinder iBinder) {
        super(context, "android.app.IActivityManager$Stub", iBinder);

        ServiceManagerProxyHandler getContentProviderHandler = new ServiceManagerProxyHandler() {

            @Override
            public Object handle(Object proxy, Object origin, Method method, Object[] args) throws Throwable {
                Object contentProviderHolder = method.invoke(origin, args);
                if (contentProviderHolder == null) {
                    return null;
                }
                Log.d(TAG, "handle() called with: origin = [" + origin + "], method = [" + method + "], args = [" + Arrays.toString(args) + "]");

                Object iContentProvider = Reflect.getField(contentProviderHolder, "provider");
                IContentProviderHandler proxyHandler = new IContentProviderHandler(getContext(), iContentProvider);
                proxyHandler.setServiceManagerProxyHandler(new IContentProviderHandler.ContentProviderInvokeHandler());

                Object iContentProviderProxy = Proxy.newProxyInstance(getContext().getClassLoader(), iContentProvider.getClass().getInterfaces(), proxyHandler);
                Reflect.setField(contentProviderHolder, "provider", iContentProviderProxy);
                return contentProviderHolder;
            }
        };
        registerProxyHandler("getContentProvider", getContentProviderHandler);
        registerProxyHandler("getContentProviderExternal", getContentProviderHandler);

        registerAsBinder();
    }
}
