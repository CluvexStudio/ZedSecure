package dev.cluvex.zedsecure.qr

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.os.Bundle
import dev.cluvex.zedsecure.core.AppLog as Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import dev.cluvex.zedsecure.R
import dev.cluvex.zedsecure.domain.config.AmneziaQr
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class QrScanActivity : ComponentActivity() {
    private val decoded = AtomicBoolean(false)
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private val amneziaChunks = AmneziaQr.Assembler()

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),

                DecodeHintType.TRY_HARDER to true,
            ),
        )
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var hintView: TextView

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else finishWith(null)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surfaceView = SurfaceView(this)
        val hint = TextView(this).apply {
            text = getString(R.string.qr_scan_hint)
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xAA000000.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        setContentView(
            FrameLayout(this).apply {
                addView(
                    surfaceView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                addView(
                    hint,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { gravity = android.view.Gravity.BOTTOM },
                )
            },
        )
        hintView = hint

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = runCatching { future.get() }.getOrNull() ?: return@addListener finishWith(null)
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(::provideSurface) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analysisExecutor, ::analyse) }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }.onFailure {
                Log.e(TAG, "camera bind failed", it)
                finishWith(null)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun provideSurface(request: SurfaceRequest) {
        val holder = surfaceView.holder
        holder.setFixedSize(request.resolution.width, request.resolution.height)
        if (holder.surface.isValid) {
            request.provideSurface(holder.surface, ContextCompat.getMainExecutor(this)) {}
            return
        }
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) {
                request.provideSurface(h.surface, ContextCompat.getMainExecutor(this@QrScanActivity)) {}
                h.removeCallback(this)
            }

            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) = Unit
            override fun surfaceDestroyed(h: SurfaceHolder) = Unit
        })
    }

    private fun analyse(image: ImageProxy) {
        try {
            if (decoded.get()) return
            if (image.format != ImageFormat.YUV_420_888) return
            val plane = image.planes[0]
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining()).also { buffer.get(it) }

            val source = PlanarYUVLuminanceSource(
                data, plane.rowStride, image.height, 0, 0, image.width, image.height, false,
            )
            val result = runCatching {
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            }.getOrNull() ?: return

            if (AmneziaQr.chunk(result.text) != null) {
                val whole = amneziaChunks.offer(result.text)
                if (whole == null) {
                    val have = amneziaChunks.received
                    val want = amneziaChunks.expected
                    runOnUiThread {
                        hintView.text = getString(R.string.qr_scan_chunk_progress, have, want)
                    }
                    return
                }
                if (decoded.compareAndSet(false, true)) runOnUiThread { finishWith(whole) }
                return
            }

            if (decoded.compareAndSet(false, true)) {
                runOnUiThread { finishWith(result.text) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "frame decode failed: ${e.message}")
        } finally {
            reader.reset()
            image.close()
        }
    }

    private fun finishWith(text: String?) {
        setResult(
            if (text == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
            Intent().putExtra(EXTRA_RESULT, text),
        )
        finish()
    }

    override fun onDestroy() {
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "QrScan"
        const val EXTRA_RESULT = "qr_result"
    }
}
