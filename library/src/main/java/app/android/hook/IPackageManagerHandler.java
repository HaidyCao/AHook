package app.android.hook;

import android.content.Context;
import android.os.IBinder;

class IPackageManagerHandler extends BaseServiceHandler {

    public IPackageManagerHandler(Context context, IBinder iBinder) {
        super(context, "android.content.pm.IPackageManager$Stub", iBinder);
    }
}
