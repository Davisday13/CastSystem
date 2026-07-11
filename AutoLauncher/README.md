# AutoLauncher

App Android nativa (Kotlin) que recrea una interfaz tipo "launcher de auto" similar a Android Auto:

- Barra lateral izquierda oscura con indicadores de estado (señal, batería, hora, notificaciones) y accesos rápidos circulares (navegación, música, teléfono, micrófono).
- Panel principal redondeado con fondo de ondas decorativas y una grilla de 4 columnas de apps, cada una con ícono circular de color y etiqueta debajo.
- Flechas de scroll arriba/abajo e indicador de barra de desplazamiento, igual que en la imagen de referencia.

Los íconos son genéricos (estilo Material Design, licencia Apache 2.0) — no se usan logos de marcas de terceros (WhatsApp, Spotify, VLC, etc.) para evitar problemas de marca registrada. Puedes reemplazarlos fácilmente por tus propios drawables.

## Cómo abrir el proyecto

1. Abre **Android Studio** (versión Koala/2024.1 o superior recomendada).
2. `File > Open` y selecciona la carpeta `AutoLauncher`.
3. Deja que Gradle sincronice (usa Android Gradle Plugin 8.5.0 y Kotlin 1.9.24).
4. Conecta un emulador o dispositivo (API 24+) y presiona **Run**.

## Estructura principal

```
app/src/main/java/com/example/autolauncher/
  MainActivity.kt        -> arma la pantalla, listas de apps y accesos rápidos
  AppItem.kt              -> modelos de datos (AppItem, SidebarItem)
  AppGridAdapter.kt        -> adapter de la grilla de apps (RecyclerView + GridLayoutManager)
  SidebarAdapter.kt        -> adapter de la barra lateral (RecyclerView vertical)

app/src/main/res/layout/
  activity_main.xml        -> layout raíz (columna lateral + panel con la grilla)
  item_app_icon.xml         -> ítem individual de la grilla
  item_sidebar_icon.xml     -> ítem individual de la barra lateral

app/src/main/res/drawable/
  bg_root_gradient.xml, bg_wave_lines.xml, bg_panel_rounded.xml -> fondos
  bg_circle_tint.xml, bg_circle_white.xml                        -> fondos circulares de íconos
  ic_*.xml                                                        -> íconos vectoriales genéricos
```

## Personalizar

- **Agregar/quitar apps**: edita la lista `apps` dentro de `MainActivity.kt` (nombre, ícono, color).
- **Cambiar accesos rápidos de la barra lateral**: edita la lista `sidebarItems` en `MainActivity.kt`.
- **Abrir apps reales**: dentro del callback `onClick` de `AppGridAdapter`, reemplaza el `Toast` por un `Intent` para lanzar la app correspondiente (por ejemplo, usando `packageManager.getLaunchIntentForPackage(...)`).
- **Colores/tema**: todo está centralizado en `res/values/colors.xml`.

## Notas técnicas

- `minSdk 24`, `targetSdk/compileSdk 34`.
- Orientación forzada a `landscape` (típico de pantallas de auto).
- Usa `RecyclerView` + `GridLayoutManager` (4 columnas) para la grilla principal, y `RecyclerView` + `LinearLayoutManager` para la barra lateral — fácil de escalar con más apps o accesos.
- Si vas a integrar con Android Auto de verdad (no solo el diseño visual), necesitarás la librería `androidx.car.app:app` y seguir las plantillas oficiales de Car App Library, ya que Android Auto no permite layouts libres por razones de seguridad al conducir — solo se pueden usar sus templates predefinidos (listas, grid template, mapas, etc.).
