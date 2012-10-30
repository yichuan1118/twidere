package edu.ucdavis.earlybird;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;

public class UploadReceiver extends BroadcastReceiver {

	public static final String ACTION_UPLOAD_PROFILE = "edu.ucdavis.earlybird.UPLOAD_PROFILE";

	@Override
	public void onReceive(final Context context, final Intent intent) {
		final boolean isWifi = ProfilingUtil.isOnWifi(context.getApplicationContext());
		final boolean isCharging = ProfilingUtil.isCharging(context.getApplicationContext());
		
		if(intent.getAction().equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)){
			final boolean wifi = intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false);
			ProfilingUtil.profiling(context, ProfilingUtil.FILE_NAME_WIFI, wifi?"connected":"disconnected");
		}

		if (isWifi && isCharging) {
			new UploadTask(context).execute();
		}
	}
}
