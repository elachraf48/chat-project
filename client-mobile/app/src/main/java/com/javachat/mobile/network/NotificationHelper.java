package com.javachat.mobile.network;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.javachat.mobile.R;
import com.javachat.mobile.ui.ChatActivity;

/**
 * Delivers local in-app notifications when a message arrives while the
 * application is not in the foreground.
 *
 * Note on background strategy (per client-mobile.md §10):
 * A persistent TCP socket cannot be guaranteed when Android backgrounds the
 * app. For reliable push notifications in production, Firebase Cloud Messaging
 * (FCM) should be added in a future phase. This class handles the foreground
 * notification case only.
 */
public class NotificationHelper {

    private static final String CHANNEL_ID   = "javachat_messages";
    private static final String CHANNEL_NAME = "New Messages";
    private static final int    NOTIF_ID     = 1001;

    private final Context context;
    private final NotificationManager notificationManager;

    public NotificationHelper(Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager =
                (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
    }

    public void showMessageNotification(String sender, String text) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getActivity(context, 0, intent, flags);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(sender != null ? sender : "JavaChat")
                .setContentText(text != null ? text : "New message")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

        notificationManager.notify(NOTIF_ID, notification);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
