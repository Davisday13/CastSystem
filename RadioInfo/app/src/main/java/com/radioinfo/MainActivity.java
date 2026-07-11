package com.radioinfo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private LinearLayout llInfo;
    private TextView tvLog;
    private Button btnRefresh, btnTestConnection, btnSaveReport;

    private final StringBuilder logBuffer = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        llInfo = findViewById(R.id.ll_info);
        tvLog = findViewById(R.id.tv_log);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnTestConnection = findViewById(R.id.btn_test_connection);
        btnSaveReport = findViewById(R.id.btn_save_report);

        btnRefresh.setOnClickListener(v -> scanDevice());
        btnTestConnection.setOnClickListener(v -> testPhoneCastConnection());
        btnSaveReport.setOnClickListener(v -> saveReport());

        scanDevice();
    }

    // ────────────────────────────────────────────────────────
    //  ESCANEO DEL DISPOSITIVO
    // ────────────────────────────────────────────────────────

    private void scanDevice() {
        llInfo.removeAllViews();
        logBuffer.setLength(0);
        log("=== RADIOINFO SCAN ===");
        log(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        log("");

        addSection("📱 SISTEMA");
        addInfo("Fabricante", Build.MANUFACTURER);
        addInfo("Modelo", Build.MODEL);
        addInfo("Dispositivo", Build.DEVICE);
        addInfo("Producto", Build.PRODUCT);
        addInfo("Hardware", Build.HARDWARE);
        addInfo("Placa", Build.BOARD);
        addInfo("Host", Build.HOST);

        addSection("🤖 ANDROID");
        addInfo("Versión", Build.VERSION.RELEASE);
        addInfo("SDK API", String.valueOf(Build.VERSION.SDK_INT));
        addInfo("Código", getAndroidCodename(Build.VERSION.SDK_INT));
        addInfo("Build ID", Build.DISPLAY);
        addInfo("Security Patch",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? Build.VERSION.SECURITY_PATCH : "N/A");
        addInfo("Tipo", Build.TYPE);
        addInfo("Tags", Build.TAGS);
        addInfo("Incremental", Build.VERSION.INCREMENTAL);
        addInfo("Fingerprint", Build.FINGERPRINT);

        addSection("🖥️  PANTALLA");
        DisplayMetrics dm = getResources().getDisplayMetrics();
        addInfo("Resolución", dm.widthPixels + "x" + dm.heightPixels + "px");
        addInfo("Densidad", dm.densityDpi + "dpi (" + getDensityName(dm.densityDpi) + ")");
        addInfo("Density (raw)", String.valueOf(dm.density));
        addInfo("Xdpi", String.valueOf(dm.xdpi));
        addInfo("Ydpi", String.valueOf(dm.ydpi));
        addInfo("Tamaño (dp)", dm.widthPixels / dm.density + "x" + dm.heightPixels / dm.density);

        addSection("🧠 CPU");
        addInfo("Arquitectura", System.getProperty("os.arch", "?"));
        String cpuInfo = readCpuInfo();
        if (cpuInfo != null) addInfo("Info CPU", cpuInfo);
        addInfo("Cores", String.valueOf(Runtime.getRuntime().availableProcessors()));
        addInfo("ABIs soportadas", TextUtils.join(", ", Build.SUPPORTED_ABIS));

        addSection("💾 MEMORIA");
        Runtime rt = Runtime.getRuntime();
        long maxMem = rt.maxMemory();
        long totalMem = rt.totalMemory();
        long freeMem = rt.freeMemory();
        addInfo("Heap Max", formatBytes(maxMem));
        addInfo("Heap Total", formatBytes(totalMem));
        addInfo("Heap Libre", formatBytes(freeMem));
        addInfo("Heap Usado", formatBytes(totalMem - freeMem));
        addInfo("RAM total", getTotalRam());

        addSection("💿 ALMACENAMIENTO");
        addInfo("Interno total", formatBytes(getTotalInternalStorage()));
        addInfo("Interno usable", formatBytes(getAvailableInternalStorage()));
        if (hasSdCard()) {
            addInfo("SD Card", "Detectada");
            addInfo("SD total", formatBytes(getTotalSdStorage()));
            addInfo("SD libre", formatBytes(getAvailableSdStorage()));
        } else {
            addInfo("SD Card", "No detectada");
        }

        addSection("📶 WIFI / RED");
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wm != null) {
            WifiInfo wi = wm.getConnectionInfo();
            if (wi != null) {
                String ssid = wi.getSSID();
                addInfo("SSID", ssid != null ? ssid.replace("\"", "") : "?");
                addInfo("BSSID", wi.getBSSID() != null ? wi.getBSSID() : "?");
                addInfo("RSSI", wi.getRssi() + " dBm");
                int ipRaw = wi.getIpAddress();
                String ipStr = (ipRaw & 0xFF) + "." +
                    ((ipRaw >> 8) & 0xFF) + "." +
                    ((ipRaw >> 16) & 0xFF) + "." +
                    ((ipRaw >> 24) & 0xFF);
                addInfo("IP Local", ipStr);
                addInfo("MAC", wi.getMacAddress() != null ? wi.getMacAddress() : "?");
                addInfo("Frecuencia", wi.getFrequency() + " MHz");
                addInfo("Velocidad", wi.getLinkSpeed() + " Mbps");
                addInfo("WiFi Estándar", getWifiStandard(wi));
            }
            DhcpInfo dhcp = wm.getDhcpInfo();
            if (dhcp != null) {
                String gw = (dhcp.gateway & 0xFF) + "." +
                    ((dhcp.gateway >> 8) & 0xFF) + "." +
                    ((dhcp.gateway >> 16) & 0xFF) + "." +
                    ((dhcp.gateway >> 24) & 0xFF);
                addInfo("Gateway", gw);
                String dns = (dhcp.dns1 & 0xFF) + "." +
                    ((dhcp.dns1 >> 8) & 0xFF) + "." +
                    ((dhcp.dns1 >> 16) & 0xFF) + "." +
                    ((dhcp.dns1 >> 24) & 0xFF);
                addInfo("DNS1", dns);
                addInfo("Lease Dur.", dhcp.leaseDuration + "s");
            }
            addInfo("WiFi Activo", isWifiConnected() ? "Sí" : "No");
        }

        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo active = cm.getActiveNetworkInfo();
            addInfo("Tipo de red", active != null ? active.getTypeName() : "Desconectado");
            addInfo("Roaming", active != null && active.isRoaming() ? "Sí" : "No");
        }

        // Mostrar IPs de todas las interfaces
        addSection("🌐 INTERFACES DE RED");
        try {
            List<NetworkInterface> ifaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface iface : ifaces) {
                if (!iface.isUp() || iface.isLoopback()) continue;
                List<InetAddress> addrs = Collections.list(iface.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        addInfo(iface.getName(), addr.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            addInfo("Error", e.getMessage());
        }

        addSection("📦 APPS INSTALADAS (Android Auto)");
        PackageManager pm = getPackageManager();
        String[] packagesToCheck = {
            "com.google.android.projection.gearhead",
            "com.google.android.gms",
            "com.android.chrome",
            "com.google.android.apps.maps",
            "com.google.android.youtube"
        };
        for (String pkg : packagesToCheck) {
            try {
                PackageInfo pi = pm.getPackageInfo(pkg, 0);
                String ver = pi.versionName != null ? pi.versionName : "?";
                addInfo(pkg, "Instalado v" + ver);
            } catch (PackageManager.NameNotFoundException e) {
                addInfo(pkg, "No instalado");
            }
        }

        // Bluetooth
        addSection("📡 BLUETOOTH");
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                    addInfo("Bluetooth", "Permiso BLUETOOTH_CONNECT no concedido");
                }
            }
            android.bluetooth.BluetoothAdapter bt = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (bt != null) {
                addInfo("Nombre", bt.getName());
                try {
                    addInfo("Dirección", bt.getAddress());
                } catch (Exception e) {
                    addInfo("Dirección", "No accesible (Android 12+ sin permiso)");
                }
                addInfo("Estado", bt.isEnabled() ? "Encendido" : "Apagado");
                try {
                    addInfo("Modo descubrible",
                        bt.getScanMode() == android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
                            ? "Sí" : "No");
                } catch (Exception ignored) {}
            } else {
                addInfo("Bluetooth", "No soportado");
            }
        } catch (Exception e) {
            addInfo("Bluetooth", "Error: " + e.getMessage());
        }

        // USB
        addSection("🔌 USB");
        try {
            android.hardware.usb.UsbManager usb = (android.hardware.usb.UsbManager) getSystemService(USB_SERVICE);
            if (usb != null) {
                java.util.HashMap<String, android.hardware.usb.UsbDevice> devices = usb.getDeviceList();
                if (devices.isEmpty()) {
                    addInfo("Dispositivos USB", "Ninguno conectado");
                } else {
                    for (String key : devices.keySet()) {
                        android.hardware.usb.UsbDevice dev = devices.get(key);
                        addInfo("USB: " + dev.getProductName(),
                            "Vendor=" + dev.getVendorId() + " Product=" + dev.getProductId());
                    }
                }
            }
        } catch (Exception e) {
            addInfo("USB", "Error: " + e.getMessage());
        }

        // Sensores
        addSection("🎯 SENSORES");
        try {
            android.hardware.SensorManager sm = (android.hardware.SensorManager) getSystemService(SENSOR_SERVICE);
            if (sm != null) {
                List<android.hardware.Sensor> sensors = sm.getSensorList(android.hardware.Sensor.TYPE_ALL);
                addInfo("Total sensores", String.valueOf(sensors.size()));
                for (android.hardware.Sensor s : sensors) {
                    addInfo("  " + s.getName(), s.getVendor() + " v" + s.getVersion());
                }
            }
        } catch (Exception e) {
            addInfo("Sensores", "Error: " + e.getMessage());
        }

        log("=== FIN DEL ESCANEO ===");
    }

    // ────────────────────────────────────────────────────────
    //  TEST DE CONEXIÓN A PHONECAST
    // ────────────────────────────────────────────────────────

    private void testPhoneCastConnection() {
        log("=== TEST CONEXIÓN PHONECAST ===");

        // Obtener gateway (posible IP del hotspot del teléfono)
        String testIp = "192.168.43.1";
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wm != null && wm.getDhcpInfo() != null) {
            int gw = wm.getDhcpInfo().gateway;
            testIp = (gw & 0xFF) + "." +
                ((gw >> 8) & 0xFF) + "." +
                ((gw >> 16) & 0xFF) + "." +
                ((gw >> 24) & 0xFF);
        }
        log("Probando conexión a: " + testIp);

        // Test ping
        log(">> Ping a " + testIp + "...");
        try {
            InetAddress addr = InetAddress.getByName(testIp);
            boolean reachable = addr.isReachable(3000);
            log(reachable ? "✓ Ping exitoso" : "✗ Ping falló (timeout 3s)");
        } catch (Exception e) {
            log("✗ Error ping: " + e.getMessage());
        }

        // Test puerto 8080 (video stream)
        log(">> Test puerto 8080 (video)...");
        try {
            URL url = new URL("http://" + testIp + ":8080/stream");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            log("✓ Puerto 8080 responde (HTTP " + code + ")");
            conn.disconnect();
        } catch (java.net.ConnectException e) {
            log("✗ Puerto 8080: Conexión rechazada (PhoneCast no está transmitiendo)");
        } catch (java.net.SocketTimeoutException e) {
            log("✗ Puerto 8080: Timeout (firewall o IP incorrecta)");
        } catch (Exception e) {
            log("✗ Puerto 8080: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }

        // Test puerto 8081 (touch)
        log(">> Test puerto 8081 (touch)...");
        try {
            java.net.Socket s = new java.net.Socket();
            s.connect(new java.net.InetSocketAddress(testIp, 8081), 3000);
            log("✓ Puerto 8081 responde");
            s.close();
        } catch (java.net.ConnectException e) {
            log("✗ Puerto 8081: Conexión rechazada");
        } catch (java.net.SocketTimeoutException e) {
            log("✗ Puerto 8081: Timeout");
        } catch (Exception e) {
            log("✗ Puerto 8081: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }

        log("=== FIN DIAGNÓSTICO ===");
    }

    // ────────────────────────────────────────────────────────
    //  GUARDAR REPORTE
    // ────────────────────────────────────────────────────────

    private void saveReport() {
        String report = tvLog.getText().toString();
        if (report.isEmpty()) {
            Toast.makeText(this, "No hay datos para guardar", Toast.LENGTH_SHORT).show();
            return;
        }

        String filename = "RadioInfo_" +
            new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".txt";

        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, filename);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(report.getBytes());
            Toast.makeText(this, "Reporte guardado en:\n" + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            log(">> Reporte guardado: " + file.getAbsolutePath());
        } catch (Exception e) {
            Toast.makeText(this, "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            log(">> Error guardando reporte: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────────────
    //  HELPERS
    // ────────────────────────────────────────────────────────

    private void addSection(String title) {
        View v = getLayoutInflater().inflate(R.layout.item_section, null);
        ((TextView) v.findViewById(R.id.tv_section_title)).setText(title);
        llInfo.addView(v);
        log("[" + title + "]");
    }

    private void addInfo(String label, String value) {
        View v = getLayoutInflater().inflate(R.layout.item_info, null);
        ((TextView) v.findViewById(R.id.tv_label)).setText(label);
        ((TextView) v.findViewById(R.id.tv_value)).setText(value != null ? value : "?");
        llInfo.addView(v);
        log(label + ": " + value);
    }

    private void log(String msg) {
        logBuffer.append(msg).append("\n");
        tvLog.setText(logBuffer.toString());
        // Auto-scroll al final
        ((ScrollView) tvLog.getParent()).fullScroll(View.FOCUS_DOWN);
    }

    private String getAndroidCodename(int api) {
        switch (api) {
            case 21: return "5.0 Lollipop";
            case 22: return "5.1 Lollipop";
            case 23: return "6.0 Marshmallow";
            case 24: return "7.0 Nougat";
            case 25: return "7.1 Nougat";
            case 26: return "8.0 Oreo";
            case 27: return "8.1 Oreo";
            case 28: return "9.0 Pie";
            case 29: return "10.0 Q";
            case 30: return "11.0 R";
            case 31: return "12.0 S";
            case 32: return "12L";
            case 33: return "13.0 Tiramisu";
            case 34: return "14.0 UpsideDownCake";
            case 35: return "15.0 VanillaIceCream";
            default: return api + " (?)";
        }
    }

    private String getDensityName(int dpi) {
        if (dpi <= 120) return "ldpi";
        if (dpi <= 160) return "mdpi";
        if (dpi <= 240) return "hdpi";
        if (dpi <= 320) return "xhdpi";
        if (dpi <= 480) return "xxhdpi";
        if (dpi <= 640) return "xxxhdpi";
        return "xxxxhdpi";
    }

    private String readCpuInfo() {
        try {
            BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"));
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = br.readLine()) != null) {
                if (line.contains("Hardware") || line.contains("Processor") || line.contains("model name")) {
                    String[] parts = line.split(":");
                    if (parts.length > 1) {
                        sb.append(parts[1].trim()).append(" ");
                    }
                }
            }
            br.close();
            return sb.length() > 0 ? sb.toString().trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String getTotalRam() {
        try {
            BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"));
            String line = br.readLine();
            br.close();
            if (line != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    long kb = Long.parseLong(parts[1]);
                    return formatBytes(kb * 1024);
                }
            }
        } catch (Exception ignored) {}
        return "?";
    }

    private long getTotalInternalStorage() {
        StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        return stat.getBlockCountLong() * stat.getBlockSizeLong();
    }

    private long getAvailableInternalStorage() {
        StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
    }

    private boolean hasSdCard() {
        File ext = new File("/storage/extSdCard");
        if (ext.exists()) return true;
        File storage = new File("/storage");
        if (storage.exists() && storage.isDirectory()) {
            for (File f : storage.listFiles()) {
                if (f.isDirectory() && !f.getName().equals("emulated") && !f.getName().equals("self")) {
                    return true;
                }
            }
        }
        return false;
    }

    private long getTotalSdStorage() {
        File storage = new File("/storage");
        if (storage.exists() && storage.isDirectory()) {
            for (File f : storage.listFiles()) {
                if (f.isDirectory() && !f.getName().equals("emulated") && !f.getName().equals("self")) {
                    try {
                        StatFs stat = new StatFs(f.getAbsolutePath());
                        return stat.getBlockCountLong() * stat.getBlockSizeLong();
                    } catch (Exception ignored) {}
                }
            }
        }
        return 0;
    }

    private long getAvailableSdStorage() {
        File storage = new File("/storage");
        if (storage.exists() && storage.isDirectory()) {
            for (File f : storage.listFiles()) {
                if (f.isDirectory() && !f.getName().equals("emulated") && !f.getName().equals("self")) {
                    try {
                        StatFs stat = new StatFs(f.getAbsolutePath());
                        return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
                    } catch (Exception ignored) {}
                }
            }
        }
        return 0;
    }

    private boolean isWifiConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return ni != null && ni.isConnected();
    }

    private String getWifiStandard(WifiInfo wi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            int std = wi.getWifiStandard();
            switch (std) {
                case 6: return "WiFi 6 (802.11ax)";
                case 5: return "WiFi 5 (802.11ac)";
                case 4: return "WiFi 4 (802.11n)";
                case 3: return "802.11g";
                case 2: return "802.11a";
                case 1: return "802.11b";
                default: return "? (std=" + std + ")";
            }
        }
        return "N/A (API < 30)";
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    // ────────────────────────────────────────────────────────
    //  ITEM LAYOUTS (inflados programáticamente)
    // ────────────────────────────────────────────────────────

    static class SectionViewHolder {
        TextView title;
    }
}
