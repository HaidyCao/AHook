package app.android.hook;

import android.content.Context;
import android.os.IBinder;

public class IAutofillManagerHandler extends BaseServiceHandler {

    public IAutofillManagerHandler(Context context, IBinder iBinder) {
        super(context, "android.view.autofill.IAutoFillManager$Stub", iBinder);
    }
}
