package com.javachat.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import com.javachat.mobile.network.ConnectionManager;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Application subclass — global lifecycle management.
 *
 * Fix IMP 13: calls setNotificationContext() so that local notifications
 * are enabled for incoming messages.
 *
 * Debug: installs a global uncaught-exception handler that shows the full
 * crash stacktrace on screen before the process dies — no adb/logcat needed.
 */
public class JavaChatApplication extends Application {

    private static final String TAG = "JavaChat";

    /** Most recently resumed Activity — used to show the crash dialog. */
    private Activity currentActivity;

    @Override
    public void onCreate() {
        super.onCreate();

        // IMP 13: create the notification channel on a background thread.
        // NotificationHelper.createChannel() calls createNotificationChannel() which
        // is a synchronous Binder IPC to system_server — can block the main thread
        // for several seconds on first launch → ANR. Run it off the main thread.
        new Thread(() ->
                ConnectionManager.getInstance().setNotificationContext(
                        JavaChatApplication.this)).start();

        // Track the foreground activity so the crash handler can show a dialog.
        // Set as early as onCreate so crashes that happen during Activity.onCreate are caught.
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) { currentActivity = a; }
            @Override public void onActivityStarted(Activity a)           { currentActivity = a; }
            @Override public void onActivityResumed(Activity a)           { currentActivity = a; }
            @Override public void onActivityPaused(Activity a)            {}
            @Override public void onActivityStopped(Activity a)           {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a)         { if (currentActivity == a) currentActivity = null; }
        });

        // Global crash handler — shows full scrollable, selectable stacktrace on
        // screen before dying so you can read/copy it without adb or Logcat.
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            String trace = "Thread: " + thread.getName() + "\n\n" + sw;

            Log.e(TAG, "CRASH on thread " + thread.getName(), throwable);

            // DEV: send full trace to server before showing the dialog —
            // appears in server log as [CLIENT_ERROR] so you see it without adb.
            ConnectionManager.getInstance().sendErrorReport(
                    "CRASH on " + thread.getName() + "\n" + trace);

            Activity a = currentActivity;
            if (a != null && !a.isFinishing()) {
                a.runOnUiThread(() -> {
                    try {
                        // Use a selectable, scrollable TextView so the full trace
                        // is readable and can be long-pressed to copy on device.
                        android.widget.TextView tv = new android.widget.TextView(a);
                        tv.setText(trace);
                        tv.setTextIsSelectable(true);
                        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
                        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
                        tv.setPadding(32, 24, 32, 24);

                        android.widget.ScrollView sv = new android.widget.ScrollView(a);
                        sv.addView(tv);

                        new AlertDialog.Builder(a)
                                .setTitle("💥 CRASH — long-press to copy")
                                .setView(sv)
                                .setPositiveButton("Kill app", (d, w) ->
                                        android.os.Process.killProcess(android.os.Process.myPid()))
                                .setCancelable(false)
                                .show();
                    } catch (Exception ignored) {
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }
                });
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        });
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        // disconnect() off main thread — same ANR risk as LoginActivity
        new Thread(ConnectionManager.getInstance()::disconnect).start();
    }
}
