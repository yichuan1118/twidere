package edu.ucdavis.earlybird;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.SocketException;
import java.util.Calendar;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPReply;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;

public class UploadTask extends AsyncTask<Void, Void, Void> {
	private Context mContext;
	private static final String LAST_UPLOAD_DATE = "LastUploadTime";
	private static final double MILLSECS_HALF_DAY = 1000 * 60 * 60 * 12;

	public UploadTask(Context context) {
		mContext = context;
	}

	@Override
	protected Void doInBackground(Void... params) {
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(mContext);

		if (settings.contains(LAST_UPLOAD_DATE)) {
			long lastUpload = settings.getLong(LAST_UPLOAD_DATE,
					System.currentTimeMillis());
			double deltaDays = ((double) (System.currentTimeMillis() - lastUpload))
					/ (MILLSECS_HALF_DAY * 2);
			if (deltaDays < 1) {
				Util.log("Uploaded less than 1 day ago.");
				return null;
			}
		}

		File root = mContext.getFileStreamPath("");
		File[] files = root.listFiles();

		try {

			for (File f : files) {
				if (f.isFile()) {
					final File profile = f;
					Util.log("profile name " + profile.getName() + " length "
							+ profile.length());
					new Thread() {
						@Override
						public void run() {
							try {
								if (profile.length() > 0) {
									Upload2FTP(profile);
								}
							} catch (Exception e) {
								Util.log("UploadFTP : " + e.toString());
							}
						}
					}.start();
				}
			}

			settings.edit()
					.putLong(LAST_UPLOAD_DATE, System.currentTimeMillis())
					.commit();
		} catch (Exception ex) {
		} finally {
		}
		return null;
	}

	private boolean Upload2FTP(File file) throws IOException {
		final String ftp_profile_server = "earlybird_profile.metaisle.com";
		final String username = "profile";
		final String password = "profile";

		String DeviceID = Secure.getString(mContext.getContentResolver(),
				Secure.ANDROID_ID);

		FTPClient ftp = new FTPClient();

		ftp.setDefaultTimeout(30000);

		try {
			ftp.connect(ftp_profile_server, 21);
			int reply = ftp.getReplyCode();
			if (!FTPReply.isPositiveCompletion(reply)) {
				Util.log("FTP connect fail");
				ftp.disconnect();
			} else {
				if (ftp.login(username, password)) {
					ftp.enterLocalPassiveMode();
					Util.log("FTP connect OK");
					ftp.setFileType(FTP.BINARY_FILE_TYPE);
					Calendar now = Calendar.getInstance();
					FileInputStream fis = new FileInputStream(file);
					String profile_type = file.getName().substring(0,
							file.getName().indexOf('.'));
					String file_type = file.getName().substring(
							file.getName().indexOf('.'));
					boolean flag = ftp.changeWorkingDirectory("/profile/"
							+ DeviceID + "/" + profile_type);
					if (flag == false) {
						Util.log("create user floder : " + "/profile/"
								+ DeviceID + "/" + profile_type);
						ftp.makeDirectory("/profile/" + DeviceID);
						ftp.makeDirectory("/profile/" + DeviceID + "/"
								+ profile_type);
					}

					ftp.setFileType(FTP.BINARY_FILE_TYPE);

					ftp.storeFile("/profile/" + DeviceID + "/" + profile_type
							+ "/" + String.valueOf(now.getTimeInMillis())
							+ file_type, fis);
					Util.log("Upload File : /profile/" + DeviceID + "/"
							+ profile_type + "/"
							+ String.valueOf(now.getTimeInMillis()) + file_type);
					reply = ftp.getReplyCode();
					Util.log("reply :" + reply);
					if (reply == 226) {
						Util.log("file upload success");
						file.delete();
						ftp.logout();
						return true;
					}
				}
				ftp.logout();
			}
		} catch (FTPConnectionClosedException e) {
			Util.log("ftp connect error!!");
			e.printStackTrace();
			return false;
		} catch (SocketException e) {
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return false;

	}

}
