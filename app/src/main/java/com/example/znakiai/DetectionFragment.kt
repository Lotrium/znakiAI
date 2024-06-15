package com.example.znakiai

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class DetectionFragment : Fragment() {

    private lateinit var viewFinder: PreviewView
    private lateinit var textView: TextView
    private lateinit var interpreter: Interpreter
    private val coinCount = mutableMapOf<String, Int>()
    private var totalValue = 0.0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_detection, container, false)
        viewFinder = view.findViewById(R.id.view_finder)
        textView = view.findViewById(R.id.text_view)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load the TensorFlow Lite model
        try {
            interpreter = Interpreter(loadModelFile())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize interpreter", e)
            // Handle the error appropriately (e.g., show an error message to the user)
            return
        }

        // Set up camera
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun loadModelFile(): MappedByteBuffer {
        val modelPath = "monetary.tflite"
        val fileDescriptor = requireContext().assets.openFd(modelPath)
        val inputStream = fileDescriptor.createInputStream()
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview: Preview = Preview.Builder()
            .build()

        val cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview.setSurfaceProvider(viewFinder.surfaceProvider)

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(224, 224))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(requireContext()), ImageAnalyzer())

        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
    }

    private inner class ImageAnalyzer : ImageAnalysis.Analyzer {
        @SuppressLint("UnsafeExperimentalUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val bitmap = imageProxy.toBitmap()
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
            val byteBuffer = convertBitmapToByteBuffer(resizedBitmap)

            val inputShape = interpreter.getInputTensor(0).shape()
            val outputShape = interpreter.getOutputTensor(0).shape()

            val inputSize = inputShape[1] * inputShape[2] * inputShape[3]
            val outputSize = outputShape[1]

            if (byteBuffer.remaining() != inputSize) {
                Log.e(TAG, "Input buffer size mismatch. Expected: $inputSize, Actual: ${byteBuffer.remaining()}")
                imageProxy.close()
                return
            }

            val output = Array(1) { FloatArray(outputSize) }

            try {
                interpreter.run(byteBuffer, output)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to run inference", e)
                imageProxy.close()
                return
            }

            val label = getLabel(output[0])
            updateCoinCount(label)
            updateTotalValue(label)
            updateUI()

            imageProxy.close()
        }

        private fun ImageProxy.toBitmap(): Bitmap {
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
            val imageBytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }

        private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
            val byteBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3)
            byteBuffer.order(ByteOrder.nativeOrder())
            val intValues = IntArray(224 * 224)
            bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            var pixel = 0
            for (i in 0 until 224) {
                for (j in 0 until 224) {
                    val `val` = intValues[pixel++]
                    byteBuffer.putFloat((`val` shr 16 and 0xFF) / 255f)
                    byteBuffer.putFloat((`val` shr 8 and 0xFF) / 255f)
                    byteBuffer.putFloat((`val` and 0xFF) / 255f)
                }
            }
            return byteBuffer
        }

        private fun getLabel(output: FloatArray): String {
            val maxIndex = output.indices.maxBy { output[it] } ?: -1
            return when (maxIndex) {
                0 -> "1gr"
                1 -> "2gr"
                2 -> "5gr"
                3 -> "10gr"
                4 -> "20gr"
                5 -> "50gr"
                6 -> "1zl"
                7 -> "2zl"
                8 -> "5zl"
                else -> "Unknown"
            }
        }

        private fun updateCoinCount(label: String) {
            coinCount[label] = coinCount.getOrDefault(label, 0) + 1
        }

        private fun updateTotalValue(label: String) {
            totalValue += getCoinValue(label)
        }

        private fun getCoinValue(label: String): Double {
            return when (label) {
                "1gr" -> 0.01
                "2gr" -> 0.02
                "5gr" -> 0.05
                "10gr" -> 0.1
                "20gr" -> 0.2
                "50gr" -> 0.5
                "1zl" -> 1.0
                "2zl" -> 2.0
                "5zl" -> 5.0
                else -> 0.0 // Unknown or undetected coins
            }
        }

        private fun updateUI() {
            val coinCountText = coinCount.entries.joinToString(separator = "\n") { "${it.key}: ${it.value}" }
            val totalValueText = "%.2f".format(totalValue)
            val resultString = "Wykryto monet:\n$coinCountText\nWartość: $totalValueText PLN"
            textView.text = resultString
        }
    }

    companion object {
        private const val TAG = "DetectionFragment"
    }
}