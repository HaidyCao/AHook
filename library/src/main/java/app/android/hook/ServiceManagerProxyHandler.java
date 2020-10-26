package app.android.hook;

import java.lang.reflect.Method;

public interface ServiceManagerProxyHandler {

    Object handle(Object proxy, Object origin, Method method, Object[] args) throws Throwable;
}
