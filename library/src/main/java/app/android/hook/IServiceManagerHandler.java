package app.android.hook;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Arrays;

public class IServiceManagerHandler extends BaseServiceHandler {

    public IServiceManagerHandler(Context context, Object origin) {
        super(context, origin);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Log.d(IServiceManagerHandler.class.getSimpleName(), "invoke() called with: method = [" + method + "], args = [" + Arrays.toString(args) + "]");
        return super.invoke(proxy, method, args);
    }
}
