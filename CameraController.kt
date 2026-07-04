package com.procam.rn10s.camera

import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "CameraController"

/**
 * Envuelve CameraX + Camera2Interop para exponer los controles manuales
 * que necesita un flujo de cámara "profesional": ISO, obturador, balance
 * de blancos en Kelvin y el perfil LOG (via tonemap curve manual).
 *
 * Fase 1: valida en tiempo real qué soporta realmente el HAL del
 * Redmi Note 10S en este build de MIUI/Android 13 — nunca asumas
 * soporte, siempre consulta CameraCharacteristics.
 */
@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var camera2CameraInfo: Camera2CameraInfo? = null

    private var isoValue = 400
    private var shutterDenominator = 50 // 1/50s
    private var whiteBalanceKelvin = 5600
    private var logProfileEnabled = false

    var onLogSupportKnown: ((Boolean) -> Unit)? = null

    fun start() {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            cameraProvider = providerFuture.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.FHD))
            .build()
        val newVideoCapture = VideoCapture.withOutput(recorder)

        val newPreview = Preview.Builder()

        // Puente a Camera2: aquí es donde inyectamos ISO, shutter, WB
        // y el tonemap curve manual. CameraX no expone esto de forma
        // nativa por diseño, por eso usamos Camera2Interop.Extender.
        val extender = Camera2Interop.Extender(newPreview)
        applyManualRequestOptions(extender)

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        provider.unbindAll()
        val camera = provider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            newPreview.build(),
            newVideoCapture
        )

        preview = newPreview.build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        videoCapture = newVideoCapture
        camera2CameraInfo = Camera2CameraInfo.from(camera.cameraInfo)

        checkLogSupport()
    }

    /**
     * Consulta CameraCharacteristics para saber si este equipo, con esta
     * ROM, expone TONEMAP_MODE_CONTRAST_CURVE. Si no lo hace, el toggle
     * de LOG en la UI debe avisar que se usará la ruta de software
     * (Fase 2, shader GPU) en vez de fingir que el HAL lo soporta.
     */
    private fun checkLogSupport() {
        val info = camera2CameraInfo ?: return
        val availableModes = info.getCameraCharacteristic(
            CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES
        )
        val hardwareLevel = info.getCameraCharacteristic(
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL
        )
        val supportsManualTonemap = availableModes
            ?.contains(CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE) == true

        Log.i(TAG, "Hardware level=$hardwareLevel, tonemap manual soportado=$supportsManualTonemap")
        onLogSupportKnown?.invoke(supportsManualTonemap)
    }

    private fun applyManualRequestOptions(extender: Camera2Interop.Extender<Preview>) {
        extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF
        )
        extender.setCaptureRequestOption(
            CaptureRequest.SENSOR_SENSITIVITY, isoValue
        )
        extender.setCaptureRequestOption(
            CaptureRequest.SENSOR_EXPOSURE_TIME, exposureNanosFromDenominator(shutterDenominator)
        )
        extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF
        )

        if (logProfileEnabled) {
            extender.setCaptureRequestOption(
                CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE
            )
            extender.setCaptureRequestOption(
                CaptureRequest.TONEMAP_CURVE, LogCurve.buildTonemapCurve()
            )
        } else {
            extender.setCaptureRequestOption(
                CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_FAST
            )
        }
    }

    private fun exposureNanosFromDenominator(denominator: Int): Long {
        // 1 / denominator segundos, en nanosegundos.
        return (1_000_000_000L / denominator)
    }

    fun setIso(value: Int) {
        isoValue = value.coerceIn(50, 3200)
        bindCamera()
    }

    fun setShutterDenominator(value: Int) {
        shutterDenominator = value.coerceIn(8, 2000)
        bindCamera()
    }

    fun setWhiteBalanceKelvin(value: Int) {
        // Nota: Camera2 no tiene un control directo "Kelvin" universal;
        // en Fase 1 usamos AWB_MODE_OFF + ganancias de canal de color
        // (COLOR_CORRECTION_GAINS) calculadas a partir de Kelvin. Placeholder
        // aquí: guardamos el valor, el cálculo de ganancias se añade cuando
        // se calibre con la cámara real del Redmi Note 10S.
        whiteBalanceKelvin = value.coerceIn(2500, 10000)
        bindCamera()
    }

    fun setLogProfileEnabled(enabled: Boolean) {
        logProfileEnabled = enabled
        bindCamera()
    }

    fun startRecording(onEvent: (VideoRecordEvent) -> Unit) {
        val capture = videoCapture ?: return
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "ProCamRN10S_$name.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        }
        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        activeRecording = capture.output
            .prepareRecording(context, outputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context), onEvent)
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }
}
