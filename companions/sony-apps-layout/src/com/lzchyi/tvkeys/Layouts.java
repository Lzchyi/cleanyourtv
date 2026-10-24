package com.lzchyi.tvkeys;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.PackageManager;
public final class Layouts extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!"com.lzchyi.tvkeys.SET_FORWARDING".equals(intent.getAction())) return;
        boolean enabled = intent.getBooleanExtra("enabled", true);
        context.getPackageManager().setComponentEnabledSetting(new ComponentName(context, LibraryRoute.class),
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        setResultCode(enabled ? 1 : 0);
        setResultData("forwarding=" + enabled);
    }
}
