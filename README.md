# ProCam RN10S — Fase 1

App de cámara propia, optimizada exclusivamente para el Redmi Note 10S
(MediaTek Helio G95, MIUI 14.0.11, Android 13 build `TPA1A.220624.014`).

## Cómo compilarlo sin Android Studio (en la nube, gratis)

No necesitas instalar nada en tu computadora. Usamos GitHub Actions,
que ya viene configurado en `.github/workflows/build-apk.yml`:

1. Crea una cuenta gratis en [github.com](https://github.com) si no
   tienes una.
2. Crea un repositorio nuevo (puede ser privado) y sube el contenido
   de esta carpeta `ProCamRN10S` (arrastra los archivos desde la web
   de GitHub, o usa "Add file → Upload files").
3. Ve a la pestaña **Actions** del repositorio. Debería aparecer el
   workflow "Compilar APK debug" ejecutándose solo, o dale
   "Run workflow" manualmente.
4. Cuando termine (unos 3-5 minutos), entra al resultado y descarga
   el artefacto `ProCamRN10S-debug` — es un `.zip` que contiene el
   `.apk`.
5. Pasa ese `.apk` a tu Redmi Note 10S (por cable, Google Drive, lo
   que prefieras) y ábrelo desde el explorador de archivos del
   teléfono. MIUI te pedirá permitir "instalar apps de origen
   desconocido" — acéptalo solo para este archivo.

Esto compila la app en un servidor de GitHub, no en tu equipo. Sigue
siendo necesario instalar el resultado en el teléfono físico, porque
ninguna nube puede simular el HAL de cámara real de tu Redmi Note
10S — eso solo se puede leer en el hardware real.

## Cómo abrir el proyecto (alternativa con Android Studio)

1. Abre Android Studio (Iguana o más reciente).
2. `File → Open` y selecciona la carpeta `ProCamRN10S`.
3. Deja que Gradle sincronice (usa AGP 8.4.0 / Kotlin 1.9.24, ajusta si tu
   Android Studio pide otra versión).
4. Conecta el Redmi Note 10S por USB con depuración activada y ejecuta.

Este entorno de análisis no tiene el SDK de Android ni acceso a los
repositorios de Google/Maven, así que el proyecto no se compiló aquí —
está listo para abrir y compilar en tu máquina.

## Qué hace esta Fase 1

- Preview de cámara en vivo (CameraX `Preview`).
- Grabación de video H.264/HEVC a través de `CameraX VideoCapture` +
  `Recorder` (lo único que el Helio G95 acelera de verdad por hardware).
- Controles manuales de **ISO**, **velocidad de obturador** y **balance
  de blancos**, inyectados vía `Camera2Interop.Extender` — CameraX no
  expone esto de forma nativa, por eso el puente a Camera2.
- Un toggle de **perfil LOG** que intenta activar
  `TONEMAP_MODE_CONTRAST_CURVE` con una curva logarítmica calculada en
  `LogCurve.kt`.

## Sobre el perfil LOG — lo que hay que validar en el equipo real

El código intenta el camino "correcto": pedirle al HAL de la cámara que
aplique una curva de tono manual (`TONEMAP_CURVE`) antes de que la imagen
llegue al encoder. Esto es exactamente cómo apps de cámara "pro" logran
un perfil plano sin necesitar un codec especial — es una curva, no un
formato de archivo distinto.

**Pero esto depende de qué exponga el HAL de MIUI en este chip.** La app,
al arrancar la cámara, consulta:

```kotlin
CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES
CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL
```

y te avisa en pantalla (`logStatusText`) si el modo manual está
disponible o no. Hay dos escenarios:

1. **Si `TONEMAP_MODE_CONTRAST_CURVE` está en la lista** → el LOG
   funciona tal cual está en este código. Falta calibrar los
   coeficientes `LIFT`/`GAIN` de `LogCurve.kt` comparando contra una
   carta de grises real, usando el histograma/waveform que construiremos
   en la Fase 3.
2. **Si no está** (posible en algunos HAL de MediaTek/MIUI que solo
   exponen `FAST`/`HIGH_QUALITY`) → hay que mover el LOG a una segunda
   ruta: aplicar la misma fórmula de `LogCurve` como shader OpenGL sobre
   el buffer de cámara antes de pasarlo al encoder (pipeline
   `Surface → GLSurfaceView/EGL → MediaCodec`). Esa es la Fase 2 de
   `camera/` — no está incluida aún en este commit porque primero hay
   que confirmar en tu Redmi Note 10S físico cuál de los dos escenarios
   aplica. Ejecuta la app, mira el texto en la esquina superior
   izquierda, y me confirmas qué dice.

## Pendiente para las siguientes fases

- Fase 2: pipeline GPU (OpenGL) para LOG garantizado sin depender del HAL,
  más LUTs `.cube` y focus peaking.
- Fase 3: histograma y waveform en vivo.
- Fase 4: gestión térmica y foreground service para grabaciones largas
  en MIUI (ver análisis previo).
- Cálculo real de `COLOR_CORRECTION_GAINS` a partir de Kelvin para el
  control de balance de blancos (hoy es un placeholder).
