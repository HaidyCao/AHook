package app.android.hook;

import android.content.Context;

import java.lang.reflect.Method;
import java.util.Map;

class IContentProviderHandler extends BaseServiceHandler {

    public IContentProviderHandler(Context context, Object origin) {
        super(context, origin);

        registerAsBinder();
    }

    static class ContentProviderInvokeHandler implements ServiceManagerProxyHandler {

        @Override
        public Object handle(Object proxy, Object origin, Method method, Object[] args) throws Throwable {
            Map<String, ServiceManagerProxyHandler> map = Hook.sIContentProviderProxyHandler;
            ServiceManagerProxyHandler handler = map.get(method.getName());
            if (handler != null) {
                return handler.handle(proxy, origin, method, args);
            }

            return method.invoke(origin, args);
        }
    }
}
