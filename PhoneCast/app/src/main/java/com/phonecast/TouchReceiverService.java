package com.phonecast;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * TouchReceiverService
 * Servicio auxiliar que permite recibir eventos de toque desde el radio.
 * La lógica real de recepción de toques está integrada en CastService (puerto 8081),
 * pero este servicio se declara en el Manifest para futuras extensiones
 * (p. ej., accesibilidad, ADB over WiFi).
 */
public class TouchReceiverService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Delegado: los toques ya se manejan en CastService#startTouchServer()
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
