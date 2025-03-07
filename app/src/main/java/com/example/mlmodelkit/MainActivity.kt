package com.example.mlmodelkit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var cameraImage: ImageView
    private lateinit var captureImgBtn: Button
    private lateinit var resultText: TextView
    private lateinit var doctorName: TextView
    private lateinit var hospitalName: TextView
    private lateinit var button: Button
    private lateinit var copyTextBtn: Button


    private var currentPhotoPath: String? = null
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        cameraImage = findViewById(R.id.cameraImage)
        captureImgBtn = findViewById(R.id.captureImgBtn)
        resultText = findViewById(R.id.resultText)
        copyTextBtn = findViewById(R.id.copyTxtBtn)
        button=findViewById(R.id.buttonExtract)
        doctorName=findViewById(R.id.doctorNameText)
        hospitalName=findViewById(R.id.hospitalNameText)


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
        button.setOnClickListener {
            sendTextToModel(resultText.text.toString())
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

    private fun sendTextToModel(text: String) {
        if (text.isEmpty()) {
            Toast.makeText(this, "No extracted text available", Toast.LENGTH_SHORT).show()
            return
        }
        val request = TextRequest(text)
        RetrofitClient.instance.extractTextDetails(request)
            .enqueue(object : Callback<TextExtractionResponse> {
                override fun onResponse(
                    call: Call<TextExtractionResponse>,
                    response: Response<TextExtractionResponse>
                ) {
                    if (response.isSuccessful) {
                        val doctorNames = response.body()?.doctor_names?.joinToString(", ") ?: "Not Found"
                        val hospitalNames = response.body()?.hospital_names?.joinToString(", ") ?: "Not Found"
                        doctorName.text = "Doctor: $doctorNames"
                        hospitalName.text = "Hospital: $hospitalNames"
                    } else {
                        doctorName.text = "Error: ${response.message()}"
                        hospitalName.text = "Error: ${response.message()}"
                    }
                }


                override fun onFailure(call: Call<TextExtractionResponse>, t: Throwable) {
                    doctorName.text = "Failed: ${t.message}"
                    hospitalName.text = "Failed: ${t.message}"
                }
            })
    }
}



