package dev.cluvex.zedsecure.ui.glass

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.atan2
import kotlin.math.sqrt

@Composable
actual fun rememberGlassTilt(enabled: Boolean): State<Float> {
    val context = LocalContext.current
    val angle = remember { mutableFloatStateOf(GlassLightAngle) }
    val manager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val sensor = remember(manager) {
        manager?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    LifecycleResumeEffect(enabled, sensor) {
        if (!enabled || manager == null || sensor == null) {
            angle.floatValue = GlassLightAngle
            return@LifecycleResumeEffect onPauseOrDispose { }
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val roll = Math.toDegrees(atan2(x.toDouble(), sqrt((y * y + z * z).toDouble()))).toFloat()
                val target = (GlassLightAngle + roll * 1.5f).coerceIn(-45f, 135f)
                angle.floatValue = angle.floatValue * 0.8f + target * 0.2f
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onPauseOrDispose { manager.unregisterListener(listener) }
    }
    return angle
}
