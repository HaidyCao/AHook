package app.android.hook;

import android.content.Context;

import java.lang.reflect.Method;

public class IBinderHandler extends BaseServiceHandler {

    private Object mInterface;
    private IQueryLocalInterface mQueryLocalInterface;

    public IBinderHandler(Context context, Object origin, Object iInterface) {
        super(context, origin);
        mInterface = iInterface;

        registerProxyHandler("queryLocalInterface", new ServiceManagerProxyHandler() {

            @Override
            public Object handle(Object proxy, Object origin, Method method, Object[] args) {
                if (mInterface != null) {
                    return mInterface;
                }

                return mQueryLocalInterface.queryLocalInterface();
            }
        });
    }

    public void setQueryLocalInterface(IQueryLocalInterface queryLocalInterface) {
        mQueryLocalInterface = queryLocalInterface;
    }

    public interface IQueryLocalInterface {

        Object queryLocalInterface();
    }
}
