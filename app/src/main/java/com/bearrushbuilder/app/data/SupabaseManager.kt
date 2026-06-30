package com.bearrushbuilder.app.data

import com.bearrushbuilder.app.BuildConfig
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
    @SerialName("refresh_token") val refreshToken: String? = null,
    val user: AuthUser? = null
)

@Serializable
data class UserProfile(
    val id: String,
    val username: String,
    val nickname: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    val bio: String = "",
    val coins: Int = 100,
    @SerialName("device_id") val deviceId: String? = null
)

@Serializable
data class UserCreateRequest(
    val id: String,
    val email: String,
    val username: String,
    val nickname: String,
    val coins: Int = 100,
    @SerialName("device_id") val deviceId: String? = null
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
data class UserRelation(
    val username: String
)

@Serializable
data class PresetComment(
    val id: Long? = null,
    @SerialName("preset_id") val presetId: Long,
    @SerialName("user_id") val userId: String? = null,
    val username: String = "Guest",
    val comment: String,
    @SerialName("parent_comment_id") val parentCommentId: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val users: UserRelation? = null
) {
    val displayUsername: String
        get() = users?.username ?: username
}

@Serializable
data class CommentPresetId(
    @SerialName("preset_id") val presetId: Long
)


class SupabaseManager(
    private val supabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val supabaseKey: String = BuildConfig.SUPABASE_KEY
) {
    private val client by lazy {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }

    suspend fun signUp(email: String, username: String, password: String): AuthResponse = withContext(Dispatchers.IO) {
        val clean = username.trim().lowercase().replace(" ", "").replace("@", "")
        val formattedUsername = "@$clean"
        val response = client.post("$supabaseUrl/auth/v1/signup") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseKey)
            setBody(AuthRequest(email, password, mapOf("username" to formattedUsername)))
        }
        if (!response.status.isSuccess()) {
            throw Exception("Sign up gagal: ${response.bodyAsText()}")
        }
        response.body()
    }

    suspend fun isDeviceBanned(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$supabaseUrl/rest/v1/banned_devices?device_id=eq.$deviceId") {
                header("apikey", supabaseKey)
            }
            if (response.status.isSuccess()) {
                val list: List<Map<String, String>> = response.body()
                return@withContext list.isNotEmpty()
            }
        } catch (_: Exception) {}
        return@withContext false
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

    suspend fun refreshToken(refreshToken: String): AuthResponse = withContext(Dispatchers.IO) {
        val response = client.post("$supabaseUrl/auth/v1/token?grant_type=refresh_token") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseKey)
            setBody(mapOf("refresh_token" to refreshToken))
        }
        if (!response.status.isSuccess()) {
            throw Exception("Refresh token gagal: ${response.bodyAsText()}")
        }
        response.body()
    }

    suspend fun getProfile(
        userId: String,
        token: String? = null,
        email: String = "",
        username: String = "",
        deviceId: String? = null
    ): UserProfile = withContext(Dispatchers.IO) {
        if (deviceId != null && isDeviceBanned(deviceId)) {
            throw Exception("Perangkat ini telah di-ban!")
        }
        if (isDeviceBanned(userId) || (email.isNotEmpty() && isDeviceBanned(email)) || (username.isNotEmpty() && isDeviceBanned(username))) {
            throw Exception("Akun ini telah di-ban!")
        }
        val response = client.get("$supabaseUrl/rest/v1/users?id=eq.$userId") {
            header("apikey", supabaseKey)
            if (!token.isNullOrEmpty()) {
                header("Authorization", "Bearer $token")
            }
        }
        if (!response.status.isSuccess()) {
            throw Exception("Gagal mengambil profil: ${response.bodyAsText()}")
        }
        val list: List<UserProfile> = response.body()
        val profile = if (list.isEmpty()) {
            if (email.isEmpty() || username.isEmpty()) {
                throw Exception("ACCOUNT_DELETED")
            }
            val rawFallback = if (username.isNotEmpty()) username else email.split("@").firstOrNull() ?: "user_${userId.take(5)}"
            val cleanFallback = rawFallback.trim().lowercase().replace(" ", "").replace("@", "")
            val fallbackUsername = "@$cleanFallback"
            val createResponse = client.post("$supabaseUrl/rest/v1/users") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                if (!token.isNullOrEmpty()) {
                    header("Authorization", "Bearer $token")
                }
                header("Prefer", "return=representation")
                setBody(UserCreateRequest(
                    id = userId,
                    email = email,
                    username = fallbackUsername,
                    nickname = cleanFallback,
                    coins = 100,
                    deviceId = deviceId
                ))
            }
            if (!createResponse.status.isSuccess()) {
                throw Exception("Profil tidak ditemukan dan gagal dibuat otomatis: ${createResponse.bodyAsText()}")
            }
            val createdList: List<UserProfile> = createResponse.body()
            if (createdList.isEmpty()) {
                throw Exception("Profil gagal dibuat otomatis")
            }
            createdList.first()
        } else {
            val existing = list.first()
            if (deviceId != null && existing.deviceId != deviceId) {
                try {
                    client.patch("$supabaseUrl/rest/v1/users?id=eq.$userId") {
                        contentType(ContentType.Application.Json)
                        header("apikey", supabaseKey)
                        if (!token.isNullOrEmpty()) {
                            header("Authorization", "Bearer $token")
                        }
                        setBody(mapOf("device_id" to deviceId))
                    }
                } catch (_: Exception) {}
                existing.copy(deviceId = deviceId)
            } else {
                existing
            }
        }

        // Record active user presence automatically
        recordPresence(profile.id, profile.username, deviceId, token)

        profile
    }

    suspend fun recordPresence(
        userId: String,
        nickname: String,
        deviceId: String?,
        token: String? = null
    ): Unit = withContext(Dispatchers.IO) {
        val finalDeviceId = deviceId ?: return@withContext
        try {
            client.post("$supabaseUrl/rest/v1/active_users_presence") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                if (!token.isNullOrEmpty()) {
                    header("Authorization", "Bearer $token")
                }
                header("Prefer", "resolution=merge-duplicates")
                setBody(mapOf(
                    "device_id" to finalDeviceId,
                    "user_id" to userId,
                    "nickname" to nickname,
                    "country" to "id",
                    "opened_at" to java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US).format(java.util.Date()),
                    "last_seen_at" to java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US).format(java.util.Date()),
                    "updated_at" to java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US).format(java.util.Date())
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun isUsernameTaken(username: String, currentUserId: String? = null): Boolean = withContext(Dispatchers.IO) {
        if (username.isBlank()) return@withContext false
        try {
            val response = client.get("$supabaseUrl/rest/v1/users?username=eq.${username.trim().lowercase()}") {
                header("apikey", supabaseKey)
            }
            if (response.status.isSuccess()) {
                val list: List<UserProfile> = response.body()
                return@withContext list.any { it.id != currentUserId }
            }
        } catch (_: Exception) {}
        return@withContext false
    }

    suspend fun updateProfile(
        userId: String,
        token: String,
        username: String,
        nickname: String,
        bio: String,
        avatarUrl: String
    ): UserProfile = withContext(Dispatchers.IO) {
        val response = client.patch("$supabaseUrl/rest/v1/users?id=eq.$userId") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseKey)
            header("Authorization", "Bearer $token")
            header("Prefer", "return=representation")
            setBody(mapOf(
                "username" to username,
                "nickname" to nickname,
                "bio" to bio,
                "avatar_url" to avatarUrl
            ))
        }
        if (!response.status.isSuccess()) {
            throw Exception("Gagal mengupdate profil: ${response.bodyAsText()}")
        }

        // Update username & nickname in previous comments posted by this user
        try {
            client.patch("$supabaseUrl/rest/v1/preset_comments?user_id=eq.$userId") {
                contentType(ContentType.Application.Json)
                header("apikey", supabaseKey)
                header("Authorization", "Bearer $token")
                setBody(mapOf(
                    "username" to username,
                    "nickname" to nickname
                ))
            }
        } catch (_: Exception) {
            // If nickname doesn't exist in preset_comments table, update just username
            try {
                client.patch("$supabaseUrl/rest/v1/preset_comments?user_id=eq.$userId") {
                    contentType(ContentType.Application.Json)
                    header("apikey", supabaseKey)
                    header("Authorization", "Bearer $token")
                    setBody(mapOf("username" to username))
                }
            } catch (_: Exception) {}
        }

        val list: List<UserProfile> = response.body()
        list.first()
    }

    suspend fun updateCoins(userId: String, token: String, newCoins: Int): UserProfile = withContext(Dispatchers.IO) {
        val response = client.patch("$supabaseUrl/rest/v1/users?id=eq.$userId") {
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
            header("Prefer", "resolution=ignore-duplicates")
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

    suspend fun getComments(presetId: Long, token: String? = null): List<PresetComment> = withContext(Dispatchers.IO) {
        val response = client.get("$supabaseUrl/rest/v1/preset_comments?preset_id=eq.$presetId&order=created_at.desc") {
            header("apikey", supabaseKey)
        }
        if (!response.status.isSuccess()) {
            return@withContext emptyList()
        }
        val rawComments: List<PresetComment> = response.body()
        if (rawComments.isEmpty()) {
            return@withContext emptyList()
        }

        val userIds = rawComments.mapNotNull { it.userId }.filter { it.isNotEmpty() }.distinct()
        if (userIds.isEmpty()) {
            return@withContext rawComments
        }

        try {
            val usersResponse = client.get("$supabaseUrl/rest/v1/users?id=in.(${userIds.joinToString(",")})") {
                header("apikey", supabaseKey)
                if (token != null) {
                    header("Authorization", "Bearer $token")
                }
            }
            if (usersResponse.status.isSuccess()) {
                val profiles: List<UserProfile> = usersResponse.body()
                val profilesMap = profiles.associateBy { it.id }
                return@withContext rawComments.map { comment ->
                    val matchedProfile = profilesMap[comment.userId]
                    if (matchedProfile != null) {
                        comment.copy(users = UserRelation(username = matchedProfile.username))
                    } else {
                        comment
                    }
                }
            }
        } catch (_: Exception) {}

        rawComments
    }

    suspend fun getAllCommentsCountMap(): Map<Long, Int> = withContext(Dispatchers.IO) {
        try {
            val response = client.get("$supabaseUrl/rest/v1/preset_comments?select=preset_id") {
                header("apikey", supabaseKey)
            }
            if (response.status.isSuccess()) {
                val list: List<CommentPresetId> = response.body()
                return@withContext list.groupBy { it.presetId }
                    .mapValues { it.value.size }
            }
        } catch (_: Exception) {}
        emptyMap()
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

    suspend fun incrementViews(presetId: Long, currentViews: Long) = withContext(Dispatchers.IO) {
        val response = client.patch("$supabaseUrl/rest/v1/presets?id=eq.$presetId") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseKey)
            setBody(mapOf("views" to currentViews + 1))
        }
        if (!response.status.isSuccess()) {
            throw Exception("Gagal mengupdate views: ${response.bodyAsText()}")
        }
    }

    suspend fun incrementLoves(presetId: Long, currentLoves: Long) = withContext(Dispatchers.IO) {
        val response = client.patch("$supabaseUrl/rest/v1/presets?id=eq.$presetId") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseKey)
            setBody(mapOf("loves" to currentLoves + 1))
        }
        if (!response.status.isSuccess()) {
            throw Exception("Gagal mengupdate loves: ${response.bodyAsText()}")
        }
    }

    suspend fun incrementDownloads(presetId: Long, currentDownloads: Long) = withContext(Dispatchers.IO) {
        val response = client.patch("$supabaseUrl/rest/v1/presets?id=eq.$presetId") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseKey)
            setBody(mapOf("downloads" to currentDownloads + 1))
        }
        if (!response.status.isSuccess()) {
            throw Exception("Gagal mengupdate downloads: ${response.bodyAsText()}")
        }
    }

    suspend fun followUser(followerId: String, followingId: String, token: String) = withContext(Dispatchers.IO) {
        val response = client.post("$supabaseUrl/rest/v1/follows") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseKey)
            header("Authorization", "Bearer $token")
            setBody(FollowRecord(followerId = followerId, followingId = followingId))
        }
        if (!response.status.isSuccess()) {
            throw Exception("Gagal mengikuti pengguna: ${response.bodyAsText()}")
        }
    }

    suspend fun unfollowUser(followerId: String, followingId: String, token: String) = withContext(Dispatchers.IO) {
        val response = client.delete("$supabaseUrl/rest/v1/follows?follower_id=eq.$followerId&following_id=eq.$followingId") {
            header("apikey", supabaseKey)
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) {
            throw Exception("Gagal berhenti mengikuti: ${response.bodyAsText()}")
        }
    }

    suspend fun isFollowing(followerId: String, followingId: String, token: String): Boolean = withContext(Dispatchers.IO) {
        val response = client.get("$supabaseUrl/rest/v1/follows?follower_id=eq.$followerId&following_id=eq.$followingId") {
            header("apikey", supabaseKey)
            header("Authorization", "Bearer $token")
        }
        if (!response.status.isSuccess()) {
            return@withContext false
        }
        val list: List<FollowRecord> = response.body()
        list.isNotEmpty()
    }

    suspend fun getFollowStats(userId: String, token: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val followersResp = client.get("$supabaseUrl/rest/v1/follows?following_id=eq.$userId") {
            header("apikey", supabaseKey)
            header("Authorization", "Bearer $token")
        }
        val followersList: List<FollowRecord> = if (followersResp.status.isSuccess()) followersResp.body() else emptyList()

        val followingResp = client.get("$supabaseUrl/rest/v1/follows?follower_id=eq.$userId") {
            header("apikey", supabaseKey)
            header("Authorization", "Bearer $token")
        }
        val followingList: List<FollowRecord> = if (followingResp.status.isSuccess()) followingResp.body() else emptyList()

        Pair(followersList.size, followingList.size)
    }

    suspend fun uploadStorageFile(
        bucket: String,
        path: String,
        bytes: ByteArray,
        mimeType: String,
        token: String? = null
    ): String = withContext(Dispatchers.IO) {
        val url = "$supabaseUrl/storage/v1/object/$bucket/$path"
        val response = client.post(url) {
            header("apikey", supabaseKey)
            if (!token.isNullOrEmpty()) {
                header("Authorization", "Bearer $token")
            }
            contentType(ContentType.parse(mimeType))
            setBody(bytes)
        }
        if (!response.status.isSuccess() && response.status.value != 400) {
            throw Exception("Gagal mengunggah berkas ke storage: ${response.bodyAsText()}")
        }
        "$supabaseUrl/storage/v1/object/public/$bucket/$path"
    }

    suspend fun insertPreset(
        preset: PresetInsertRequest,
        token: String? = null
    ): Unit = withContext(Dispatchers.IO) {
        val response = client.post("$supabaseUrl/rest/v1/presets") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseKey)
            if (!token.isNullOrEmpty()) {
                header("Authorization", "Bearer $token")
            }
            setBody(preset)
        }
        if (!response.status.isSuccess()) {
            throw Exception("Gagal menyimpan preset ke database: ${response.bodyAsText()}")
        }
    }

    suspend fun updatePreset(
        presetId: Long,
        preset: PresetInsertRequest,
        token: String? = null
    ): Unit = withContext(Dispatchers.IO) {
        val response = client.patch("$supabaseUrl/rest/v1/presets?id=eq.$presetId") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseKey)
            if (!token.isNullOrEmpty()) {
                header("Authorization", "Bearer $token")
            }
            setBody(preset)
        }
        if (!response.status.isSuccess()) {
            throw Exception("Gagal memperbarui preset di database: ${response.bodyAsText()}")
        }
    }


    suspend fun getFileSHA(
        path: String,
        gitHubToken: String
    ): String? = withContext(Dispatchers.IO) {
        val repoOwner = "Nizerchron"
        val repoName = "Bear-Rush-Go"
        val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$path"

        try {
            val response = client.get(url) {
                header("Accept", "application/vnd.github+json")
                header("Authorization", "Bearer $gitHubToken")
                header("X-GitHub-Api-Version", "2022-11-28")
            }
            if (response.status.value == 200) {
                @Serializable
                data class GitHubContentInfo(val sha: String)
                val info: GitHubContentInfo = response.body()
                return@withContext info.sha
            }
        } catch (_: Exception) {}
        null
    }

    suspend fun uploadToGitHub(
        path: String,
        bytes: ByteArray,
        gitHubToken: String
    ): String = withContext(Dispatchers.IO) {
        val repoOwner = "Nizerchron"
        val repoName = "Bear-Rush-Go"
        val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$path"

        val sha = getFileSHA(path, gitHubToken)

        val base64Content = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

        val requestBody = GitHubPutRequest(
            message = "Upload/Replace $path via Bear Rush Builder",
            content = base64Content,
            sha = sha
        )

        val response = client.put(url) {
            header("Accept", "application/vnd.github+json")
            header("Authorization", "Bearer $gitHubToken")
            header("X-GitHub-Api-Version", "2022-11-28")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        if (!response.status.isSuccess()) {
            throw Exception("Gagal mengunggah ke GitHub ($path): ${response.bodyAsText()}")
        }

        "https://raw.githubusercontent.com/$repoOwner/$repoName/main/$path"
    }

    suspend fun deleteFromGitHub(
        path: String,
        gitHubToken: String
    ): Unit = withContext(Dispatchers.IO) {
        val sha = getFileSHA(path, gitHubToken) ?: return@withContext

        val repoOwner = "Nizerchron"
        val repoName = "Bear-Rush-Go"
        val url = "https://api.github.com/repos/$repoOwner/$repoName/contents/$path"

        val requestBody = GitHubDeleteRequest(
            message = "Delete $path via Bear Rush Builder",
            sha = sha
        )

        val response = client.delete(url) {
            header("Accept", "application/vnd.github+json")
            header("Authorization", "Bearer $gitHubToken")
            header("X-GitHub-Api-Version", "2022-11-28")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        if (!response.status.isSuccess()) {
            throw Exception("Gagal menghapus berkas di GitHub ($path): ${response.bodyAsText()}")
        }
    }

    suspend fun deletePreset(
        presetId: Long,
        token: String? = null
    ): Unit = withContext(Dispatchers.IO) {
        val response = client.delete("$supabaseUrl/rest/v1/presets?id=eq.$presetId") {
            header("apikey", supabaseKey)
            if (!token.isNullOrEmpty()) {
                header("Authorization", "Bearer $token")
            }
        }
        if (!response.status.isSuccess()) {
            throw Exception("Gagal menghapus preset di database: ${response.bodyAsText()}")
        }
    }

    suspend fun getUserDevices(userId: String): List<UserDevice> = withContext(Dispatchers.IO) {
        val response = client.get("$supabaseUrl/rest/v1/user_devices?user_id=eq.$userId") {
            header("apikey", supabaseKey)
        }
        if (response.status.isSuccess()) {
            response.body()
        } else {
            emptyList()
        }
    }

    suspend fun upsertDevice(userId: String, deviceId: String, token: String? = null): Boolean = withContext(Dispatchers.IO) {
        val now = java.time.Instant.now().toString()
        val response = client.post("$supabaseUrl/rest/v1/user_devices") {
            header("apikey", supabaseKey)
            header("Prefer", "resolution=merge-duplicates")
            if (!token.isNullOrEmpty()) header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UserDevice(userId = userId, deviceId = deviceId, lastLogin = now))
        }
        response.status.isSuccess()
    }

    suspend fun deleteDevice(userId: String, deviceId: String, token: String? = null): Boolean = withContext(Dispatchers.IO) {
        val response = client.delete("$supabaseUrl/rest/v1/user_devices?user_id=eq.$userId&device_id=eq.$deviceId") {
            header("apikey", supabaseKey)
            if (!token.isNullOrEmpty()) header("Authorization", "Bearer $token")
        }
        response.status.isSuccess()
    }

    suspend fun sendOtpEmail(email: String): Boolean = withContext(Dispatchers.IO) {
        val response = client.post("$supabaseUrl/auth/v1/otp") {
            header("apikey", supabaseKey)
            contentType(ContentType.Application.Json)
            setBody(kotlinx.serialization.json.buildJsonObject {
                put("email", kotlinx.serialization.json.JsonPrimitive(email))
                put("create_user", kotlinx.serialization.json.JsonPrimitive(false))
            })
        }
        response.status.isSuccess()
    }

    suspend fun verifyOtpCode(email: String, token: String): AuthResponse = withContext(Dispatchers.IO) {
        val response = client.post("$supabaseUrl/auth/v1/verify") {
            header("apikey", supabaseKey)
            contentType(ContentType.Application.Json)
            setBody(kotlinx.serialization.json.buildJsonObject {
                put("email", kotlinx.serialization.json.JsonPrimitive(email))
                put("token", kotlinx.serialization.json.JsonPrimitive(token))
                put("type", kotlinx.serialization.json.JsonPrimitive("magiclink"))
            })
        }
        if (response.status.isSuccess()) {
            response.body()
        } else {
            throw Exception("Kode verifikasi salah atau sudah kadaluarsa.")
        }
    }

    companion object {
        val GITHUB_TOKEN = BuildConfig.GITHUB_TOKEN
    }
}

@Serializable
data class FollowRecord(
    val id: Long? = null,
    @SerialName("follower_id") val followerId: String,
    @SerialName("following_id") val followingId: String
)

@Serializable
data class PresetInsertRequest(
    val name: String,
    val description: String,
    val category: String,
    @SerialName("preview_url") val previewUrl: String,
    @SerialName("download_url") val downloadUrl: String,
    @SerialName("is_free") val isFree: Boolean,
    val price: Long,
    val author: String,
    @SerialName("youtube_url") val youtubeUrl: String = "",
    val downloads: Long = 0,
    val loves: Long = 0,
    val views: Long = 0
)

@Serializable
data class GitHubPutRequest(
    val message: String,
    val content: String,
    val sha: String? = null,
    val branch: String = "main"
)

@Serializable
data class GitHubDeleteRequest(
    val message: String,
    val sha: String,
    val branch: String = "main"
)

@Serializable
data class UserDevice(
    val id: Long? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("last_login") val lastLogin: String? = null
)

