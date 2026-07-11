package com.example.autolauncher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val clock = findViewById<TextView>(R.id.clockText)
        val now = Calendar.getInstance()
        clock.text = DateFormat.format("h:mm", now)

        val sidebarItems = listOf(
            SidebarItem(R.drawable.ic_navigation),
            SidebarItem(R.drawable.ic_music_note),
            SidebarItem(R.drawable.ic_call),
            SidebarItem(R.drawable.ic_mic)
        )
        val sidebarRecycler = findViewById<RecyclerView>(R.id.sidebarRecycler)
        sidebarRecycler.layoutManager = LinearLayoutManager(this)
        sidebarRecycler.adapter = SidebarAdapter(sidebarItems) { item ->
            when (sidebarItems.indexOf(item)) {
                0 -> launchApp("com.google.android.apps.maps", "com.google.android.maps.MapsActivity")
                1 -> launchApp("com.spotify.music", "com.spotify.music.MainActivity")
                2 -> openPhone()
                3 -> openVoiceAssistant()
            }
        }

        val apps = listOf(
            AppItem("RadioCast", R.drawable.ic_play, "#4CAF50"),
            AppItem("Configuracion", R.drawable.ic_settings, "#1FA7D8"),
            AppItem("Radio FM", R.drawable.ic_radio, "#1E5FD1"),
            AppItem("Streaming", R.drawable.ic_cloud, "#F1701A"),
            AppItem("Podcasts", R.drawable.ic_headset, "#FFFFFF", onWhiteBg = true),
            AppItem("Mensajes", R.drawable.ic_chat, "#1FA7D8"),
            AppItem("Llamadas", R.drawable.ic_call, "#1E5FD1"),
            AppItem("Reproductor", R.drawable.ic_movie, "#F1701A"),
            AppItem("Clima", R.drawable.ic_wb_sunny, "#FFFFFF", onWhiteBg = true),
            AppItem("YouTube", R.drawable.ic_play, "#E0342A"),
            AppItem("Maps", R.drawable.ic_navigation, "#3BC855"),
            AppItem("Notas", R.drawable.ic_edit, "#1E5FD1")
        )

        val gridRecycler = findViewById<RecyclerView>(R.id.appsGridRecycler)
        gridRecycler.layoutManager = GridLayoutManager(this, 4)
        gridRecycler.adapter = AppGridAdapter(apps) { item ->
            when (item.name) {
                "RadioCast" -> launchRadioCast()
                "Radio FM" -> launchApp("com.android.fmplayer", null)
                "Streaming" -> launchApp("com.spotify.music", null)
                "Podcasts" -> launchApp("com.google.android.apps.podcasts", null)
                "Mensajes" -> launchApp("com.google.android.apps.messaging", null)
                "Llamadas" -> openPhone()
                "Reproductor" -> launchApp("com.google.android.youtube", null)
                "Clima" -> launchApp("com.google.android.apps.weather", null)
                "YouTube" -> launchApp("com.google.android.youtube", null)
                "Maps" -> launchApp("com.google.android.apps.maps", null)
                "Notas" -> launchApp("com.google.android.keep", null)
                "Configuracion" -> launchApp("com.android.settings", null)
            }
        }
    }

    private fun launchRadioCast() {
        try {
            val intent = Intent().apply {
                component = ComponentName("com.radiocast", "com.radiocast.MainActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = packageManager.getLaunchIntentForPackage("com.radiocast")
                if (intent != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "RadioCast no instalado", Toast.LENGTH_SHORT).show()
                }
            } catch (e2: Exception) {
                Toast.makeText(this, "Error al abrir RadioCast", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchApp(packageName: String, activityName: String?) {
        try {
            val intent = if (activityName != null) {
                Intent().apply {
                    component = ComponentName(packageName, activityName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                packageManager.getLaunchIntentForPackage(packageName) ?: return
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "App no disponible", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPhone() {
        val intent = Intent(Intent.ACTION_DIAL)
        startActivity(intent)
    }

    private fun openVoiceAssistant() {
        try {
            val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Asistente no disponible", Toast.LENGTH_SHORT).show()
        }
    }
}