package com.radiocast;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "RadioCast";
    private static final int PORT_VIDEO = 8080;
    private static final int PORT_TOUCH = 8081;
    private static final int KEEPALIVE_INTERVAL = 5000;
    private static final int RECONNECT_BASE_DELAY = 2000;
    private static final int RECONNECT_MAX_DELAY = 30000;

    private ImageView ivScreen;
    private LinearLayout llConnect, navBar;
    private EditText etPhoneIp;
    private TextView tvStatus;

    private volatile boolean connected = false;
    private volatile boolean connecting = false;
    private volatile boolean shouldReconnect = true;
    private Socket touchSocket;
    private OutputStream touchOut;
    private Thread videoThread;
    private Thread discoveryThread;
    private Thread keepaliveThread;
    private Thread reconnectThread;
    private String phoneIp = "";
    private int reconnectDelay = 1000;

    private float touchStartX, touchStartY;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        ivScreen  = findViewById(R.id.iv_screen);
        llConnect = findViewById(R.id.ll_connect);
        navBar    = findViewById(R.id.nav_bar);
        etPhoneIp = findViewById(R.id.et_phone_ip);
        tvStatus  = findViewById(R.id.tv_status);

        findViewById(R.id.btn_connect).setOnClickListener(v -> {
            String ip = etPhoneIp.getText().toString().trim();
            if (!ip.isEmpty()) { phoneIp = ip; connect(); }
            else Toast.makeText(this, "Ingresa IP", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> sendTouch("BACK"));
        findViewById(R.id.btn_home).setOnClickListener(v -> sendTouch("HOME"));
        findViewById(R.id.btn_recents).setOnClickListener(v -> sendTouch("RECENTS"));

        ivScreen.setOnTouchListener((v, event) -> {
            if (!connected) return false;
            float x = event.getX() / v.getWidth();
            float y = event.getY() / v.getHeight();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartX = x; touchStartY = y;
                    break;
                case MotionEvent.ACTION_UP:
                    float dx = x - touchStartX;
                    float dy = y - touchStartY;
                    if (Math.sqrt(dx * dx + dy * dy) < 0.05f) {
                        sendTouch("TAP:" + touchStartX + "," + touchStartY);
                    }
                    break;
            }
            return true;
        });

        setStatus("Buscando...", 0xFFFFAA00);
        startDiscovery();
    }

    private void startDiscovery() {
        discoveryThread = new Thread(() -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

            while (!connected && !Thread.currentThread().isInterrupted()) {
                try {
                    String found = findPhone();
                    if (found != null) {
                        phoneIp = found;
                        mainHandler.post(() -> {
                            setStatus("Detectado: " + found, 0xFF4CAF50);
                            Toast.makeText(this, "Telefono: " + found, Toast.LENGTH_SHORT).show();
                            connect();
                        });
                        return;
                    }
                } catch (Exception ignored) {}

                try { Thread.sleep(3000); } catch (InterruptedException e) { return; }
            }
        });
        discoveryThread.setDaemon(true);
        discoveryThread.start();
    }

    private String findPhone() {
        List<String> prefixes = new java.util.ArrayList<>();

        try {
            List<NetworkInterface> ifaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface iface : ifaces) {
                if (!iface.isUp() || iface.isLoopback()) continue;
                List<InetAddress> addrs = Collections.list(iface.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                        String ip = addr.getHostAddress();
                        String prefix = ip.substring(0, ip.lastIndexOf('.'));
                        if (!prefixes.contains(prefix)) prefixes.add(prefix);
                    }
                }
            }
        } catch (Exception ignored) {}

        prefixes.add("192.168.0");
        prefixes.add("192.168.1");
        prefixes.add("192.168.43");
        prefixes.add("192.168.42");

        List<String> quickCandidates = new java.util.ArrayList<>();
        for (String p : prefixes) {
            quickCandidates.add(p + ".1");
            quickCandidates.add(p + ".254");
            quickCandidates.add(p + ".129");
            quickCandidates.add(p + ".100");
            quickCandidates.add(p + ".101");
        }
        for (String ip : quickCandidates) {
            if (checkPort(ip, PORT_TOUCH, 200)) return ip;
        }

        for (String prefix : prefixes) {
            int threadCount = Math.min(20, 256);
            final java.util.concurrent.atomic.AtomicReference<String> result =
                new java.util.concurrent.atomic.AtomicReference<>(null);
            Thread[] scanners = new Thread[threadCount];
            int ipsPerThread = 256 / threadCount;
            for (int t = 0; t < threadCount; t++) {
                final int start = t * ipsPerThread;
                final int end = (t == threadCount - 1) ? 256 : start + ipsPerThread;
                scanners[t] = new Thread(() -> {
                    for (int i = start; i < end && result.get() == null; i++) {
                        if (i == 0) continue;
                        String ip = prefix + "." + i;
                        if (checkPort(ip, PORT_TOUCH, 100)) {
                            result.set(ip);
                            break;
                        }
                    }
                });
                scanners[t].start();
            }
            for (Thread scanner : scanners) {
                try { scanner.join(5000); } catch (InterruptedException ignored) {}
            }
            if (result.get() != null) return result.get();
        }
        return null;
    }

    private boolean checkPort(String ip, int port, int timeout) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), timeout);
            return true;
        } catch (Exception e) { return false; }
    }

    private void connect() {
        if (connecting) return;
        connecting = true;
        connected = false;
        setStatus("Conectando...", 0xFFFFAA00);

        new Thread(() -> {
            try {
                Socket sock = new Socket();
                sock.connect(new InetSocketAddress(phoneIp, PORT_TOUCH), 5000);
                touchSocket = sock;
                touchOut = sock.getOutputStream();
                connected = true;
                connecting = false;

                mainHandler.post(() -> {
                    setStatus("Conectado", 0xFF4CAF50);
                    llConnect.setVisibility(View.GONE);
                    navBar.setVisibility(View.VISIBLE);
                    ivScreen.setImageBitmap(null);
                });

                startVideo();
                startKeepalive();
            } catch (Exception e) {
                connected = false;
                connecting = false;
                mainHandler.post(() -> {
                    setStatus("Error: " + phoneIp, 0xFFFF5252);
                    Toast.makeText(this, "PhoneCast no responde en " + phoneIp, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void startKeepalive() {
        keepaliveThread = new Thread(() -> {
            while (connected && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(KEEPALIVE_INTERVAL);
                    if (connected && touchOut != null) {
                        touchOut.write("KEEPALIVE\n".getBytes());
                        touchOut.flush();
                    }
                } catch (Exception e) {
                    if (connected) {
                        mainHandler.post(this::disconnect);
                    }
                    break;
                }
            }
        });
        keepaliveThread.setDaemon(true);
        keepaliveThread.start();
    }

    private void startVideo() {
        videoThread = new Thread(() -> {
            Socket vs = null;
            try {
                vs = new Socket();
                vs.connect(new InetSocketAddress(phoneIp, PORT_VIDEO), 5000);
                InputStream in = vs.getInputStream();
                // Saltar cabeceras HTTP
                String line;
                while ((line = readLine(in)) != null && !line.isEmpty()) {}

                while (connected && !Thread.currentThread().isInterrupted()) {
                    // Leer hasta Content-Length
                    int length = -1;
                    while ((line = readLine(in)) != null) {
                        if (line.isEmpty()) continue;
                        if (line.startsWith("Content-Length:")) {
                            try {
                                length = Integer.parseInt(line.substring(15).trim());
                            } catch (NumberFormatException e) {
                                length = -1;
                            }
                            break;
                        }
                    }
                    if (length <= 0 || length > 1024 * 1024) continue;

                    // Saltar línea en blanco (\r\n) después de Content-Length
                    readLine(in);

                    // Leer exactamente los bytes del JPEG
                    byte[] jpeg = new byte[length];
                    int copied = 0;
                    while (copied < length) {
                        int rd = in.read(jpeg, copied, length - copied);
                        if (rd < 0) break;
                        copied += rd;
                    }

                    Bitmap bmp = BitmapFactory.decodeByteArray(jpeg, 0, copied);
                    if (bmp != null) {
                        final Bitmap fb = bmp;
                        mainHandler.post(() -> ivScreen.setImageBitmap(fb));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Video error", e);
            } finally {
                try { if (vs != null) vs.close(); } catch (IOException ignored) {}
                if (connected) mainHandler.post(() -> disconnect());
            }
        });
        videoThread.setDaemon(true);
        videoThread.start();
    }

    private String readLine(InputStream stream) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = stream.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
        }
        return c == -1 ? null : sb.toString();
    }

    private void sendTouch(String msg) {
        if (!connected || touchOut == null) return;
        try {
            touchOut.write((msg + "\n").getBytes());
            touchOut.flush();
        } catch (Exception ignored) {}
    }

    private void disconnect() {
        connected = false;
        try { if (touchSocket != null) touchSocket.close(); } catch (IOException ignored) {}
        touchOut = null;
        if (videoThread != null) { videoThread.interrupt(); videoThread = null; }
        if (keepaliveThread != null) { keepaliveThread.interrupt(); keepaliveThread = null; }
        mainHandler.post(() -> {
            setStatus("Desconectado", 0xFFFF5252);
            llConnect.setVisibility(View.VISIBLE);
            navBar.setVisibility(View.GONE);
            ivScreen.setImageBitmap(null);
            if (shouldReconnect && !phoneIp.isEmpty()) {
                startReconnect();
            }
        });
    }

    private void startReconnect() {
        if (reconnectThread != null && reconnectThread.isAlive()) return;
        reconnectThread = new Thread(() -> {
            int delay = RECONNECT_BASE_DELAY;
            while (shouldReconnect && !connected && !Thread.currentThread().isInterrupted()) {
                try {
                    int seconds = delay / 1000;
                    mainHandler.post(() -> setStatus("Reconectando en " + seconds + "s...", 0xFFFFAA00));
                    Thread.sleep(delay);
                    if (!connected && !phoneIp.isEmpty()) {
                        mainHandler.post(this::connect);
                        return;
                    }
                } catch (InterruptedException e) { return; }
                delay = Math.min(delay * 2, RECONNECT_MAX_DELAY);
            }
        });
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }

    private void setStatus(String text, int color) {
        if (tvStatus != null) { tvStatus.setText(text); tvStatus.setTextColor(color); }
    }

    @Override
    protected void onDestroy() {
        connected = false;
        if (discoveryThread != null) discoveryThread.interrupt();
        try { if (touchSocket != null) touchSocket.close(); } catch (IOException ignored) {}
        if (videoThread != null) videoThread.interrupt();
        super.onDestroy();
    }
}