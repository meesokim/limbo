package com.max2idea.android.limbo.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

import com.limbo.emu.main.arm.R;
import com.max2idea.android.limbo.jni.VMExecutor;
import com.max2idea.android.limbo.utils.UIUtils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v7.app.NotificationCompat;
import android.support.v7.app.NotificationCompat.Builder;
import android.system.Os;
import android.util.Log;

public class LimboService extends Service {

	private static final String TAG = "LimboService";
	private static Notification mNotification;
	private static WifiLock mWifiLock;
	public static LimboService service;
	private static WakeLock mWakeLock;
	private NotificationManager mNotificationManager;

	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	public static VMExecutor executor;
	private static Builder builder;

	public static final int notifID = 1000;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		final String action = intent.getAction();
		final Bundle b = intent.getExtras();

		if (action.equals(Config.ACTION_START)) {
			Log.d(TAG, "Received ACTION_URL");
			if (LimboActivity.currMachine == null)
				return START_NOT_STICKY;

			setUpAsForeground(LimboActivity.currMachine.machinename + ": VM Running");

			startLogging();

			Log.v(TAG, "Starting the VM");
			executor.loadNativeLibs();

			Thread t = new Thread(new Runnable() {
				public void run() {
					String res = executor.start();
					Log.d(TAG, res);
					// LimboActivity.sendHandlerMessage(LimboActivity.OShandler,
					// Const.VM_STOPPED);

				}
			});
			t.start();

		}
		

		// Don't restart if killed
		return START_NOT_STICKY;
	}

	public static StringBuilder log = null;
	

	private void startLogging() {
		// TODO Auto-generated method stub
		Thread t = new Thread(new Runnable() {
			public void run() {

				FileOutputStream os = null;
				File logFile = new File(Config.logFilePath);
				if (logFile.exists()) {
					logFile.delete();
				}
				try {
					Runtime.getRuntime().exec("logcat -c");
					Process process = Runtime.getRuntime().exec("logcat v main");
					os = new FileOutputStream(logFile);
					BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

					log = new StringBuilder();
					String line = "";
					while ((line = bufferedReader.readLine()) != null) {
						log.append(line + "\n");
						os.write((line + "\n").getBytes("UTF-8"));
						os.flush();
					}
				} catch (IOException e) {

				} finally {
					try {
						os.flush();
						os.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}

			}
		});
		t.start();
	}

	private static void setUpAsForeground(String text) {
		Class<?> clientClass = null;
		if (LimboActivity.currMachine != null) {
			if (LimboActivity.currMachine.ui != null) {
				if (LimboActivity.currMachine.ui.equals("VNC")) {
					clientClass = LimboVNCActivity.class;
				} else if (LimboActivity.currMachine.ui.equals("SDL")) {
					clientClass = LimboSDLActivity.class;
				} else {
					UIUtils.toastLong(service, "Unknown User Interface");
					return;
				}
			} else {
				// UIUtils.toastLong(service, "Machine UI is not set");
				//using VNC by default
				clientClass = LimboVNCActivity.class;
			}
		} else {
			UIUtils.toastLong(service, "No Machine selected");
			return;
		}
		Intent intent = new Intent(service.getApplicationContext(), clientClass);

		PendingIntent pi = PendingIntent.getActivity(service.getApplicationContext(), 0, intent,
				PendingIntent.FLAG_UPDATE_CURRENT);

		// mNotification = new Notification();
		// mNotification.tickerText = text;
		// mNotification.icon = R.drawable.limbo;
		// mNotification.flags |= Notification.FLAG_ONGOING_EVENT;
		// mNotification.setLatestEventInfo(service.getApplicationContext(),
		// Config.APP_NAME , text, pi);
		// service.startForeground(notifID, mNotification);

		builder = new NotificationCompat.Builder(service);
		mNotification = builder.setContentIntent(pi).setContentTitle("Limbo PC Emulator").setContentText(text)
				.setSmallIcon(R.drawable.limbo)
				.setLargeIcon(BitmapFactory.decodeResource(service.getResources(), R.drawable.limbo)).build();
		mNotification.tickerText = text;
		mNotification.flags |= Notification.FLAG_ONGOING_EVENT;

		service.startForeground(notifID, mNotification);

		mWakeLock.acquire();

	}

	public static void notifyNotification(String text) {
		if (builder != null) {
			builder.setContentText(text);
			mNotification = builder.build();
			// mNotification.tickerText = text ;

			NotificationManager mNotificationManager = (NotificationManager) service
					.getSystemService(Context.NOTIFICATION_SERVICE);
			// mId allows you to update the notification later on.
			mNotificationManager.notify(notifID, mNotification);
		}
	}

	@Override
	public void onCreate() {
		Log.d(TAG, "debug: Creating " + TAG);

		setupWifiLock();

		service = this;

	}

	private void setupWifiLock() {

		mWifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
				.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WAKELOCK_KEY");

		mWifiLock.setReferenceCounted(false);

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Limbo");
		mWakeLock.setReferenceCounted(false);

		mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

	}

	public static void stopService() {
		// TODO Auto-generated method stub
		LimboActivity.vmexecutor.stopvm(0);
		mWakeLock.release();
		service.stopSelf();
	}

}
