package com.example.mlmodelkit

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

data class TextRequest(val text: String)

// Updated data model to include hospital names
data class TextExtractionResponse(
    val doctor_names: List<String>,
    val hospital_names: List<String>
)

interface TextExtractionApiService {
    @Headers("Content-Type: application/json")
    @POST("/extract")
    fun extractTextDetails(@Body request: TextRequest): Call<TextExtractionResponse>
}
