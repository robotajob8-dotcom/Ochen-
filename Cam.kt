package com.example.remote

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

// Снимок без превью: открываем камеру, ждём несколько кадров (чтобы
// выставилась экспозиция), сохраняем четвёртый.
object Cam {

    @SuppressLint("MissingPermission")
    fun capture(ctx: Context, front: Boolean, out: File, onDone: (Exception?) -> Unit) {
        val mgr = ctx.getSystemService(CameraManager::class.java)
        val facing = if (front) CameraCharacteristics.LENS_FACING_FRONT
                     else CameraCharacteristics.LENS_FACING_BACK
        val id = mgr.cameraIdList.firstOrNull {
            mgr.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing
        } ?: return onDone(Exception("камера не найдена"))

        val chars = mgr.getCameraCharacteristics(id)
        val size = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            .getOutputSizes(ImageFormat.JPEG)
            .filter { it.width <= 1920 }
            .maxByOrNull { it.width * it.height }
            ?: return onDone(Exception("нет размера JPEG"))
        val orient = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

        val ht = HandlerThread("cam").also { it.start() }
        val h = Handler(ht.looper)
        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
        val done = AtomicBoolean(false)
        var device: CameraDevice? = null

        fun finish(e: Exception?) {
            if (!done.compareAndSet(false, true)) return
            runCatching { device?.close() }
            runCatching { reader.close() }
            ht.quitSafely()
            onDone(e)
        }

        var frames = 0
        reader.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            frames++
            if (frames >= 4) {
                try {
                    val buf = img.planes[0].buffer
                    val bytes = ByteArray(buf.remaining())
                    buf.get(bytes)
                    out.writeBytes(bytes)
                    img.close()
                    finish(null)
                } catch (e: Exception) {
                    img.close()
                    finish(e)
                }
            } else img.close()
        }, h)

        h.postDelayed({ finish(Exception("таймаут")) }, 10000)

        try {
            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) {
                    device = d
                    try {
                        val req = d.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(reader.surface)
                            set(CaptureRequest.JPEG_ORIENTATION, orient)
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        }.build()
                        @Suppress("DEPRECATION")
                        d.createCaptureSession(listOf(reader.surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(s: CameraCaptureSession) {
                                    try { s.setRepeatingRequest(req, null, h) }
                                    catch (e: Exception) { finish(e) }
                                }
                                override fun onConfigureFailed(s: CameraCaptureSession) =
                                    finish(Exception("сессия не создана"))
                            }, h)
                    } catch (e: Exception) { finish(e) }
                }
                override fun onDisconnected(d: CameraDevice) { device = d; finish(Exception("камера отключена")) }
                override fun onError(d: CameraDevice, e: Int) { device = d; finish(Exception("ошибка камеры $e")) }
            }, h)
        } catch (e: Exception) { finish(e) }
    }
}
