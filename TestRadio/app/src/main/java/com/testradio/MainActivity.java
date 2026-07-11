package com.testradio;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.FileReader;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StringBuilder sb = new StringBuilder();
        sb.append("=== TEST RADIO ===\n\n");
        sb.append("Fabricante: ").append(Build.MANUFACTURER).append("\n");
        sb.append("Modelo: ").append(Build.MODEL).append("\n");
        sb.append("Dispositivo: ").append(Build.DEVICE).append("\n");
        sb.append("Producto: ").append(Build.PRODUCT).append("\n");
        sb.append("Hardware: ").append(Build.HARDWARE).append("\n");
        sb.append("Android: ").append(Build.VERSION.RELEASE).append("\n");
        sb.append("SDK API: ").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("Build: ").append(Build.DISPLAY).append("\n");
        if (Build.VERSION.SDK_INT >= 23) {
            sb.append("Security Patch: ").append(Build.VERSION.SECURITY_PATCH).append("\n");
        }
        sb.append("CPU ABIs: ");
        for (String abi : Build.SUPPORTED_ABIS) sb.append(abi).append(" ");
        sb.append("\n");
        sb.append("Cores: ").append(Runtime.getRuntime().availableProcessors()).append("\n");

        // RAM
        try {
            BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"));
            String line = br.readLine();
            br.close();
            if (line != null) sb.append("RAM: ").append(line).append("\n");
        } catch (Exception e) {
            sb.append("RAM: error reading\n");
        }

        sb.append("\nWiFi: ");
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager)
                getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null) {
                android.net.wifi.WifiInfo wi = wm.getConnectionInfo();
                if (wi != null) {
                    sb.append(wi.getSSID()).append("\n");
                    int ip = wi.getIpAddress();
                    sb.append("IP: ").append(ip & 0xFF).append(".").append((ip >> 8) & 0xFF)
                        .append(".").append((ip >> 16) & 0xFF).append(".").append((ip >> 24) & 0xFF).append("\n");
                } else {
                    sb.append("No conectado\n");
                }
            }
        } catch (Exception e) {
            sb.append("Error: ").append(e.getMessage()).append("\n");
        }

        sb.append("\nInterfaces de red:\n");
        try {
            java.util.List<java.net.NetworkInterface> ifaces =
                java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces());
            for (java.net.NetworkInterface iface : ifaces) {
                if (!iface.isUp()) continue;
                java.util.List<java.net.InetAddress> addrs =
                    java.util.Collections.list(iface.getInetAddresses());
                for (java.net.InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        sb.append("  ").append(iface.getName()).append(": ")
                            .append(addr.getHostAddress()).append("\n");
                    }
                }
            }
        } catch (Exception e) {
            sb.append("  Error: ").append(e.getMessage()).append("\n");
        }

        sb.append("\nApps instaladas:\n");
        String[] pkgs = {"com.google.android.projection.gearhead", "com.phonecast",
            "com.google.android.apps.maps", "com.google.android.youtube"};
        for (String pkg : pkgs) {
            try {
                String ver = getPackageManager().getPackageInfo(pkg, 0).versionName;
                sb.append("  ").append(pkg).append(" v").append(ver).append("\n");
            } catch (Exception e) {
                sb.append("  ").append(pkg).append(" - NO INSTALADO\n");
            }
        }

        TextView tv = new TextView(this);
        tv.setText(sb.toString());
        tv.setTextSize(12);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setPadding(16, 16, 16, 16);
        tv.setTextColor(0xFFE0E0E0);
        tv.setBackgroundColor(0xFF1A1A2E);

        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        setContentView(sv);
    }
}
