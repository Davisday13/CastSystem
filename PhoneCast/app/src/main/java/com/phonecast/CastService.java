package com.phonecast;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public class CastService extends Service {

    private static final String TAG = "CastService";
    private static final int PORT_VIDEO = 8080;
    private static final int PORT_TOUCH = 8081;
    private static final String CHANNEL_ID = "cast_channel";

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private ServerSocket videoServer;
    private ServerSocket touchServer;
    private final AtomicReference<byte[]> latestFrame = new AtomicReference<>();
    private volatile boolean running = false;
    private HandlerThread handlerThread;
    private Handler backgroundHandler;
    private int screenWidth, screenHeight, screenDpi;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            // Limpiar estado anterior si existe
            if (running) {
                Log.d(TAG, "Reiniciando servicio...");
                cleanup();
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }

            if (intent == null || !intent.hasExtra("resultCode")) {
                Log.e(TAG, "Intent sin datos necesarios");
                stopSelf();
                return START_NOT_STICKY;
            }

            startForegroundSafe();

            int resultCode = intent.getIntExtra("resultCode", -1);
            Intent data = intent.getParcelableExtra("data");

            if (data == null) {
                Log.e(TAG, "data intent es null, intentando con resultCode solamente");
            }

            running = true;

            getScreenMetrics();
            setupMediaProjection(resultCode, data);

            new Thread(this::startVideoServer).start();
            new Thread(this::startTouchServer).start();
            return START_STICKY;

        } catch (Throwable t) {
            Log.e(TAG, "FATAL", t);
            logError(t);
            showToast("Error: " + t.getMessage());
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    private void startForegroundSafe() {
        int api = android.os.Build.VERSION.SDK_INT;
        int type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;

        if (api >= 34) {
            startForeground(1, buildNotification(), type);
        } else {
            startForeground(1, buildNotification());
        }
    }

    private void getScreenMetrics() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        screenWidth  = dm.widthPixels / 2;
        screenHeight = dm.heightPixels / 2;
        screenDpi    = dm.densityDpi;
    }

    private void setupMediaProjection(int resultCode, Intent data) {
        MediaProjectionManager mpm =
            (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpm.getMediaProjection(resultCode, data);

        if (mediaProjection == null) {
            throw new RuntimeException("MediaProjection is null — ¿permiso de captura denegado?");
        }

        // OBLIGATORIO en Android 13+: registrar callback antes de crear VirtualDisplay
        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.d(TAG, "MediaProjection stopped externally");
                if (running) stopSelf();
            }
        }, null);

        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "PhoneCast",
            screenWidth, screenHeight, screenDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.getSurface(), null, null
        );

        handlerThread = new HandlerThread("CastImage");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());

        imageReader.setOnImageAvailableListener(reader -> {
            try (Image image = reader.acquireLatestImage()) {
                if (image == null) return;
                Image.Plane[] planes = image.getPlanes();
                if (planes == null || planes.length == 0) return;
                ByteBuffer buffer = planes[0].getBuffer();
                int pixelStride  = planes[0].getPixelStride();
                int rowStride    = planes[0].getRowStride();
                int rowPadding   = rowStride - pixelStride * screenWidth;

                Bitmap bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight, Bitmap.Config.ARGB_8888);
                bitmap.copyPixelsFromBuffer(buffer);

                Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);
                bitmap.recycle();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                cropped.compress(Bitmap.CompressFormat.JPEG, 60, baos);
                cropped.recycle();

                latestFrame.set(baos.toByteArray());
            } catch (Throwable t) {
                Log.e(TAG, "Frame error", t);
            }
        }, backgroundHandler);
    }

    private void startVideoServer() {
        try {
            videoServer = new ServerSocket(PORT_VIDEO);
            videoServer.setReuseAddress(true);
            Log.d(TAG, "Video server escuchando en puerto " + PORT_VIDEO);
            while (running) {
                Socket client = videoServer.accept();
                Log.d(TAG, "Video cliente conectado");
                new Thread(() -> handleVideoClient(client)).start();
            }
        } catch (IOException e) {
            if (running) Log.e(TAG, "Video server error", e);
        }
    }

    private void handleVideoClient(Socket client) {
        try {
            OutputStream out = client.getOutputStream();
            String header = "HTTP/1.0 200 OK\r\n" +
                "Content-Type: multipart/x-mixed-replace;boundary=frame\r\n\r\n";
            out.write(header.getBytes());

            while (running && !client.isClosed()) {
                byte[] frame = latestFrame.get();
                if (frame == null) { Thread.sleep(33); continue; }

                String boundary = "--frame\r\nContent-Type: image/jpeg\r\n" +
                    "Content-Length: " + frame.length + "\r\n\r\n";
                out.write(boundary.getBytes());
                out.write(frame);
                out.write("\r\n".getBytes());
                out.flush();
                Thread.sleep(33);
            }
        } catch (Exception e) {
            Log.d(TAG, "Video client disconnected");
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    private void startTouchServer() {
        try {
            touchServer = new ServerSocket(PORT_TOUCH);
            touchServer.setReuseAddress(true);
            Log.d(TAG, "Touch server escuchando en puerto " + PORT_TOUCH);
            while (running) {
                Socket client = touchServer.accept();
                Log.d(TAG, "Touch cliente conectado");
                new Thread(() -> handleTouchClient(client)).start();
            }
        } catch (IOException e) {
            if (running) Log.e(TAG, "Touch server error", e);
        }
    }

    // ─── TOUCH ──────────────────────────────────────────────

    private void handleTouchClient(Socket client) {
        try {
            byte[] buf = new byte[256];
            while (running && !client.isClosed()) {
                int len = client.getInputStream().read(buf);
                if (len <= 0) break;
                String msg = new String(buf, 0, len).trim();
                processTouch(msg);
            }
        } catch (Exception e) {
            Log.d(TAG, "Touch client disconnected");
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    private void processTouch(String msg) {
        try {
            if (msg.equals("KEEPALIVE")) {
                return;
            }
            if (msg.startsWith("TAP:")) {
                String[] parts = msg.substring(4).split(",");
                float nx = Float.parseFloat(parts[0]);
                float ny = Float.parseFloat(parts[1]);
                int realX = (int)(nx * screenWidth * 2);
                int realY = (int)(ny * screenHeight * 2);
                CastAccessibilityService.click(realX, realY);
            } else if (msg.startsWith("SWIPE:")) {
                String[] parts = msg.substring(6).split(",");
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                DisplayMetrics dm = new DisplayMetrics();
                wm.getDefaultDisplay().getMetrics(dm);
                CastAccessibilityService.swipe(
                    (int)(Float.parseFloat(parts[0]) * dm.widthPixels),
                    (int)(Float.parseFloat(parts[1]) * dm.heightPixels),
                    (int)(Float.parseFloat(parts[2]) * dm.widthPixels),
                    (int)(Float.parseFloat(parts[3]) * dm.heightPixels),
                    300);
            } else if (msg.equals("BACK")) {
                CastAccessibilityService.performGlobalActionHelper(AccessibilityService.GLOBAL_ACTION_BACK);
            } else if (msg.equals("HOME")) {
                CastAccessibilityService.performGlobalActionHelper(AccessibilityService.GLOBAL_ACTION_HOME);
            } else if (msg.equals("RECENTS")) {
                CastAccessibilityService.performGlobalActionHelper(AccessibilityService.GLOBAL_ACTION_RECENTS);
            } else if (msg.equals("MUSIC")) {
                Intent intent = new Intent(android.content.Intent.ACTION_MAIN);
                intent.addCategory(android.content.Intent.CATEGORY_APP_MUSIC);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(intent); } catch (Exception e) { Log.e(TAG, "No se pudo abrir musica", e); }
            } else if (msg.equals("MAPS")) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=mapas"));
                intent.setPackage("com.google.android.apps.maps");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(intent); }
                catch (Exception e) {
                    Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=navigation"));
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { startActivity(fallback); } catch (Exception e2) { Log.e(TAG, "No maps", e2); }
                }
            } else if (msg.equals("WAZE")) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setPackage("com.waze");
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(intent); }
                catch (Exception e) {
                    Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul"));
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { startActivity(fallback); } catch (Exception e2) { Log.e(TAG, "No waze", e2); }
                }
            } else if (msg.equals("NAV")) {
                Intent intent = new Intent(android.content.Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=navigation"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(intent); } catch (Exception e) { Log.e(TAG, "No se pudo abrir navegacion", e); }
            } else if (msg.equals("YOUTUBE")) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setPackage("com.google.android.youtube");
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(intent); } catch (Exception e) {
                    // intentar abrir youtube en chrome
                    Intent ci = new Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com"));
                    ci.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { startActivity(ci); } catch (Exception e2) { Log.e(TAG, "No se pudo abrir YouTube", e2); }
                }
            } else if (msg.equals("PLAYPAUSE")) {
                Intent playIntent = new Intent(android.content.Intent.ACTION_MEDIA_BUTTON);
                playIntent.putExtra(Intent.EXTRA_KEY_EVENT,
                    new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                sendBroadcast(playIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Touch error: " + msg, e);
        }
    }

    private Notification buildNotification() {
        NotificationChannel ch = new NotificationChannel(
            CHANNEL_ID, "PhoneCast", NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PhoneCast activo")
            .setContentText("Transmitiendo pantalla al radio...")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setOngoing(true)
            .build();
    }

    private void showToast(String msg) {
        try {
            new Handler(android.os.Looper.getMainLooper()).post(() -> {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            });
        } catch (Exception ignored) {}
    }

    private void logError(Throwable t) {
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "PhoneCast");
            dir.mkdirs();
            File f = new File(dir, "crash_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".txt");
            FileOutputStream fos = new FileOutputStream(f);
            fos.write((t.toString() + "\n\n").getBytes());
            for (StackTraceElement e : t.getStackTrace()) {
                fos.write(("\tat " + e.toString() + "\n").getBytes());
            }
            fos.close();
        } catch (Exception ignored) {}
    }

    private void cleanup() {
        running = false;
        if (handlerThread != null) {
            handlerThread.quitSafely();
            try { handlerThread.join(1000); } catch (InterruptedException ignored) {}
            handlerThread = null;
            backgroundHandler = null;
        }
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (mediaProjection != null) { mediaProjection.stop(); mediaProjection = null; }
        if (imageReader != null) { imageReader.close(); imageReader = null; }
        try { if (videoServer != null) { videoServer.close(); videoServer = null; } } catch (IOException ignored) {}
        try { if (touchServer != null) { touchServer.close(); touchServer = null; } } catch (IOException ignored) {}
        latestFrame.set(null);
    }

    @Override
    public void onDestroy() {
        cleanup();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
