package com.lody.virtual.client.stub;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.os.Build;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;


/**
 * @author Lody
 *
 */
public class DaemonService extends Service {

    private static final int NOTIFY_ID = 1001;

    /**
     * PORT FIX: the original posted `new Notification()` with no channel and no
     * small icon. Channels became mandatory in O, and since then an invalid
     * foreground notification kills the process with
     * CannotPostForegroundServiceNotificationException.
     */
    static Notification buildDaemonNotification(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(
                    "va_engine", "Engine", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            if (nm != null) nm.createNotificationChannel(ch);
            return new Notification.Builder(ctx, "va_engine")
                    .setSmallIcon(android.R.drawable.sym_def_app_icon)
                    .build();
        }
        return new Notification();
    }


	public static void startup(Context context) {
		context.startService(new Intent(context, DaemonService.class));
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		startup(this);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
        startService(new Intent(this, InnerService.class));
        startForeground(NOTIFY_ID, buildDaemonNotification(this));

	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}

	public static final class InnerService extends Service {

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            startForeground(NOTIFY_ID, buildDaemonNotification(this));
            stopForeground(true);
            stopSelf();
            return super.onStartCommand(intent, flags, startId);
        }

		@Override
		public IBinder onBind(Intent intent) {
			return null;
		}
	}


}
