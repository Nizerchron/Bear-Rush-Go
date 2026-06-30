package com.bearrushbuilder.app.model

import kotlinx.serialization.Serializable

@Serializable
data class Preset(
    val id: Long,
    val name: String,
    val description: String = "",
    val category: String = "",
    val preview_url: String = "",
    val download_url: String = "",
    val is_free: Boolean = true,
    val price: Long = 0,
    val downloads: Long = 0,
    val loves: Long = 0,
    val views: Long = 0,
    val author: String = "",
    val youtube_url: String = ""
)