package com.phonecast;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class CastAccessibilityService extends AccessibilityService {
    private static final String TAG = "CastAccessService";
    private static CastAccessibilityService instance;

    public static CastAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Servicio de Accesibilidad conectado.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No-op
    }

    @Override
    public void onInterrupt() {
        // No-op
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    public static boolean click(float x, float y) {
        if (instance == null) {
            Log.w(TAG, "El servicio de accesibilidad no está activo.");
            return false;
        }
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, 50);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        return instance.dispatchGesture(builder.build(), null, null);
    }

    public static boolean swipe(float x1, float y1, float x2, float y2, int duration) {
        if (instance == null) {
            Log.w(TAG, "El servicio de accesibilidad no está activo.");
            return false;
        }
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, duration);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        return instance.dispatchGesture(builder.build(), null, null);
    }

    public static boolean performGlobalActionHelper(int action) {
        if (instance == null) {
            Log.w(TAG, "El servicio de accesibilidad no está activo.");
            return false;
        }
        return instance.performGlobalAction(action);
    }
}
