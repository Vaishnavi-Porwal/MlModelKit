package com.example.mlmodelkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var cameraImage: ImageView
    private lateinit var captureImgBtn: Button
    private lateinit var resultText: TextView
    private lateinit var doctorNameText: TextView
    private lateinit var hospitalNameText: TextView
    private lateinit var dateText: TextView
    private lateinit var copyTextBtn: Button
    private lateinit var nerModel: NERModel


    private var currentPhotoPath: String? = null
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        nerModel = NERModel(this)

        cameraImage = findViewById(R.id.cameraImage)
        captureImgBtn = findViewById(R.id.captureImgBtn)
        resultText = findViewById(R.id.resultText)
        doctorNameText = findViewById(R.id.doctorNameText)
        hospitalNameText = findViewById(R.id.hospitalNameText)
        dateText = findViewById(R.id.dateText)
        copyTextBtn = findViewById(R.id.copyTxtBtn)


        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    captureImage()
                } else {
                    Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        takePictureLauncher =
            registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
                if (success) {
                    currentPhotoPath?.let { path ->
                        val bitmap = BitmapFactory.decodeFile(path)
                        cameraImage.setImageBitmap(bitmap)
                        recognizeText(bitmap)
                    }
                }
            }
        captureImgBtn.setOnClickListener {
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.getDefault()
        ).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }

    }

    private fun captureImage() {
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: IOException) {
            Toast.makeText(this, "Error occurred while creating the file", Toast.LENGTH_SHORT)
                .show()
            null
        }
        photoFile?.also {
            val photoUri: Uri =
                FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", it)
            takePictureLauncher.launch(photoUri)
        }
    }

    private fun recognizeText(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image).addOnSuccessListener { ocrText ->

            val extractedText = ocrText.text
            if (extractedText.isEmpty()) {
                Toast.makeText(this, "No text detected!", Toast.LENGTH_SHORT).show()

            }
            resultText.text = extractedText
            resultText.movementMethod = ScrollingMovementMethod()

            CoroutineScope(Dispatchers.IO).launch {
                val nerResults = nerModel.predict(extractedText)
                withContext(Dispatchers.Main) {
                    doctorNameText.text = "Doctor: ${nerResults["DOCTOR"] ?: "Not Found"}"
                    hospitalNameText.text = "Hospital: ${nerResults["HOSPITAL"] ?: "Not Found"}"
                    dateText.text = "Date: ${nerResults["DATE"] ?: "Not Found"}"
                }
            }

            copyTextBtn.visibility = Button.VISIBLE
            copyTextBtn.setOnClickListener {
                val clipBoard = ContextCompat.getSystemService(
                    this,
                    android.content.ClipboardManager::class.java
                )
                val clip = android.content.ClipData.newPlainText("recognized text", ocrText.text)
                clipBoard?.setPrimaryClip(clip)
                Toast.makeText(this, "Text Copied", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to recognize text: ${e.message}", Toast.LENGTH_SHORT)
                .show()
        }
    }

    // Inner Class: NER Model Handler
    class NERModel(private val context: Context) {
        private var interpreter: Interpreter? = null
        private var wordDict: Map<String, Int> = emptyMap()
        private var labelDict: Map<Int, String> = emptyMap()

        init {
            loadModel()
            loadDictionaries()
        }

        private fun loadModel() {
            try {
                val assetFileDescriptor = context.assets.openFd("ner_model.tflite")
                val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
                val fileChannel = fileInputStream.channel
                val mappedByteBuffer: MappedByteBuffer =
                    fileChannel.map(FileChannel.MapMode.READ_ONLY, assetFileDescriptor.startOffset, assetFileDescriptor.declaredLength)
                interpreter = Interpreter(mappedByteBuffer)
            } catch (e: IOException) {
                Log.e("NERModel", "Error loading model", e)
            }
        }

        private fun loadDictionaries() {
            wordDict = loadWordDict()
            labelDict = loadLabelDict()
        }

        private fun loadWordDict(): Map<String, Int> {
            return try {
                val jsonString = context.assets.open("word_dict.json").bufferedReader().use { it.readText() }
                Gson().fromJson(jsonString, object : TypeToken<Map<String, Int>>() {}.type)
            } catch (e: IOException) {
                emptyMap()
            }
        }

        private fun loadLabelDict(): Map<Int, String> {
            return try {
                val jsonString = context.assets.open("label_dict.json").bufferedReader().use { it.readText() }
                val stringMap: Map<String, String> = Gson().fromJson(jsonString, object : TypeToken<Map<String, String>>() {}.type)

                // Convert String keys to Integer keys safely
                stringMap.mapNotNull { (key, value) -> key.toIntOrNull()?.let { it to value } }.toMap()

            } catch (e: IOException) {
                e.printStackTrace()
                emptyMap()
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                emptyMap()
            }
        }

        fun predict(text: String): Map<String, String> {
            val words = text.lowercase().replace(".", "").split(Regex("\\s+|[,;]")).filter { it.isNotEmpty() }
            val tokens = words.mapNotNull { wordDict[it] }

            if (tokens.isEmpty()) {  //  Handle empty token list before running inference
                Log.e("NERModel", "No valid tokens found. Returning empty result.")
                return emptyMap()
            }


            val maxLength = 16
            val paddedTokens = if (tokens.size < maxLength) {
                tokens + List(maxLength - tokens.size) { 0 } // Padding
            } else {
                tokens.take(maxLength) // Truncate if too long
            }
            val inputArray = arrayOf(paddedTokens.map { it.toFloat() }.toFloatArray())

            val numClasses = labelDict.size
            val outputArray = Array(1) { Array(maxLength) { FloatArray(numClasses) } }

            try {
                interpreter?.run(inputArray, outputArray)
            } catch (e: Exception) {
                Log.e("NERModel", "TensorFlow Lite model inference failed", e)
                return emptyMap()
            }

            val entityMap = mutableMapOf<String, String>()
            var currentEntity = ""
            var lastLabel = ""

            for (i in 0 until words.size.coerceAtMost(maxLength)) {
                val classIndex = outputArray[0][i].indices.maxByOrNull { outputArray[0][i][it] } ?: 0
                val entityLabel = labelDict[classIndex] ?: "O"

                if (entityLabel.startsWith("B-")) {
                    lastLabel = entityLabel.substring(2)
                    currentEntity = words[i]
                } else if (entityLabel.startsWith("I-") && lastLabel == entityLabel.substring(2)) {
                    currentEntity += " " + words[i]
                } else if (currentEntity.isNotEmpty()) {
                    entityMap[lastLabel] = currentEntity
                    currentEntity = ""
                    lastLabel = ""
                }
            }
            return entityMap
        }

    }
}



