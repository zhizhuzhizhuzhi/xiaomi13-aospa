package com.xiaomi.settings.touch;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class TouchPollingService extends Service {
    private static final String KEY = "touch_polling_rate";
    private static final String CONFIG = "/vendor/etc/touch-polling.conf";
    private Handler worker;
    private ContentObserver observer;
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { worker.post(TouchPollingService.this::apply); }
    };
    private void apply() {
        int value = Settings.System.getIntForUser(getContentResolver(), KEY, 0, UserHandle.USER_CURRENT) == 1 ? 1 : 0;
        try {
            String path = Files.readString(Paths.get(CONFIG)).trim();
            if (!path.startsWith("/sys/")) throw new IOException("Invalid touch node");
            try (FileWriter writer = new FileWriter(path)) {
                writer.write(Integer.toString(value));
            }
            Log.i("TouchPollingService", "Requested touch sampling mode " + value);
        } catch (IOException | SecurityException e) {
            Log.e("TouchPollingService", "Unable to apply touch sampling mode " + value, e);
        }
    }
    @Override public void onCreate() {
        super.onCreate();
        HandlerThread thread = new HandlerThread("TouchPollingService");
        thread.start();
        worker = new Handler(thread.getLooper());
        observer = new ContentObserver(worker) {
            @Override public void onChange(boolean selfChange) { apply(); }
        };
        getContentResolver().registerContentObserver(Settings.System.getUriFor(KEY), false, observer, UserHandle.USER_ALL);
        registerReceiver(screenReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
        worker.post(this::apply);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        worker.post(this::apply);
        return START_STICKY;
    }
    @Override public void onDestroy() {
        unregisterReceiver(screenReceiver);
        getContentResolver().unregisterContentObserver(observer);
        worker.getLooper().quitSafely();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
