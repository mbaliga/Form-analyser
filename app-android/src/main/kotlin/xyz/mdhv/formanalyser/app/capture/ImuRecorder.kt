package xyz.mdhv.formanalyser.app.capture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xyz.mdhv.baseline.engine.model.Sample
import xyz.mdhv.baseline.engine.model.TimeSeries
import xyz.mdhv.formanalyser.archery.ArcheryChannels
import kotlin.math.sqrt

/**
 * Captures the phone IMU into the engine's [TimeSeries] shape (handoff §6: SensorManager,
 * SENSOR_DELAY_FASTEST). Gyro is converted to deg/s and accelerometer to g, matching the
 * units [xyz.mdhv.formanalyser.archery.ArcheryFeatureExtractor] expects.
 *
 * Accelerometer and gyroscope arrive on independent callbacks; we sample-and-hold the latest
 * accelerometer reading and emit one fused 6-channel sample per gyro event (the gyro is the
 * faster, motion-critical channel).
 */
class ImuRecorder(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyro: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accel: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var thread: HandlerThread? = null

    private val samples = ArrayList<Sample>(60_000)
    private val latestAccelG = DoubleArray(3) // x,y,z in g
    @Volatile private var haveAccel = false
    @Volatile private var recording = false

    /** Live hold-steadiness proxy for the capture gauge: smoothed angular-speed magnitude (deg/s). */
    private val _liveAngularSpeed = MutableStateFlow(0.0)
    val liveAngularSpeed: StateFlow<Double> = _liveAngularSpeed

    val isAvailable: Boolean get() = gyro != null && accel != null

    fun start() {
        if (recording) return
        samples.clear()
        haveAccel = false
        _liveAngularSpeed.value = 0.0
        val t = HandlerThread("imu-recorder").also { it.start() }
        thread = t
        val handler = Handler(t.looper)
        accel?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, handler) }
        gyro?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, handler) }
        recording = true
    }

    /** Stops capture and returns the recorded window, or null if too little was captured. */
    fun stop(): TimeSeries? {
        if (!recording) return null
        recording = false
        sensorManager.unregisterListener(this)
        thread?.quitSafely()
        thread = null
        val snapshot = ArrayList(samples)
        if (snapshot.size < 2) return null
        val durationSec = (snapshot.last().tNanos - snapshot.first().tNanos) / 1e9
        val rate = if (durationSec > 0) (snapshot.size - 1) / durationSec else 100.0
        return TimeSeries(CHANNELS, snapshot, rate)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                latestAccelG[0] = event.values[0] / G
                latestAccelG[1] = event.values[1] / G
                latestAccelG[2] = event.values[2] / G
                haveAccel = true
            }
            Sensor.TYPE_GYROSCOPE -> {
                if (!haveAccel) return // wait until we can fuse a full sample
                val gx = Math.toDegrees(event.values[0].toDouble())
                val gy = Math.toDegrees(event.values[1].toDouble())
                val gz = Math.toDegrees(event.values[2].toDouble())
                if (recording) {
                    samples.add(
                        Sample(
                            tNanos = event.timestamp,
                            values = doubleArrayOf(gx, gy, gz, latestAccelG[0], latestAccelG[1], latestAccelG[2]),
                        )
                    )
                }
                val mag = sqrt(gx * gx + gy * gy + gz * gz)
                // Exponential smoothing for a calm live readout.
                _liveAngularSpeed.value = 0.85 * _liveAngularSpeed.value + 0.15 * mag
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no-op */ }

    private companion object {
        const val G = 9.80665
        val CHANNELS = listOf(
            ArcheryChannels.GYRO_X, ArcheryChannels.GYRO_Y, ArcheryChannels.GYRO_Z,
            ArcheryChannels.ACC_X, ArcheryChannels.ACC_Y, ArcheryChannels.ACC_Z,
        )
    }
}
