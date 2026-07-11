package com.phonecast;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "PhoneCast";
    private static final int REQ_PROJECTION = 1001;
    private static final int REQ_AUDIO = 1002;

    private TextView tvStatus, tvIp, tvAccessibilityStatus;
    private Button btnStart, btnStop;
    private MediaProjectionManager mpm;
    private ActivityResultLauncher<Intent> projectionLauncher;
    private ActivityResultLauncher<String> audioPermissionLauncher;

    private Intent pendingProjectionData;
    private int pendingProjectionCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                throwable.printStackTrace(pw);
                pw.flush();
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), "PhoneCast");
                dir.mkdirs();
                File f = new File(dir, "crash_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".txt");
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(sw.toString().getBytes());
                fos.close();
            } catch (Exception ignored) {}
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(1);
        });

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        tvIp = findViewById(R.id.tv_my_ip);
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status);
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);

        mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        audioPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted && pendingProjectionData != null) {
                    startCastService(pendingProjectionCode, pendingProjectionData);
                    pendingProjectionData = null;
                } else if (!granted) {
                    Toast.makeText(this, "Permiso de micrófono necesario para audio", Toast.LENGTH_LONG).show();
                    if (pendingProjectionData != null) {
                        startCastService(pendingProjectionCode, pendingProjectionData);
                        pendingProjectionData = null;
                    }
                }
            }
        );

        projectionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Log.d(TAG, "Projection result: " + result.getResultCode());
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    pendingProjectionCode = result.getResultCode();
                    pendingProjectionData = result.getData();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                            != PackageManager.PERMISSION_GRANTED) {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
                    } else {
                        startCastService(pendingProjectionCode, pendingProjectionData);
                        pendingProjectionData = null;
                    }
                } else {
                    Toast.makeText(this, "Acepta el permiso de captura de pantalla", Toast.LENGTH_LONG).show();
                }
            }
        );

        btnStart.setOnClickListener(v -> {
            if (mpm == null) {
                Toast.makeText(this, "Captura de pantalla no soportada", Toast.LENGTH_LONG).show();
                return;
            }
            try {
                Intent intent = mpm.createScreenCaptureIntent();
                projectionLauncher.launch(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error", e);
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, CastService.class));
            setStatus(false);
        });

        findViewById(R.id.btn_enable_accessibility).setOnClickListener(v -> {
            startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS"));
        });

        updateIp();
        setStatus(false);
        updateAccessibilityStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityStatus();
    }

    private void updateAccessibilityStatus() {
        String enabledServices = Settings.Secure.getString(
            getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        String serviceName = getPackageName() + "/" + CastAccessibilityService.class.getName();
        boolean enabled = enabledServices != null && enabledServices.contains(serviceName);

        if (enabled) {
            SpannableString ss = new SpannableString(" Activado");
            ss.setSpan(new ForegroundColorSpan(0xFF4CAF50), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            tvAccessibilityStatus.setText(ss);
        } else {
            SpannableString ss = new SpannableString(" Desactivado");
            ss.setSpan(new ForegroundColorSpan(0xFFFF5252), 0, ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            tvAccessibilityStatus.setText(ss);
        }
    }

    private void startCastService(int resultCode, Intent data) {
        try {
            stopService(new Intent(this, CastService.class));
            Thread.sleep(300);
        } catch (Exception ignored) {}

        try {
            Intent svc = new Intent(this, CastService.class);
            svc.putExtra("resultCode", resultCode);
            svc.putExtra("data", data);
            startForegroundService(svc);
            setStatus(true);
        } catch (Throwable t) {
            Log.e(TAG, "Error iniciando servicio", t);
            Toast.makeText(this, t.getMessage(), Toast.LENGTH_LONG).show();
            setStatus(false);
        }
    }

    private void setStatus(boolean active) {
        tvStatus.setText(active ? "Transmitiendo" : "Detenido");
        tvStatus.setTextColor(active ? 0xFF4CAF50 : 0xFFFF5252);
        btnStart.setVisibility(active ? View.GONE : View.VISIBLE);
        btnStop.setVisibility(active ? View.VISIBLE : View.GONE);
        updateIp();
    }

    private void updateIp() {
        try {
            List<NetworkInterface> ifaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface iface : ifaces) {
                if (!iface.isUp() || iface.isLoopback()) continue;
                List<InetAddress> addrs = Collections.list(iface.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        if (ip != null && ip.contains(".")) {
                            tvIp.setText(ip);
                            return;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        tvIp.setText("Sin conexion");
    }
}
