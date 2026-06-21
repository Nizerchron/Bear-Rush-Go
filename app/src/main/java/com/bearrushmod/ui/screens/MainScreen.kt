package com.bearrushmod.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.content.Intent
import android.net.Uri
import coil.compose.AsyncImage
import java.io.File
import android.os.Environment
import com.bearrushmod.R
import com.bearrushmod.data.AdsManager
import com.bearrushmod.data.DownloadManager
import com.bearrushmod.model.Category
import com.bearrushmod.model.Preset
import com.bearrushmod.ui.components.CategoryChip
import com.bearrushmod.ui.components.PresetCard
import com.bearrushmod.ui.components.scaleOnPress
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    presets: List<Preset>,
    categories: List<Category>,
    selectedCategory: Category?,
    onCategorySelected: (Category?) -> Unit,
    darkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    downloadManager: DownloadManager? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var detailPreset by remember { mutableStateOf<Preset?>(null) }
    var confirmDownloadPreset by remember { mutableStateOf<Preset?>(null) }
    val scope = rememberCoroutineScope()
    val downloadStates = remember { mutableStateMapOf<Long, Float>() }

    val filteredPresets = remember(presets, selectedCategory, searchQuery) {
        presets
            .filter { selectedCategory == null || it.category == selectedCategory.name }
            .filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
            val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

            SmallFloatingActionButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onThemeToggle()
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                interactionSource = interactionSource,
                modifier = Modifier.scaleOnPress(
                    interactionSource = interactionSource,
                    pressedScale = 0.9f
                )
            ) {
                Icon(
                    imageVector = if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = if (darkTheme) stringResource(R.string.light_mode) else stringResource(R.string.dark_mode)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            WelcomeHeader()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .statusBarsPadding()
            ) {
                DownloadSbaButton(downloadManager = downloadManager)

                Spacer(modifier = Modifier.height(12.dp))
                SearchBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it }
                )

                Spacer(modifier = Modifier.height(20.dp))
                CategoriesSection(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = onCategorySelected
                )

                Spacer(modifier = Modifier.height(24.dp))
                PopularPresetsHeader(
                    resultCount = filteredPresets.size
                )

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = paddingValues.calculateBottomPadding() + 16.dp
                        )
                    ) {
                        items(filteredPresets.chunked(2)) { rowItems ->
                            PresetsRow(
                                presets = rowItems,
                                downloadStates = downloadStates,
                                downloadManager = downloadManager,
                                onCardClick = { preset -> detailPreset = preset },
                                onDownloadClick = { preset ->
                                    if (downloadManager != null && downloadStates[preset.id] == null) {
                                        // Step 1: Tampilkan interstitial (bisa skip)
                                        AdsManager.showInterstitial(context as Activity, 
                                            onCompleted = {
                                                // Iklan pertama selesai — langsung lanjut ke confirm
                                                confirmDownloadPreset = preset
                                            },
                                            onSkipped = {
                                                // Iklan di-skip — tetap lanjut ke confirm
                                                confirmDownloadPreset = preset
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Confirm download dialog ──
    confirmDownloadPreset?.let { preset ->
        AlertDialog(
            onDismissRequest = { confirmDownloadPreset = null },
            confirmButton = {
                Button(
                    onClick = {
                        val p = preset
                        confirmDownloadPreset = null
                        // Step 2: Interstitial WAJIB — hanya lanjut kalau ditonton sampai selesai
                        AdsManager.showInterstitial(context as Activity,
                            onCompleted = {
                                // Iklan selesai — mulai download
                                if (downloadManager != null && downloadStates[p.id] == null) {
                                    downloadStates[p.id] = 0f
                                    scope.launch {
                                        downloadManager.download(
                                            url = p.download_url,
                                            fileName = "${p.name}.bin",
                                            onProgress = { progress ->
                                                if (progress.error != null) {
                                                    downloadStates[p.id] = Float.POSITIVE_INFINITY
                                                } else if (progress.isCompleted) {
                                                    downloadStates[p.id] = 2f
                                                } else if (progress.totalBytes > 0) {
                                                    downloadStates[p.id] =
                                                        progress.bytesDownloaded.toFloat() / progress.totalBytes.toFloat()
                                                }
                                            }
                                        )
                                    }
                                }
                            },
                            onSkipped = {
                                // Iklan ditutup sebelum selesai — download GAGAL
                                downloadStates[p.id] = Float.POSITIVE_INFINITY
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                ) {
                    Text("Ya, Download!", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDownloadPreset = null }) {
                    Text("Batal")
                }
            },
            title = {
                Text("Konfirmasi Download", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "Preset \"${preset.name}\" siap diunduh!",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Setelah konfirmasi, tonton iklan sebentar ya 😊\n\n" +
                               "Jika iklan selesai, file akan langsung tersimpan di folder:\n" +
                               "📁 Geokar_Mods/SBA/saved_scenes/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    detailPreset?.let { preset ->
        AlertDialog(
            onDismissRequest = { detailPreset = null },
            confirmButton = {
                TextButton(onClick = { detailPreset = null }) { Text("Tutup") }
            },
            title = { Text(preset.name, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    AsyncImage(
                        model = preset.preview_url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = preset.description.ifEmpty { "Tidak ada deskripsi." },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Kategori: ${preset.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Composable
fun WelcomeHeader() {
    Image(
        painter = painterResource(id = R.drawable.bg_welcome_header),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth().aspectRatio(1440f / 667f)
    )
}

@Composable
fun DownloadSbaButton(downloadManager: DownloadManager?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // ponytail: cek apakah APK sudah ada di Downloads
    val apkName = "SBA_12.x.x_(Mod_by_geokar)_clone.apk"
    val apkFile = remember {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), apkName)
    }
    val isDownloaded = remember { mutableStateOf(apkFile.exists()) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isDownloaded.value) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sub4unlock.co/gvSOKKmE"))
                    context.startActivity(intent)
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "geokar")
            val bounce by infiniteTransition.animateFloat(
                initialValue = 1f, targetValue = 1.12f,
                animationSpec = infiniteRepeatable(animation = tween(600, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                label = "bounce"
            )
            Image(
                painter = painterResource(id = R.drawable.geokar),
                contentDescription = null,
                modifier = Modifier.size(48.dp).scale(bounce).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.download_sba), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = if (isDownloaded.value) "✓ Ada di folder Downloads" else "Super Bear Adventure Geokar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sub4unlock.co/gvSOKKmE"))
                    context.startActivity(intent)
                },
                enabled = !isDownloaded.value,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isDownloaded.value) "✓" else "APK", fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun SearchBar(searchQuery: String, onSearchQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = searchQuery, onValueChange = onSearchQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = true, shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    )
}

@Composable
fun CategoriesSection(categories: List<Category>, selectedCategory: Category?, onCategorySelected: (Category?) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.categories), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { CategoryChip(name = "All", isSelected = selectedCategory == null, onClick = { onCategorySelected(null) }) }
            items(categories) { category -> CategoryChip(name = category.name, isSelected = selectedCategory?.name == category.name, onClick = { onCategorySelected(category) }) }
        }
    }
}

@Composable
fun PopularPresetsHeader(resultCount: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.popular_presets), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("$resultCount items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PresetsRow(presets: List<Preset>, downloadStates: MutableMap<Long, Float>, downloadManager: DownloadManager?, onCardClick: (Preset) -> Unit = {}, onDownloadClick: (Preset) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        presets.forEach { preset ->
            PresetCard(
                preset = preset, modifier = Modifier.weight(1f),
                isDownloaded = downloadManager?.isDownloaded("${preset.name}.bin") == true,
                downloadProgress = downloadStates[preset.id],
                onCardClick = { onCardClick(preset) },
                onDownloadClick = { onDownloadClick(preset) }
            )
        }
        if (presets.size < 2) Spacer(modifier = Modifier.weight(1f))
    }
}