package com.example.znakiai

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeOp.ResizeMethod
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DetectionFragment : Fragment() {
    private lateinit var previewView: PreviewView
    private lateinit var textView: TextView
    private lateinit var objectDetector: ObjectDetector
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_detection, container, false)
        previewView = view.findViewById(R.id.preview_view)
        textView = view.findViewById(R.id.text_view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val metadata = mapOf(
            "input_norm_mean" to listOf(0.0f),
            "input_norm_std" to listOf(1.0f)
        )

        val options = ObjectDetectorOptions.builder()
            .setMaxResults(5)
            .setScoreThreshold(0.5f)
            .setBaseOptions(BaseOptions.builder().build())
            .build()

        objectDetector = ObjectDetector.createFromFileAndOptions(
            requireContext(),
            "monetary.tflite",
            options
        )

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ImageAnalyzer())
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (exc: Exception) {
                Toast.makeText(requireContext(), "Failed to bind camera use case", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private inner class ImageAnalyzer : ImageAnalysis.Analyzer {
        @SuppressLint("UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val bitmap = mediaImage.toBitmap()
                val tensorImage = preprocessImage(bitmap)
                val results = objectDetector.detect(tensorImage)

                var totalCoins = 0
                var totalValue = 0.0

                results.forEach { result ->
                    totalCoins++
                    val label = result.categories.firstOrNull()?.label
                    val value = labelToValue(label)
                    totalValue += value
                }

                activity?.runOnUiThread {
                    textView.text = "Wykryto monet: $totalCoins\nWartość: ${String.format("%.2f", totalValue)} PLN"
                }
            }
            imageProxy.close()
        }

        private fun labelToValue(label: String?): Double {
            return when (label) {
                "1gr" -> 0.01
                "2gr" -> 0.02
                "5gr" -> 0.05
                "10gr" -> 0.10
                "20gr" -> 0.20
                "50gr" -> 0.50
                "1zl" -> 1.0
                "2zl" -> 2.0
                "5zl" -> 5.0
                else -> 0.0
            }
        }

        private fun preprocessImage(bitmap: Bitmap): TensorImage {
            val tensorImage = TensorImage.fromBitmap(bitmap)
            val imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(300, 300, ResizeMethod.BILINEAR)) // Replace with your model's expected size
                .add(NormalizeOp(0.0f, 1.0f)) // Normalizing to [0, 1] range
                .build()
            return imageProcessor.process(tensorImage)
        }

        private fun Image.toBitmap(): Bitmap {
            val buffer = planes[0].buffer
            buffer.rewind()
            val bytes = ByteArray(buffer.capacity())
            buffer.get(bytes)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(requireContext(), "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}