package com.bearrushmod.data

import com.bearrushmod.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AuthRequest(
    val email: String,
    val password: String,
    val data: Map<String, String>? = null
)

@Serializable
data class OidcAuthRequest(
    val provider: String,
    @SerialName("id_token") val idToken: String,
    val nonce: String? = null
)

@Serializable
data class AuthUser(
    val id: String,
    val email: String? = null
)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String? = null,
    val user: AuthUser? = null
)

@Serializable
data class UserProfile(
    val id: String,
    val username: String,
    @SerialName("avatar_url") val avatarUrl: String = "",
    val bio: String = "",
    val coins: Int = 100
)

@Serializable
data class UserPresetLog(
    val id: Long? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("preset_id") val presetId: Long,
    @SerialName("preset_name") val presetName: String,
    @SerialName("preset_preview_url") val presetPreviewUrl: String,
    @SerialName("preset_category") val presetCategory: String,
    @SerialName("acquired_at") val acquiredAt: String? = null
)

@Serializable
data class PresetComment(
    val id: Long? = null,
    @SerialName("preset_id") val presetId: Long,
    @SerialName("user_id") val userId: String? = null,
    val username: String = "Guest",
    val comment: String,
    @SerialName("created_at") val createdAt: String? = null
)

class SupabaseManager(
    private val supabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val supabaseKey: String = BuildConfig.SUPABASE_KEY
) {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun signUp(email: String, username: String, password: String): AuthResponse = withContext(Dispatchers.IO) {
        val response = client.post("$supabaseUrl/auth/v1/signup") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseKey)
            setBody(AuthRequest(email, password, mapOf("username" to username)))
        }
        if (!response.status.isSuccess()) {
            throw Exception("Sign up gagal: ${response.bodyAsText()}")
        }
        response.body()
    }

    suspend fun signIn(email: String, password: String): AuthResponse = withContext(Dispatchers.IO) {
        val response = client.post("$supabaseUrl/auth/v1/token?grant_type=password") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseKey)
            setBody(AuthRequest(email, password))
        }
        if (!response.status.isSuccess()) {
            throw Exception("Sign in gagal: ${response.bodyAsText()}")
        }
        response.body()
    }

    suspend fun signInWithGoogle(idToken: String): AuthResponse = withContext(Dispatchers.IO) {
        val response = client.post("$supabaseUrl/auth/v1/token?grant_type=id_token") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseKey)
            setBody(OidcAuthRequest(provider = "google", idToken = idToken))
        }
        if (!response.status.isSuccess()) {
            throw Exception("Google Sign In gagal: ${response.bodyAsText()}")
        }
        response.body()
    }

    suspend fun getProfile(userId: String, token: String): UserProfile = withContext(Dispatchers.IO) {
        val response = client.get("$supabaseUrl/rest/v1/profiles?id=eq.$userId") {
            header("apikey", supabaseKey)
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) {
            throw Exception("Gagal mengambil profil: ${response.bodyAsText()}")
        }
        val list: List<UserProfile> = response.body()
        if (list.isEmpty()) throw Exception("Profil tidak ditemukan")
        list.first()
    }

    suspend fun updateProfile(userId: String, token: String, username: String, bio: String, avatarUrl: String): UserProfile = withContext(Dispatchers.IO) {
        val response = client.patch("$supabaseUrl/rest/v1/profiles?id=eq.$userId") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseKey)
            header("Authorization", "Bearer $token")
            header("Prefer", "return=representation")
            setBody(mapOf("username" to username, "bio" to bio, "avatar_url" to avatarUrl))
        }
        if (!response.status.isSuccess()) {
            throw Exception("Gagal mengupdate profil: ${response.bodyAsText()}")
        }
        val list: List<UserProfile> = response.body()
        list.first()
    }

    suspend fun updateCoins(userId: String, token: String, newCoins: Int): UserProfile = withContext(Dispatchers.IO) {
        val response = client.patch("$supabaseUrl/rest/v1/profiles?id=eq.$userId") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseKey)
            header("Authorization", "Bearer $token")
            header("Prefer", "return=representation")
            setBody(mapOf("coins" to newCoins))
        }
        if (!response.status.isSuccess()) {
            throw Exception("Gagal mengupdate koin: ${response.bodyAsText()}")
        }
        val list: List<UserProfile> = response.body()
        list.first()
    }

    suspend fun logDownload(token: String, log: UserPresetLog) = withContext(Dispatchers.IO) {
        val response = client.post("$supabaseUrl/rest/v1/user_presets") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseKey)
            header("Authorization", "Bearer $token")
            setBody(log)
        }
        if (!response.status.isSuccess()) {
            throw Exception("Gagal mencatat preset: ${response.bodyAsText()}")
        }
    }

    suspend fun getOwnedPresets(userId: String, token: String): List<UserPresetLog> = withContext(Dispatchers.IO) {
        val response = client.get("$supabaseUrl/rest/v1/user_presets?user_id=eq.$userId") {
            header("apikey", supabaseKey)
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) {
            return@withContext emptyList()
        }
        response.body()
    }

    suspend fun getComments(presetId: Long): List<PresetComment> = withContext(Dispatchers.IO) {
        val response = client.get("$supabaseUrl/rest/v1/preset_comments?preset_id=eq.$presetId&order=created_at.desc") {
            header("apikey", supabaseKey)
        }
        if (!response.status.isSuccess()) {
            return@withContext emptyList()
        }
        response.body()
    }

    suspend fun postComment(comment: PresetComment, token: String? = null): PresetComment = withContext(Dispatchers.IO) {
        val response = client.post("$supabaseUrl/rest/v1/preset_comments") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseKey)
            if (token != null) {
                header("Authorization", "Bearer $token")
            }
            header("Prefer", "return=representation")
            setBody(comment)
        }
        if (!response.status.isSuccess()) {
            throw Exception("Gagal mengirim komentar: ${response.bodyAsText()}")
        }
        val list: List<PresetComment> = response.body()
        list.first()
    }
}
