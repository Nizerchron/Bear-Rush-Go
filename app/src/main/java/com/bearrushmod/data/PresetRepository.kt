package com.bearrushmod.data

import com.bearrushmod.model.Category
import com.bearrushmod.model.Preset
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class PresetRepository(supabaseUrl: String, supabaseKey: String) {
    private val restUrl = "$supabaseUrl/rest/v1/presets"
    private val apiKey = supabaseKey

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun getPresets(): List<Preset> {
        return client.get(restUrl) {
            header("apikey", apiKey)
            header("Authorization", "Bearer $apiKey")
        }.body()
    }

    companion object {
        val categories = listOf(
            Category(id = 1, name = "Nature"),
            Category(id = 2, name = "Structure")
        )
    }
}