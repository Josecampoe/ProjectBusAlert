package com.busalert.shared.data.api.gemini

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

object GeminiConfig {
    const val API_KEY: String = "YOUR_GEMINI_API_KEY_HERE"
    const val BASE_URL: String = "https://generativelanguage.googleapis.com/"
    const val PRIMARY_MODEL: String = "gemini-3-flash-preview"
    const val FALLBACK_MODEL: String = "gemini-flash-latest"
}

data class GeminiPart(
    @SerializedName("text") val text: String
)

data class GeminiContent(
    @SerializedName("parts") val parts: List<GeminiPart>
)

data class GeminiRequest(
    @SerializedName("contents") val contents: List<GeminiContent>
)

data class GeminiResponse(
    @SerializedName("candidates") val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    @SerializedName("content") val content: GeminiContentResponse?
)

data class GeminiContentResponse(
    @SerializedName("parts") val parts: List<GeminiPart>?
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}
