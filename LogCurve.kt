package com.procam.rn10s.camera

import android.hardware.camera2.params.TonemapCurve
import kotlin.math.log10

/**
 * Genera una curva de tono estilo "LOG" (inspirada en el enfoque de Log-C /
 * S-Log: negros levantados, altas luces comprimidas, imagen "plana" para
 * corregir en postproducción).
 *
 * IMPORTANTE: esto es un punto de partida matemático, no una réplica de
 * ninguna curva propietaria. Los coeficientes (lift, gain) deben calibrarse
 * en el dispositivo real usando el waveform monitor de la propia app,
 * comparando contra una carta de grises.
 *
 * Esta curva solo funciona si el HAL de cámara del equipo soporta
 * TONEMAP_MODE_CONTRAST_CURVE (hay que verificarlo en tiempo de ejecución,
 * ver CameraController.isManualTonemapSupported). Si no está soportado,
 * la Fase 2 debe aplicar esta misma fórmula como shader GPU sobre el
 * buffer de preview/grabación en vez de depender del HAL.
 */
object LogCurve {

    // Negros levantados (0.0 = negro puro, sin recorte). Súbelo si el
    // waveform muestra que los negros se están "aplastando" a 0.
    private const val LIFT = 0.09f

    // Ganancia aplicada tras el logaritmo; controla cuánto se comprimen
    // las altas luces. Más bajo = más plano/"log", más alto = más contraste.
    private const val GAIN = 0.842f

    /**
     * Devuelve puntos (x_in, y_out) normalizados [0,1] siguiendo:
     *   y = LIFT + GAIN * log10(x * 9 + 1)
     * que es una aproximación práctica a una curva log de cine.
     */
    fun generatePoints(numPoints: Int = 32): FloatArray {
        require(numPoints >= 2)
        val points = FloatArray(numPoints * 2)
        for (i in 0 until numPoints) {
            val x = i.toFloat() / (numPoints - 1)
            val y = (LIFT + GAIN * log10(x * 9f + 1f)).coerceIn(0f, 1f)
            points[i * 2] = x
            points[i * 2 + 1] = y
        }
        return points
    }

    /**
     * Construye el TonemapCurve aplicando la misma curva a los tres
     * canales (aproximación neutra; en Fase 2 se puede desacoplar por
     * canal para simular respuestas de color tipo negativo de cine).
     */
    fun buildTonemapCurve(numPoints: Int = 32): TonemapCurve {
        val curve = generatePoints(numPoints)
        return TonemapCurve(curve, curve, curve)
    }

    /** Curva neutra (paso a través), para desactivar el modo LOG. */
    fun buildLinearCurve(): TonemapCurve {
        val linear = floatArrayOf(0f, 0f, 1f, 1f)
        return TonemapCurve(linear, linear, linear)
    }
}
