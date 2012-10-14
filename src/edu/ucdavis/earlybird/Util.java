package edu.ucdavis.earlybird;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.util.Log;

public class Util {
	public static boolean DEBUG = true;

	public static boolean log(String msg) {
		if (DEBUG) {
			StackTraceElement ste = new Throwable().fillInStackTrace()
					.getStackTrace()[1];
			String fullname = ste.getClassName();
			String name = fullname.substring(fullname.lastIndexOf('.'));
			String tag = name + "." + ste.getMethodName();
			Log.v(tag, msg);
			return true;
		} else
			return false;
	}

	public static void profile(final Context context, final long accountID,
			final String name, final String text) {
		final String filename = accountID > 0 ? accountID + "_" + name : name;
		new Thread() {
			@Override
			public void run() {
				FileOutputStream fOut;
				try {
					fOut = context
							.openFileOutput(filename, Context.MODE_APPEND);
					BufferedWriter bw = new BufferedWriter(
							new OutputStreamWriter(fOut));
					bw.write("[" + System.currentTimeMillis() + "], " + text
							+ "\n");
					bw.flush();
				} catch (Exception e) {
					e.printStackTrace();
				}
			};
		}.start();
	}

	public static boolean isCharging(Context context) {
		Intent intent = context.registerReceiver(null, new IntentFilter(
				Intent.ACTION_BATTERY_CHANGED));
		int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
		return plugged == BatteryManager.BATTERY_PLUGGED_AC
				|| plugged == BatteryManager.BATTERY_PLUGGED_USB;
	}

	public static boolean isOnWifi(Context context) {
		ConnectivityManager conn = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = conn.getActiveNetworkInfo();

		if (networkInfo != null
				&& networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
			return true;
		} else {
			return false;
		}
	}
}
