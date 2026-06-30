package com.bearrushbuilder.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bearrushbuilder.app.R
import com.bearrushbuilder.app.BillingManager
import com.android.billingclient.api.ProductDetails
import com.bearrushbuilder.app.data.*
import com.bearrushbuilder.app.model.Category
import com.bearrushbuilder.app.model.Preset
import com.bearrushbuilder.app.ui.components.CategoryChip
import com.bearrushbuilder.app.ui.components.PresetCard
import com.bearrushbuilder.app.ui.components.scaleOnPress
import com.bearrushbuilder.app.ui.components.findActivity
import com.bearrushbuilder.app.ui.theme.Accent
import com.bearrushbuilder.app.ui.theme.ColorButtonGreen
import com.bearrushbuilder.app.ui.theme.ColorCoinBadge
import com.bearrushbuilder.app.ui.theme.ColorLove
import com.bearrushbuilder.app.ui.theme.ColorDevMode
import com.bearrushbuilder.app.ui.theme.ColorDevModeDark
import com.bearrushbuilder.app.ui.theme.ColorDevCard
import com.bearrushbuilder.app.ui.theme.ColorDevCardEnd
import com.bearrushbuilder.app.ui.theme.ColorPink
import com.bearrushbuilder.app.ui.theme.ColorSuccess
import com.bearrushbuilder.app.ui.theme.ColorTextMuted
import com.bearrushbuilder.app.ui.theme.OnBackgroundLight
import com.bearrushbuilder.app.ui.theme.Primary
import com.bearrushbuilder.app.ui.theme.PrimaryDark
import com.bearrushbuilder.app.ui.theme.SurfaceLight
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

enum class ScreenTab {
    CATALOG,
    SHOP,
    PROFILE
}

data class CoinPackage(val coins: Int, val price: String, val desc: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    presets: List<Preset>,
    categories: List<Category>,
    selectedCategory: Category?,
    onCategorySelected: (Category?) -> Unit,
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    downloadManager: DownloadManager? = null,
    dataStoreManager: DataStoreManager,
    supabaseManager: SupabaseManager,
    billingManager: BillingManager?,
    isSystemNavVisible: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(ScreenTab.CATALOG) }
    var showCreatorUploadScreen by remember { mutableStateOf(false) }

    // Supabase Auth and User States
    val session by dataStoreManager.userSession.collectAsState(initial = null)
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var ownedPresets by remember { mutableStateOf<List<UserPresetLog>>(emptyList()) }
    var isLoadingProfile by remember { mutableStateOf(false) }

    // Billing Manager Purchase success listener
    LaunchedEffect(billingManager) {
        billingManager?.onPurchaseSuccess = { productId ->
            val coinsToAdd = when (productId) {
                "starter_pack" -> 500
                "popular_pack" -> 1300
                "got_pack" -> 2000
                else -> 0
            }
            if (coinsToAdd > 0) {
                scope.launch {
                    try {
                        if (session != null) {
                            runWithTokenRefresh(session, supabaseManager, dataStoreManager) { token ->
                                val newCoins = (profile?.coins ?: 0) + coinsToAdd
                                val updatedProfile = supabaseManager.updateCoins(session!!.userId, token, newCoins)
                                profile = updatedProfile
                                dataStoreManager.updateCoins(newCoins)
                                Toast.makeText(context, "Pembelian berhasil! $coinsToAdd koin telah ditambahkan.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Gagal sinkronisasi koin: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else if (productId == "developer_mode") {
                Toast.makeText(context, "Developer Mode berhasil diaktifkan!", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Download flow states
    val downloadStates = remember { mutableStateMapOf<Long, Float>() }
    var searchQuery by remember { mutableStateOf("") }
    var detailPreset by remember { mutableStateOf<Preset?>(null) }
    var confirmDownloadPreset by remember { mutableStateOf<Preset?>(null) }
    var pendingDownloadPreset by remember { mutableStateOf<Preset?>(null) }

    var detailViews by remember { mutableStateOf(0L) }
    var detailLoves by remember { mutableStateOf(0L) }
    var detailDownloads by remember { mutableStateOf(0L) }

    LaunchedEffect(detailPreset) {
        detailPreset?.let {
            detailViews = it.views
            detailLoves = it.loves
            detailDownloads = it.downloads
        }
    }

    var commentsCountMap by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var showSettingsScreen by remember { mutableStateOf(false) }

    LaunchedEffect(presets) {
        commentsCountMap = supabaseManager.getAllCommentsCountMap()
    }

    // Sync profile and downloaded presets when user session changes
    LaunchedEffect(session) {
        val currentSession = session
        if (currentSession != null) {
            isLoadingProfile = true
            try {
                runWithTokenRefresh(currentSession, supabaseManager, dataStoreManager) { token ->
                    val dId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                    profile = supabaseManager.getProfile(currentSession.userId, token, deviceId = dId)
                    ownedPresets = supabaseManager.getOwnedPresets(currentSession.userId, token)
                }
            } catch (e: Throwable) {
                if (e.message?.contains("ACCOUNT_DELETED") == true) {
                    Toast.makeText(context, "Akun Anda telah dihapus oleh Admin.", Toast.LENGTH_LONG).show()
                    scope.launch {
                        dataStoreManager.clearUserSession()
                    }
                }
                android.util.Log.e("MainScreen", "Session profile sync failed: ${e.message}", e)
            } finally {
                isLoadingProfile = false
            }
        } else {
            profile = null
            ownedPresets = emptyList()
        }
    }

    val refreshProfileData: () -> Unit = {
        scope.launch {
            if (session != null) {
                try {
                    runWithTokenRefresh(session, supabaseManager, dataStoreManager) { token ->
                        val dId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                        val freshProfile = supabaseManager.getProfile(session!!.userId, token, deviceId = dId)
                        profile = freshProfile
                        dataStoreManager.updateCoins(freshProfile.coins)
                        ownedPresets = supabaseManager.getOwnedPresets(session!!.userId, token)
                    }
                } catch (e: Throwable) {
                    if (e.message?.contains("ACCOUNT_DELETED") == true) {
                        Toast.makeText(context, "Akun Anda telah dihapus oleh Admin.", Toast.LENGTH_LONG).show()
                        scope.launch {
                            dataStoreManager.clearUserSession()
                        }
                    } else {
                        Toast.makeText(context, "Sinkronisasi gagal: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val tryDirectDownload = remember(session, profile, ownedPresets, detailPreset) {
        { p: Preset ->
            val baseDir = java.io.File(
                android.os.Environment.getExternalStorageDirectory(),
                "Geokar_Mods/SBA/saved_scenes"
            )
            var canWriteDirectly = false
            try {
                if (baseDir.exists() || baseDir.mkdirs()) {
                    val testFile = java.io.File(baseDir, ".test_write")
                    if (testFile.createNewFile()) {
                        testFile.delete()
                        canWriteDirectly = true
                    }
                }
            } catch (_: Exception) {}

            if (canWriteDirectly && downloadManager != null) {
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
                                scope.launch {
                                    try {
                                        supabaseManager.incrementDownloads(p.id, p.downloads)
                                        if (detailPreset?.id == p.id) {
                                            detailDownloads += 1
                                        }
                                        if (session != null) {
                                            val isAlreadyOwned = ownedPresets.any { it.presetId == p.id }
                                            runWithTokenRefresh(session, supabaseManager, dataStoreManager) { token ->
                                                if (!isAlreadyOwned) {
                                                    supabaseManager.logDownload(
                                                        token = token,
                                                        log = UserPresetLog(
                                                            userId = session!!.userId,
                                                            presetId = p.id,
                                                            presetName = p.name,
                                                            presetPreviewUrl = p.preview_url,
                                                            presetCategory = p.category
                                                        )
                                                    )
                                                    val coinCost = p.price.toInt()
                                                    if (coinCost > 0) {
                                                        val newCoins = (profile?.coins ?: coinCost) - coinCost
                                                        supabaseManager.updateCoins(
                                                            userId = session!!.userId,
                                                            token = token,
                                                            newCoins = newCoins
                                                        )
                                                        dataStoreManager.updateCoins(newCoins)
                                                    }
                                                }
                                                refreshProfileData()
                                            }
                                        }
                                        Toast.makeText(context, "Preset \"${p.name}\" berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Gagal sinkronisasi data: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                if (progress.totalBytes > 0) {
                                    downloadStates[p.id] = progress.bytesDownloaded.toFloat() / progress.totalBytes.toFloat()
                                } else {
                                    downloadStates[p.id] = 0f
                                }
                            }
                        }
                    )
                }
                true
            } else {
                false
            }
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val p = pendingDownloadPreset
            val isAlreadyOwned = p != null && ownedPresets.any { it.presetId == p.id }
            if (p != null) {
                if (downloadManager != null && downloadStates[p.id] == null) {
                    downloadStates[p.id] = 0f
                    scope.launch {
                        downloadManager.downloadToUri(
                            url = p.download_url,
                            context = context,
                            uri = uri,
                            onProgress = { progress ->
                                if (progress.error != null) {
                                    downloadStates[p.id] = Float.POSITIVE_INFINITY
                                } else if (progress.isCompleted) {
                                    downloadStates[p.id] = 2f
                                    scope.launch {
                                        try {
                                            supabaseManager.incrementDownloads(p.id, p.downloads)
                                            if (detailPreset?.id == p.id) {
                                                detailDownloads += 1
                                            }
                                            if (session != null) {
                                                runWithTokenRefresh(session, supabaseManager, dataStoreManager) { token ->
                                                    if (!isAlreadyOwned) {
                                                        supabaseManager.logDownload(
                                                            token = token,
                                                            log = UserPresetLog(
                                                                userId = session!!.userId,
                                                                presetId = p.id,
                                                                presetName = p.name,
                                                                presetPreviewUrl = p.preview_url,
                                                                presetCategory = p.category
                                                            )
                                                        )
                                                        val coinCost = p.price.toInt()
                                                        if (coinCost > 0) {
                                                            val newCoins = (profile?.coins ?: coinCost) - coinCost
                                                            supabaseManager.updateCoins(
                                                                userId = session!!.userId,
                                                                token = token,
                                                                newCoins = newCoins
                                                            )
                                                            dataStoreManager.updateCoins(newCoins)
                                                        }
                                                    }
                                                }
                                                refreshProfileData()
                                            }
                                            Toast.makeText(context, "Preset \"${p.name}\" berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Gagal sinkronisasi data: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else if (progress.totalBytes > 0) {
                                    downloadStates[p.id] = progress.bytesDownloaded.toFloat() / progress.totalBytes.toFloat()
                                }
                            }
                        )
                    }
                }
            }
        }
        pendingDownloadPreset = null
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val p = pendingDownloadPreset
        if (p != null) {
            val started = tryDirectDownload(p)
            if (!started) {
                createDocumentLauncher.launch("${p.name}.bin")
            }
        }
    }

    val filteredPresets = remember(presets, selectedCategory, searchQuery) {
        presets
            .filter { selectedCategory == null || it.category == selectedCategory.name }
            .filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val handleDownloadClick = { preset: Preset ->
        if (downloadManager != null && downloadStates[preset.id] == null) {
            val isOwned = ownedPresets.any { it.presetId == preset.id }
            val coinCost = if (isOwned) 0 else preset.price.toInt()
            var canProceed = true
            
            val currentSession = session
            if (currentSession == null) {
                detailPreset = null
                currentTab = ScreenTab.PROFILE
                canProceed = false
            } else {
                val userCoins = profile?.coins ?: 0
                if (userCoins < coinCost) {
                    Toast.makeText(context, "Koin tidak cukup (butuh $coinCost koin). Silakan isi ulang di Toko Koin!", Toast.LENGTH_LONG).show()
                    currentTab = ScreenTab.SHOP
                    canProceed = false
                }
            }
            if (canProceed) {
                confirmDownloadPreset = preset
            }
        }
    }

    if (detailPreset != null) {
        BackHandler(onBack = { detailPreset = null })
        PresetDetailScreen(
            preset = detailPreset!!,
            session = session,
            profile = profile,
            supabaseManager = supabaseManager,
            dataStoreManager = dataStoreManager,
            downloadStates = downloadStates,
            downloadManager = downloadManager,
            ownedPresets = ownedPresets,
            commentsCountMap = commentsCountMap,
            onBack = { detailPreset = null },
            onRefreshProfile = refreshProfileData,
            onLoveIncrement = { },
            onCommentAdded = {
                scope.launch {
                    commentsCountMap = supabaseManager.getAllCommentsCountMap()
                }
            },
            detailViews = detailViews,
            detailLoves = detailLoves,
            detailDownloads = detailDownloads,
            onViewsUpdate = { detailViews = it },
            onLovesUpdate = { detailLoves = it },
            onDownloadsUpdate = { detailDownloads = it },
            confirmDownloadPreset = { handleDownloadClick(it) },
            presetsCatalog = presets,
            onPresetClick = { detailPreset = it },
            onNavigateToProfile = { detailPreset = null; currentTab = ScreenTab.PROFILE }
        )
    } else if (showSettingsScreen) {
        BackHandler(onBack = { showSettingsScreen = false })
        SettingsScreen(
            onBack = { showSettingsScreen = false },
            session = session,
            dataStoreManager = dataStoreManager
        )
    } else {
    BackHandler(enabled = currentTab != ScreenTab.CATALOG) {
        currentTab = ScreenTab.CATALOG
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = !isSystemNavVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (currentTab != ScreenTab.PROFILE) {
                        com.bearrushbuilder.app.data.BannerAdView()
                    }
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = currentTab == ScreenTab.CATALOG,
                            onClick = { currentTab = ScreenTab.CATALOG },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Katalog") },
                            label = { Text("Katalog", style = MaterialTheme.typography.labelMedium) }
                        )
                        NavigationBarItem(
                            selected = currentTab == ScreenTab.SHOP,
                            onClick = { currentTab = ScreenTab.SHOP },
                            icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "Toko Koin") },
                            label = { Text("Toko Koin", style = MaterialTheme.typography.labelMedium) }
                        )
                        NavigationBarItem(
                            selected = currentTab == ScreenTab.PROFILE,
                            onClick = { currentTab = ScreenTab.PROFILE },
                            icon = { Icon(Icons.Default.Person, contentDescription = "Profil") },
                            label = { Text("Profil", style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        val bottomPadding = if (isSystemNavVisible) 0.dp else paddingValues.calculateBottomPadding()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomPadding)
        ) {
            when (currentTab) {
                ScreenTab.CATALOG -> {
                    CatalogTab(
                        presets = filteredPresets,
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onCategorySelected = onCategorySelected,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        downloadStates = downloadStates,
                        downloadManager = downloadManager,
                        commentsCountMap = commentsCountMap,
                        onCardClick = { preset -> detailPreset = preset },
                        onDownloadClick = { preset -> handleDownloadClick(preset) }
                    )
                }
                ScreenTab.SHOP -> {
                    ShopTab(
                        session = session,
                        profile = profile,
                        supabaseManager = supabaseManager,
                        dataStoreManager = dataStoreManager,
                        onRefreshProfile = refreshProfileData,
                        onNavigateToProfile = { currentTab = ScreenTab.PROFILE },
                        billingManager = billingManager
                    )
                }
                ScreenTab.PROFILE -> {
                    ProfileTab(
                        session = session,
                        profile = profile,
                        ownedPresets = ownedPresets,
                        isLoadingProfile = isLoadingProfile,
                        supabaseManager = supabaseManager,
                        dataStoreManager = dataStoreManager,
                        onRefreshProfile = refreshProfileData,
                        presetsCatalog = presets,
                        onPresetClick = { preset -> detailPreset = preset },
                        downloadManager = downloadManager,
                        onNavigateToSettings = { showSettingsScreen = true },
                        onCreatorClick = { showCreatorUploadScreen = true }
                    )
                }
            }
        }
    }
    }

    // ── Confirm download dialog ──
    confirmDownloadPreset?.let { preset ->
        val isPremium = !preset.is_free
        val isAlreadyOwned = ownedPresets.any { it.presetId == preset.id }

        AlertDialog(
            onDismissRequest = { confirmDownloadPreset = null },
            confirmButton = {
                Button(
                    onClick = {
                        val p = preset
                        confirmDownloadPreset = null
                        val activity = context.findActivity()
                        if (activity != null) {
                            AdsManager.showRewarded(activity,
                                onRewarded = {
                                     pendingDownloadPreset = p
                                     val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                         true
                                     } else {
                                         androidx.core.content.ContextCompat.checkSelfPermission(
                                             context,
                                             android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                         ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                     }

                                     if (hasPermission) {
                                         val started = tryDirectDownload(p)
                                         if (!started) {
                                             createDocumentLauncher.launch("${p.name}.bin")
                                         }
                                     } else {
                                         storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                     }
                                },
                                onSkipped = {
                                    downloadStates[p.id] = Float.POSITIVE_INFINITY
                                    Toast.makeText(context, "Iklan harus ditonton sampai selesai untuk mendapatkan preset!", Toast.LENGTH_LONG).show()
                                }
                            )
                        } else {
                            // Fallback jika Activity tidak ditemukan (langsung download tanpa iklan)
                            pendingDownloadPreset = p
                            val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                true
                            } else {
                                androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            }

                            if (hasPermission) {
                                val started = tryDirectDownload(p)
                                if (!started) {
                                    createDocumentLauncher.launch("${p.name}.bin")
                                }
                            } else {
                                storagePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Ya, Unduh!", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDownloadPreset = null }) { Text("Batal") }
            },
            title = { Text("Konfirmasi Unduh", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Preset \"${preset.name}\" siap diunduh!",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val coinCost = if (isAlreadyOwned) 0 else preset.price.toInt()
                    if (coinCost > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Unduh mod ini akan memotong $coinCost Koin milik Anda.",
                            color = Primary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isAlreadyOwned)
                                "Anda sudah memiliki preset ini (tidak memotong koin)."
                            else
                                "Preset ini Gratis untuk diunduh.",
                            color = ColorSuccess,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Setelah konfirmasi, tonton iklan sampai selesai ya 😊\n\n" +
                               "Anda akan diminta memilih lokasi penyimpanan (Disarankan: Geokar_Mods/SBA/saved_scenes/).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    if (showCreatorUploadScreen) {
        BackHandler(onBack = { showCreatorUploadScreen = false })
        CreatorUploadScreen(
            currentUserId = session?.userId ?: "",
            token = session?.accessToken ?: "",
            username = profile?.username ?: session?.username ?: "",
            supabaseManager = supabaseManager,
            onBack = { showCreatorUploadScreen = false }
        )
    }

    // Old Dialog replaced with full page PresetDetailScreen below
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogTab(
    presets: List<Preset>,
    categories: List<Category>,
    selectedCategory: Category?,
    onCategorySelected: (Category?) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    downloadStates: MutableMap<Long, Float>,
    downloadManager: DownloadManager?,
    commentsCountMap: Map<Long, Int> = emptyMap(),
    onCardClick: (Preset) -> Unit,
    onDownloadClick: (Preset) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Animasi tekstur background gaming
        val infiniteBgTransition = rememberInfiniteTransition()
        val bgSlide by infiniteBgTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(12000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "bg_slide_catalog"
        )
        
        val patternPainter = painterResource(id = R.drawable.gaming_pattern_bg)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.5f)
                .drawBehind {
                    val tileHeightPx = 400.dp.toPx()
                    val intrinsicSize = patternPainter.intrinsicSize
                    val aspectRatio = if (intrinsicSize.height > 0) intrinsicSize.width / intrinsicSize.height else 1f
                    val tileWidthPx = tileHeightPx * aspectRatio
                    
                    val slideOffsetX = bgSlide * tileWidthPx
                    val slideOffsetY = bgSlide * tileHeightPx
                    
                    val startX = -tileWidthPx * 2 + slideOffsetX
                    val startY = -tileHeightPx * 2 + slideOffsetY
                    
                    var x = startX
                    while (x < size.width) {
                        var y = startY
                        while (y < size.height) {
                            translate(left = x, top = y) {
                                with(patternPainter) {
                                    draw(size = androidx.compose.ui.geometry.Size(tileWidthPx, tileHeightPx))
                                }
                            }
                            y += tileHeightPx
                        }
                        x += tileWidthPx
                    }
                }
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
            // Header image + gradient fade ke hitam — sama persis dengan ProfileTab
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 7f)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.bg_welcome_header),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                                    startY = 120f
                                )
                            )
                    )
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DownloadSbaButton(downloadManager = downloadManager)
                    Spacer(modifier = Modifier.height(16.dp))
                    SearchBar(searchQuery = searchQuery, onSearchQueryChange = onSearchQueryChange)
                    Spacer(modifier = Modifier.height(20.dp))
                    CategoriesSection(
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onCategorySelected = onCategorySelected
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    PopularPresetsHeader(resultCount = presets.size)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Featured card (preset pertama — lebar penuh)
            if (presets.isNotEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        PresetCard(
                            preset = presets.first(),
                            modifier = Modifier.fillMaxWidth(),
                            isDownloaded = downloadManager?.isDownloaded("${presets.first().name}.bin") == true,
                            downloadProgress = downloadStates[presets.first().id],
                            commentsCount = commentsCountMap[presets.first().id] ?: 0,
                            onCardClick = { onCardClick(presets.first()) },
                            onDownloadClick = { onDownloadClick(presets.first()) }
                        )
                    }
                }
            }

            items(presets.drop(1).chunked(2)) { rowItems ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    PresetsRow(
                        presets = rowItems,
                        downloadStates = downloadStates,
                        downloadManager = downloadManager,
                        commentsCountMap = commentsCountMap,
                        onCardClick = onCardClick,
                        onDownloadClick = onDownloadClick
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
            } // end LazyColumn
        } // end PullToRefreshBox
    }

}

@Composable
fun ShopTab(
    session: UserSession?,
    profile: UserProfile?,
    supabaseManager: SupabaseManager,
    dataStoreManager: DataStoreManager,
    onRefreshProfile: () -> Unit,
    onNavigateToProfile: () -> Unit,
    billingManager: BillingManager?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playProducts by if (billingManager != null) {
        billingManager.productDetails.collectAsState()
    } else {
        remember { mutableStateOf(emptyList<ProductDetails>()) }
    }
    var purchaseProgressPackage by remember { mutableStateOf<CoinPackage?>(null) }
    var successPackageCoins by remember { mutableStateOf<Int?>(null) }
    var successAdCoins by remember { mutableStateOf<Int?>(null) }
    var isWatchingAd by remember { mutableStateOf(false) }
    var showDevInfoDialog by remember { mutableStateOf(false) }

    val triggerWatchAd: () -> Unit = {
        val activity = context.findActivity()
        if (activity != null) {
            AdsManager.showRewarded(
                activity = activity,
                onRewarded = {
                    val coinsToAdd = 15
                    successAdCoins = coinsToAdd
                    scope.launch {
                        try {
                            if (session != null) {
                                runWithTokenRefresh(session, supabaseManager, dataStoreManager) { token ->
                                    val newCoins = (profile?.coins ?: 0) + coinsToAdd
                                    supabaseManager.updateCoins(session.userId, token, newCoins)
                                    dataStoreManager.updateCoins(newCoins)
                                    onRefreshProfile()
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gagal sinkronisasi koin: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onSkipped = {
                    Toast.makeText(context, "Iklan dilewati atau tidak tersedia", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            Toast.makeText(context, "Gagal memutar iklan: Activity tidak ditemukan", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFB300), PrimaryDark)))
    ) {
        // Decorative glowing stars in background
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = 0.05f), radius = 200f, center = Offset(size.width * 0.8f, 200f))
            drawCircle(Color.White.copy(alpha = 0.03f), radius = 300f, center = Offset(size.width * 0.2f, 500f))
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // --- 1. Stunning Header ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button (Soft rounded rectangle)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { onNavigateToProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp).offset(x = (-2).dp)
                    )
                }

                // Coin Pill display (Center)
                Box(
                    modifier = Modifier.wrapContentSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // White Pill sticking out to the right
                    Box(
                        modifier = Modifier
                            .padding(start = 26.dp) // Push pill behind the coin
                            .height(38.dp)
                            .widthIn(min = 100.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(SurfaceLight)
                            .padding(start = 28.dp, end = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (session != null) "${profile?.coins ?: 100}" else "516",
                            color = Color(0xFFD84315),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                    }
                    
                    // Large Golden Coin (Left overlapping)
                    Box(
                        modifier = Modifier.size(56.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        // The Gold Coin
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFFFFD54F), Color(0xFFFF8F00))))
                                .border(3.dp, Color(0xFFFFF59D), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star, 
                                contentDescription = "Coin", 
                                tint = Color(0xFFE65100).copy(alpha = 0.5f), 
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        
                        // The Green Plus Button (Overlapping bottom-right of the coin)
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .offset(x = 2.dp, y = 2.dp)
                                .clip(CircleShape)
                                .background(ColorButtonGreen)
                                .border(2.dp, Color.White, CircleShape)
                                .clickable { /* Add coins */ },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                
                // Placeholder to balance the space (invisible)
                Spacer(modifier = Modifier.size(44.dp))
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                // --- 2. Premium "Developer Mode Pack" Banner ---
                Image(
                    painter = painterResource(id = R.drawable.bear_coin_keeper),
                    contentDescription = "Developer Mode Pack",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { purchaseProgressPackage = CoinPackage(15000, "Rp 59.000", "Developer Mode Pack") },
                    contentScale = ContentScale.FillWidth
                )
                Spacer(modifier = Modifier.height(16.dp))

                // --- 3. Developer Mode Card (Redesigned) ---
                val devProduct = playProducts.find { it.productId == "developer_mode" }
                val formattedDevPrice = devProduct?.oneTimePurchaseOfferDetails?.formattedPrice
                    ?: devProduct?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                    ?: "Rp 39.000" // Fallback price

                val devBtnInteraction = remember { MutableInteractionSource() }
                val devBtnPressed by devBtnInteraction.collectIsPressedAsState()
                val devBtnScale by animateFloatAsState(
                    targetValue = if (devBtnPressed) 0.94f else 1f,
                    animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
                    label = "dev_btn_scale"
                )

                // Shimmer sweep animation for the buy button
                val devShimmer = rememberInfiniteTransition(label = "dev_shimmer")
                val devShimmerX by devShimmer.animateFloat(
                    initialValue = -300f,
                    targetValue = 600f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 2200, easing = LinearEasing, delayMillis = 800),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "dev_shimmer_x"
                )

                Box(modifier = Modifier.fillMaxWidth().graphicsLayer { clip = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clickable { showDevInfoDialog = true },
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Background gradient: deep roasted cocoa → charred amber
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(ColorDevCard, ColorDevCardEnd, Color(0xFFA0522D))
                                        )
                                    )
                            )
                            // Subtle radial glow on the right (warm highlight)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Accent.copy(alpha = 0.12f),
                                                Color.Transparent
                                            ),
                                            center = Offset(Float.POSITIVE_INFINITY, 0f),
                                            radius = 400f
                                        )
                                    )
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 112.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                    Text(
                                        text = "Developer Mode",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 17.sp,
                                        letterSpacing = (-0.3).sp
                                    )
                                    Text(
                                        text = "Buat Preset kamu sendiri",
                                        color = Color.White.copy(alpha = 0.75f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                // Buy button — white pill with amber text, shimmer overlay, press scale
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer {
                                            scaleX = devBtnScale
                                            scaleY = devBtnScale
                                        }
                                ) {
                                    Button(
                                        onClick = {
                                            if (devProduct != null) {
                                                val activity = context.findActivity()
                                                if (activity != null) {
                                                    if (billingManager != null) {
                                                        billingManager.launchBillingFlow(activity, devProduct)
                                                    } else {
                                                        Toast.makeText(context, "Layanan Google Play tidak tersedia", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Gagal memproses transaksi: Activity tidak ditemukan", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                purchaseProgressPackage = CoinPackage(0, formattedDevPrice, "Developer Mode")
                                            }
                                        },
                                        interactionSource = devBtnInteraction,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White,
                                            contentColor = PrimaryDark
                                        ),
                                        shape = RoundedCornerShape(50.dp),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text(
                                            text = formattedDevPrice,
                                            color = PrimaryDark,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            letterSpacing = (-0.2).sp
                                        )
                                    }
                                    // Shimmer sweep overlay clipped to button shape
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clip(RoundedCornerShape(50.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color.White.copy(alpha = 0.35f),
                                                        Color.Transparent
                                                    ),
                                                    start = Offset(devShimmerX - 80f, 0f),
                                                    end = Offset(devShimmerX + 80f, 0f)
                                                )
                                            )
                                    )
                                }
                            }
                        }
                    }

                    // Bear overflows the card frame — sibling of Card so unclipped, drawn on top
                    Image(
                        painter = painterResource(id = R.drawable.bear_developer),
                        contentDescription = "Developer Mode",
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = 8.dp)
                            .size(100.dp),
                        contentScale = ContentScale.Fit
                    )

                    // ✦ PREMIUM badge — top-right corner
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-12).dp, y = (-8).dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Accent)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "✦ PREMIUM",
                            color = Color(0xFF3E2723),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))


                // --- 4. Coin List ---
                val isIndo = java.util.Locale.getDefault().country == "ID" || java.util.Locale.getDefault().country == "IDN"
                class ShopItem(val productId: String, val title: String, val priceIdr: String, val priceUsd: String, val icon: Int, val isPack: Boolean, val badge: String)
                val packagesData = listOf(
                    ShopItem("starter_pack", "500 COIN", "Rp 15.000", "$0.99", R.drawable.bear_coin_pile, false, ""),
                    ShopItem("popular_pack", "1300 COIN", "Rp 25.000", "$1.99", R.drawable.bear_coin_stack, false, "20% MORE"),
                    ShopItem("got_pack", "2000 COIN", "Rp 54.000", "$3.99", R.drawable.bear_coin_purse, false, "60% MORE")
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Free Coin Card (Tonton Iklan)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clickable {
                                triggerWatchAd()
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon Box
                            Box(
                                modifier = Modifier.size(64.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val infiniteTransition = rememberInfiniteTransition()
                                val scale by infiniteTransition.animateFloat(
                                    initialValue = 0.95f,
                                    targetValue = 1.05f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1000, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "free_coin_scale"
                                )
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 0.1f,
                                    targetValue = 1.0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "free_sparkle_alpha"
                                )

                                Box(contentAlignment = Alignment.Center) {
                                    Image(
                                        painter = painterResource(id = R.drawable.bear_coin_free),
                                        contentDescription = null,
                                        modifier = Modifier.size(135.dp).scale(scale),
                                        contentScale = ContentScale.Fit
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = alpha),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-4).dp, y = 4.dp)
                                        .size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Text Details
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = ColorCoinBadge,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "FREE COIN",
                                        color = OnBackgroundLight,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp
                                    )
                                }
                                Text(
                                    text = "WATCH AD (+15)",
                                    color = Primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }

                            // Free Button
                            Button(
                                onClick = {
                                    triggerWatchAd()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorButtonGreen),
                                shape = RoundedCornerShape(14.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text(
                                    text = "FREE",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    packagesData.forEach { pack ->
                        val productDetails = playProducts.find { it.productId == pack.productId }
                        val formattedPrice = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice 
                            ?: (if (isIndo) pack.priceIdr else pack.priceUsd)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clickable {
                                    val product = playProducts.find { it.productId == pack.productId }
                                    if (product != null) {
                                        val activity = context.findActivity()
                                        if (activity != null) {
                                            if (billingManager != null) {
                                                billingManager.launchBillingFlow(activity, product)
                                            } else {
                                                Toast.makeText(context, "Layanan Google Play tidak tersedia", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Gagal memproses transaksi: Activity tidak ditemukan", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Produk belum terhubung ke Google Play Store", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceLight), // Surface Light
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Icon
                                Box(
                                    modifier = Modifier
                                        .size(64.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val infiniteTransition = rememberInfiniteTransition()
                                    val scale by infiniteTransition.animateFloat(
                                        initialValue = 0.95f,
                                        targetValue = 1.05f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1000, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "coin_scale"
                                    )
                                    val alpha by infiniteTransition.animateFloat(
                                        initialValue = 0.1f,
                                        targetValue = 1.0f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(800, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "sparkle_alpha"
                                    )

                                    Image(
                                        painter = painterResource(id = pack.icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(60.dp).scale(scale),
                                        contentScale = ContentScale.Fit
                                    )
                                    
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = alpha),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = (-4).dp, y = 4.dp)
                                            .size(16.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                // Text Details
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (!pack.isPack) {
                                            Icon(Icons.Default.Star, contentDescription = null, tint = ColorCoinBadge, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = pack.title,
                                            color = OnBackgroundLight, // Deep Chocolate
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp
                                        )
                                    }
                                    if (pack.badge.isNotEmpty()) {
                                        Text(
                                            text = pack.badge,
                                            color = Primary, // Honey Orange
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                
                                // Buy Button
                                Button(
                                    onClick = {
                                        val product = playProducts.find { it.productId == pack.productId }
                                        if (product != null) {
                                            val activity = context.findActivity()
                                            if (activity != null) {
                                                if (billingManager != null) {
                                                    billingManager.launchBillingFlow(activity, product)
                                                } else {
                                                    Toast.makeText(context, "Layanan Google Play tidak tersedia", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "Gagal memproses transaksi: Activity tidak ditemukan", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Produk belum terhubung ke Google Play Store", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary), // Honey Orange
                                    shape = RoundedCornerShape(14.dp),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = formattedPrice,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

        }
    }
    
    // --- Popups ---
    if (isWatchingAd) {
        AlertDialog(
            onDismissRequest = { isWatchingAd = false },
            confirmButton = {},
            title = { Text("Memutar Iklan...", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(color = Primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Menonton sponsor untuk koin gratis...", textAlign = TextAlign.Center)
                }
            }
        )
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(3000)
            val coinsToAdd = 200
            successPackageCoins = coinsToAdd
            isWatchingAd = false
            scope.launch {
                try {
                    if (session != null) {
                        runWithTokenRefresh(session, supabaseManager, dataStoreManager) { token ->
                            val newCoins = (profile?.coins ?: 0) + coinsToAdd
                            supabaseManager.updateCoins(session.userId, token, newCoins)
                            dataStoreManager.updateCoins(newCoins)
                            onRefreshProfile()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Gagal sinkronisasi koin: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    purchaseProgressPackage?.let { pkg ->
        LaunchedEffect(pkg) {
            kotlinx.coroutines.delay(1500)
            successPackageCoins = pkg.coins
            purchaseProgressPackage = null
        }
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Memproses Pembayaran", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Sedang mensimulasikan gerbang pembayaran aman. Mohon tunggu...", textAlign = TextAlign.Center)
                }
            }
        )
    }

    successPackageCoins?.let { coins ->
        AlertDialog(
            onDismissRequest = { successPackageCoins = null },
            confirmButton = {
                Button(onClick = { successPackageCoins = null }) { Text("OK", color = Color.White) }
            },
            title = { Text("Pembayaran Sukses!", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ColorSuccess, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if(coins > 0) "Selamat! Paket berisi +$coins koin berhasil dibeli." else "Selamat! Developer Mode telah diaktifkan.",
                        textAlign = TextAlign.Center
                    )
                }
            }
        )
    }

    successAdCoins?.let { coins ->
        AlertDialog(
            onDismissRequest = { successAdCoins = null },
            confirmButton = {
                Button(
                    onClick = { successAdCoins = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Text("Koin Gratis!", fontWeight = FontWeight.Black, color = OnBackgroundLight)
            },
            text = {
                val infiniteTransition = rememberInfiniteTransition()
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.scale(scale)) {
                        Image(
                            painter = painterResource(id = R.drawable.bear_coin_free),
                            contentDescription = "Coin Icon",
                            modifier = Modifier.size(120.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Selamat! Anda mendapatkan +$coins koin gratis karena telah menonton iklan.",
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = OnBackgroundLight
                    )
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = SurfaceLight
        )
    }

    if (showDevInfoDialog) {
        AlertDialog(
            onDismissRequest = { showDevInfoDialog = false },
            confirmButton = {
                Button(onClick = { showDevInfoDialog = false }) { Text("OK", color = Color.White) }
            },
            title = { Text("Fitur Dalam Pengembangan", fontWeight = FontWeight.Bold) },
            text = {
                Text("Fitur Menu Developer sedang dalam pengembangan dan akan hadir di update selanjutnya!", textAlign = TextAlign.Center)
            }
        )
    }
}

fun getBrandAvatarDrawableRes(): Int {
    val manufacturer = android.os.Build.MANUFACTURER.lowercase(java.util.Locale.ROOT)
    return when {
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> R.drawable.bear_xiaomi
        manufacturer.contains("samsung") -> R.drawable.bear_samsung
        manufacturer.contains("oppo") -> R.drawable.bear_oppo
        manufacturer.contains("realme") -> R.drawable.bear_realme
        else -> R.drawable.bear_default
    }
}

data class FloatingHeart(
    val id: Long,
    val startX: Float,
    val startY: Float,
    val speedX: Float,
    val amplitude: Float,
    val frequency: Float,
    val rotation: Float,
    val rotationSpeed: Float,
    val color: Color,
    val size: Float,
    val duration: Int = 1500,
    val travelY: Float = 380f
)

@Composable
fun FloatingHearts(
    hearts: List<FloatingHeart>,
    onRemoveHeart: (Long) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        hearts.forEach { heart ->
            key(heart.id) {
                FloatingHeartItem(
                    heart = heart,
                    onFinished = { onRemoveHeart(heart.id) }
                )
            }
        }
    }
}

@Composable
fun BoxScope.FloatingHeartItem(heart: FloatingHeart, onFinished: () -> Unit) {
    var animStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animStarted = true
    }
    
    val progress by animateFloatAsState(
        targetValue = if (animStarted) 1f else 0f,
        animationSpec = tween(
            durationMillis = heart.duration,
            easing = LinearOutSlowInEasing
        ),
        finishedListener = { onFinished() },
        label = "heartProgress"
    )

    // Calculate position
    val yOffset = heart.startY - (progress * heart.travelY)
    val sway = kotlin.math.sin(progress * Math.PI.toFloat() * heart.frequency) * heart.amplitude
    val xOffset = heart.startX + (progress * heart.speedX * 45f) + sway
    
    // Scale starts small, expands, and fades out
    val scale = if (progress < 0.15f) {
        (progress / 0.15f) * heart.size
    } else {
        ((1f - progress) / 0.85f) * heart.size
    }
    
    val alpha = if (progress < 0.15f) {
        progress / 0.15f
    } else {
        ((1f - progress) / 0.85f).coerceIn(0f, 1f)
    }
    
    val rotation = heart.rotation + (progress * heart.rotationSpeed)

    Icon(
        imageVector = Icons.Default.Favorite,
        contentDescription = null,
        tint = heart.color,
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = xOffset.dp, y = yOffset.dp)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                alpha = alpha,
                rotationZ = rotation
            )
            .size(24.dp)
    )
}

fun spawnHeartsBurst(
    scope: kotlinx.coroutines.CoroutineScope,
    floatingHearts: MutableList<FloatingHeart>,
    startX: Float,
    startY: Float
) {
    val heartColors = listOf(
        ColorLove,           // Pink
        Color(0xFFFF2E93),   // Neon Pink
        Color(0xFFFF4646),   // Neon Red
        Color(0xFFB02EFF),   // Electric Purple
        Color(0xFFFF85A1),   // Light Rose
        Color(0xFFFF0A54),   // Hot Pink
        Color(0xFFFF5E36),   // Coral Orange
        Accent               // Gold/Yellow
    )
    
    scope.launch {
        // ponytail: 14 partikel cukup untuk efek visual — 30 berat di HP low-end (pasar utama)
        repeat(14) { index ->
            val id = System.currentTimeMillis() + index + (Math.random() * 10000).toLong()
            val angleDeg = kotlin.random.Random.nextInt(260, 280) // Tighter vertical path (270 is straight up)
            val angleRad = angleDeg * (Math.PI / 180f)
            val speed = 2.5f + kotlin.random.Random.nextFloat() * 2f
            val speedX = kotlin.math.cos(angleRad).toFloat() * speed
            val amplitude = 8f + kotlin.random.Random.nextFloat() * 12f // Tighter sway
            val frequency = 1.0f + kotlin.random.Random.nextFloat() * 1.0f
            val size = 0.6f + kotlin.random.Random.nextFloat() * 0.8f
            val rotation = -15f + kotlin.random.Random.nextFloat() * 30f
            val rotationSpeed = -90f + kotlin.random.Random.nextFloat() * 180f
            val color = heartColors[kotlin.random.Random.nextInt(heartColors.size)]
            val duration = 1800 + kotlin.random.Random.nextInt(600) // Longer lifespan for longer trails
            val travelY = 450f + kotlin.random.Random.nextFloat() * 200f // Higher vertical path
            
            floatingHearts.add(
                FloatingHeart(
                    id = id,
                    startX = startX,
                    startY = startY,
                    speedX = speedX,
                    amplitude = amplitude,
                    frequency = frequency,
                    rotation = rotation,
                    rotationSpeed = rotationSpeed,
                    color = color,
                    size = size,
                    duration = duration,
                    travelY = travelY
                )
            )
            delay(50L) // Staggered delay creates the trailing stream effect
        }
    }
}

fun containsBadWords(text: String): Boolean {
    // 1. Convert to lowercase
    var normalized = text.lowercase(java.util.Locale.ROOT)
    
    // 2. Leetspeak replacements
    normalized = normalized
        .replace("4", "a")
        .replace("1", "i")
        .replace("!", "i")
        .replace("0", "o")
        .replace("3", "e")
        .replace("v", "u")
        .replace("8", "b")
        .replace("9", "g")
        .replace("2", "z")
        .replace("$", "s")
        .replace("@", "a")

    // 3. Remove repeated characters (e.g., "annjiiing" -> "anjing")
    val sb = java.lang.StringBuilder()
    if (normalized.isNotEmpty()) {
        sb.append(normalized[0])
        for (i in 1 until normalized.length) {
            if (normalized[i] != normalized[i - 1]) {
                sb.append(normalized[i])
            }
        }
    }
    val deDuplicated = sb.toString()

    // 4. Comprehensive lists of bad words (length >= 4)
    val badWords = setOf(
        // Indonesian (Bahasa Indonesia kasar)
        "anjing", "anying", "anyink", "anjrit", "anjrot", "anjir", "babi", "bangsat", "bajingan", 
        "kunyuk", "keparat", "taik", "kontol", "memek", "jembut", "ngentot", "ngentod", "peler", "perek", 
        "lonte", "jablay", "goblog", "goblok", "tolol", "bego", "idiot", "geblek", "asu", "celeng", "bacot", 
        "congor", "pecun", "silit", "tetek", "toket", "itil", "ngewe", "ewe", "sinting", "binal", 
        "brengsek", "setan", "iblis", "dajal", "dajjal", "pantek", "puki", "pukimak", "kampang", "cabo", 
        "kimak", "entot", "gentot", "kentot", "ngentit", "ngetot", "tempik",
        
        // Sundanese (Bahasa Sunda kasar)
        "bagong", "kehed", "heunceut", "beungeut", "jurig", "modar", "gundik", "beulok", "borok",
        "lentah", "jalingkak", "piceun", "cicing",
        
        // Javanese (Bahasa Jawa kasar)
        "jancok", "jancuk", "dancok", "juancok", "pekok", "ndasmu", "matamu", "cangkemu", "tempek", "kirik", 
        "gateli", "bajigur", "raimu", "kisoh", "umbel", "mbokne", "ancuk", "bento", "edan", "gemblung", "sarap",
        
        // English
        "fuck", "shit", "asshole", "bitch", "bastard", "cunt", "dick", "pussy", "motherfucker", "whore", 
        "slut", "wanker", "retard", "crap", "piss", "cocksucker", "faggot", "dyke", "bollocks"
    )

    // Check if substring matches in either version
    for (word in badWords) {
        if (normalized.contains(word) || deDuplicated.contains(word)) {
            return true
        }
    }
    
    // 5. Short bad words checked as whole words to avoid false positives (e.g. "suka" containing "su")
    val wordsList = normalized.split(Regex("[\\s\\p{Punct}]+"))
    val deduplicatedWordsList = deDuplicated.split(Regex("[\\s\\p{Punct}]+"))
    
    val shortBadWords = setOf("tai", "su", "sia", "asu", "fuk", "wtg", "crap", "piss", "fag")
    for (word in shortBadWords) {
        if (wordsList.contains(word) || deduplicatedWordsList.contains(word)) {
            return true
        }
    }

    return false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTab(
    session: UserSession?,
    profile: UserProfile?,
    ownedPresets: List<UserPresetLog>,
    isLoadingProfile: Boolean,
    supabaseManager: SupabaseManager,
    dataStoreManager: DataStoreManager,
    onRefreshProfile: () -> Unit,
    presetsCatalog: List<Preset>,
    onPresetClick: (Preset) -> Unit,
    downloadManager: DownloadManager?,
    onNavigateToSettings: () -> Unit,
    onCreatorClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var usernameInput by remember { mutableStateOf("") }
    var isSignUpMode by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var isAuthLoading by remember { mutableStateOf(false) }

    var showEditProfileDialog by remember { mutableStateOf(false) }

    var ownFollowStats by remember { mutableStateOf<Pair<Int, Int>>(0 to 0) }

    LaunchedEffect(session, profile) {
        if (session != null) {
            try {
                val stats = supabaseManager.getFollowStats(session.userId, session.accessToken)
                ownFollowStats = stats
            } catch (_: Exception) {}
        }
    }

    // ── DEBUG: Skip login screen, langsung ke profile page ──
    // Hanya aktif saat BuildConfig.DEBUG = true (debug build).
    // Release build wajib login seperti biasa.
    val effectiveSession: UserSession? = if (com.bearrushbuilder.app.BuildConfig.DEBUG && session == null) {
        UserSession(
            userId = "debug-user-id",
            email = "debug@bearrush.dev",
            username = "DebugUser",
            accessToken = "debug-token",
            refreshToken = "debug-refresh-token",
            coins = 9999
        )
    } else {
        session
    }
    val effectiveProfile: UserProfile? = if (com.bearrushbuilder.app.BuildConfig.DEBUG && session == null && profile == null) {
        UserProfile(
            id = "debug-user-id",
            username = "DebugUser",
            nickname = "Debug User",
            avatarUrl = "",
            bio = "🐻 [DEBUG MODE] Profile ini hanya muncul di debug build.",
            coins = 9999
        )
    } else {
        profile
    }

    if (effectiveSession == null) {
        val gso = remember {
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(com.bearrushbuilder.app.BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .requestEmail()
                .build()
        }
        val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    isAuthLoading = true
                    authError = null
                    scope.launch {
                        try {
                            val authResp = supabaseManager.signInWithGoogle(idToken)
                            val token = authResp.accessToken ?: throw Exception("Token missing")
                            val userId = authResp.user?.id ?: ""
                            val userEmail = authResp.user?.email ?: ""
                            val userUsername = authResp.user?.email?.split("@")?.firstOrNull() ?: ""
                            val dId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                            val realProfile = supabaseManager.getProfile(userId, token, userEmail, userUsername, deviceId = dId)
                            val sessionObj = UserSession(
                                userId = userId,
                                email = userEmail,
                                username = realProfile.username,
                                accessToken = token,
                                refreshToken = authResp.refreshToken ?: "",
                                coins = realProfile.coins
                            )
                            dataStoreManager.saveUserSession(sessionObj)
                            Toast.makeText(context, "Login Google berhasil!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            authError = e.localizedMessage
                        } finally {
                            isAuthLoading = false
                        }
                    }
                } else {
                    authError = "Google tidak mengembalikan ID Token."
                }
            } catch (e: ApiException) {
                val msg = when (e.statusCode) {
                    10 -> "Error 10: SHA-1 fingerprint APK ini belum terdaftar di Google Cloud Console. Daftarkan dulu via https://console.cloud.google.com/"
                    12500 -> "Error 12500: Google Play Services belum update, coba update dulu."
                    12501 -> "Login dibatalkan pengguna."
                    7 -> "Error 7: Tidak ada koneksi internet."
                    else -> "Google Sign In gagal (kode: ${e.statusCode}): ${e.localizedMessage}"
                }
                authError = msg
                android.util.Log.e("GoogleSignIn", "ApiException statusCode=${e.statusCode}", e)
            } catch (e: Exception) {
                authError = "Error: ${e.localizedMessage}"
                android.util.Log.e("GoogleSignIn", "Exception saat sign in", e)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Image with bg_welcome_header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 7f)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.bg_welcome_header),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Gradient overlay to fade to dark background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background
                                    ),
                                    startY = 50f
                                )
                            )
                    )
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Edit Profile",
                            tint = Color.White
                        )
                    }
                }
            }

            // Title & Content
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isSignUpMode) "Create an account" else "Sign in to account",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        ),
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = if (isSignUpMode) "Daftar untuk menikmati preset eksklusif Bear Rush Mod." else "Masuk kembali untuk menggunakan koin dan preset Anda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    )

                    if (authError != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF2A0A0A)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = authError ?: "",
                                color = Color(0xFFFF6B6B),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Email Field
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        placeholder = { Text("Email address", color = Color.White.copy(alpha = 0.4f)) },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color.White.copy(alpha = 0.6f)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF1E1E1E),
                            unfocusedContainerColor = Color(0xFF161616),
                            focusedBorderColor = Color(0xFFFF9800),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                        )
                    )

                    if (isSignUpMode) {
                        Spacer(modifier = Modifier.height(14.dp))
                        // Optional Username Field for Sign Up
                        OutlinedTextField(
                            value = usernameInput,
                            onValueChange = { usernameInput = it },
                            placeholder = { Text("Username (Optional)", color = Color.White.copy(alpha = 0.4f)) },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color.White.copy(alpha = 0.6f)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF1E1E1E),
                                unfocusedContainerColor = Color(0xFF161616),
                                focusedBorderColor = Color(0xFFFF9800),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Password Field
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        placeholder = { Text("Password", color = Color.White.copy(alpha = 0.4f)) },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.6f)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF1E1E1E),
                            unfocusedContainerColor = Color(0xFF161616),
                            focusedBorderColor = Color(0xFFFF9800),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Button: Continue with Email (gradient pill button!)
                    Button(
                        onClick = {
                            if (emailInput.isBlank() || passwordInput.isBlank()) {
                                authError = "Harap isi email dan password."
                                return@Button
                            }
                            isAuthLoading = true
                            authError = null
                            scope.launch {
                                try {
                                    if (isSignUpMode) {
                                        val cleanUser = usernameInput.ifBlank { emailInput.split("@").first() }.trim().lowercase().replace(" ", "").replace("@", "")
                                        val finalUsername = "@$cleanUser"
                                        val signupResp = supabaseManager.signUp(emailInput, finalUsername, passwordInput)
                                        val token = signupResp.accessToken
                                        if (token != null) {
                                            val userId = signupResp.user?.id ?: ""
                                            val dId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                                            val realProfile = supabaseManager.getProfile(userId, token, emailInput, finalUsername, deviceId = dId)
                                            val sessionObj = UserSession(
                                                userId = userId,
                                                email = emailInput,
                                                username = realProfile.username,
                                                accessToken = token,
                                                refreshToken = signupResp.refreshToken ?: "",
                                                coins = realProfile.coins
                                            )
                                            dataStoreManager.saveUserSession(sessionObj)
                                            Toast.makeText(context, "Daftar berhasil!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            authError = "Registrasi sukses! Silakan cek email masuk untuk aktivasi."
                                        }
                                    } else {
                                        val loginResp = supabaseManager.signIn(emailInput, passwordInput)
                                        val token = loginResp.accessToken ?: throw Exception("Token missing")
                                        val userId = loginResp.user?.id ?: ""
                                        val dId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                                        val cleanUser = usernameInput.ifBlank { emailInput.split("@").first() }.trim().lowercase().replace(" ", "").replace("@", "")
                                        val finalUsername = "@$cleanUser"
                                        val realProfile = supabaseManager.getProfile(userId, token, emailInput, finalUsername, deviceId = dId)
                                        val sessionObj = UserSession(
                                            userId = userId,
                                            email = emailInput,
                                            username = realProfile.username,
                                            accessToken = token,
                                            refreshToken = loginResp.refreshToken ?: "",
                                            coins = realProfile.coins
                                        )
                                        dataStoreManager.saveUserSession(sessionObj)
                                        Toast.makeText(context, "Login berhasil!", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    authError = e.localizedMessage
                                } finally {
                                    isAuthLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFE91E63), Color(0xFFF44336))
                                    ),
                                    shape = RoundedCornerShape(25.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isAuthLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text(
                                    text = "Continue with Email",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Button: Continue with Google (Black background pill button)
                    OutlinedButton(
                        onClick = {
                            val signInIntent = googleSignInClient.signInIntent
                            launcher.launch(signInIntent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFF161616),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            GoogleIcon()
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Continue with Google",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Navigation Link at bottom
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSignUpMode) "Already have an account? " else "Don't have an account? ",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isSignUpMode) "Sign In" else "Sign Up",
                            color = Color(0xFFF44336),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.clickable {
                                isSignUpMode = !isSignUpMode
                                authError = null
                            }
                        )
                    }
                }
            }
        }
    } else {
        // Mode Terautentikasi -> Tampilkan Halaman Profil User ala Pinterest
        var activeTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) } // 0 = Dibuat, 1 = Disimpan

        val createdPresets = remember(presetsCatalog, effectiveProfile, effectiveSession) {
            presetsCatalog.filter { 
                val profileUser = (effectiveProfile?.username ?: effectiveSession?.username ?: "").replace("@", "")
                it.author.replace("@", "").equals(profileUser, ignoreCase = true) 
            }
        }

        val savedPresets = remember(ownedPresets, presetsCatalog) {
            ownedPresets.mapNotNull { owned ->
                presetsCatalog.find { it.id == owned.presetId } ?: Preset(
                    id = owned.presetId,
                    name = owned.presetName,
                    category = owned.presetCategory,
                    preview_url = owned.presetPreviewUrl
                )
            }
        }

        if (isLoadingProfile) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFF9800))
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
                val infiniteBgTransition = rememberInfiniteTransition()
                val bgSlide by infiniteBgTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(12000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "bg_slide"
                )
                
                val patternPainter = painterResource(id = R.drawable.gaming_pattern_bg)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.4f)
                        .drawBehind {
                            val tileHeightPx = 400.dp.toPx()
                            val intrinsicSize = patternPainter.intrinsicSize
                            val aspectRatio = if (intrinsicSize.height > 0) intrinsicSize.width / intrinsicSize.height else 1f
                            val tileWidthPx = tileHeightPx * aspectRatio
                            
                            val slideOffsetX = bgSlide * tileWidthPx
                            val slideOffsetY = bgSlide * tileHeightPx
                            
                            val startX = -tileWidthPx * 2 + slideOffsetX
                            val startY = -tileHeightPx * 2 + slideOffsetY
                            
                            var x = startX
                            while (x < size.width) {
                                var y = startY
                                while (y < size.height) {
                                    translate(left = x, top = y) {
                                        with(patternPainter) {
                                            draw(size = androidx.compose.ui.geometry.Size(tileWidthPx, tileHeightPx))
                                        }
                                    }
                                    y += tileHeightPx
                                }
                                x += tileWidthPx
                            }
                        }
                )
                
                var isRefreshingProfile by remember { mutableStateOf(false) }
                
                PullToRefreshBox(
                    isRefreshing = isRefreshingProfile,
                    onRefresh = {
                        isRefreshingProfile = true
                        onRefreshProfile()
                        scope.launch {
                            kotlinx.coroutines.delay(1000)
                            isRefreshingProfile = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                    // 1. Cover Banner & Avatar & Info (Spans 2 columns)
                    item(span = { GridItemSpan(2) }) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Header Banner & Avatar Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.8f)
                            ) {
                                // Cover banner
                                Image(
                                    painter = painterResource(id = R.drawable.bg_welcome_header),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(2.28f)
                                        .align(Alignment.TopCenter)
                                )

                                // Settings Icon at top-right of cover
                                IconButton(
                                    onClick = onNavigateToSettings,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                        .size(36.dp)
                                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Edit Profile",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            
                                // Avatar overlapping
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .align(Alignment.BottomCenter)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(3.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                RenderUserAvatar(
                                    avatarUrl = effectiveProfile?.avatarUrl ?: "",
                                    fallbackUsername = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // User Info (Name, Username, Bio, Stats)
                        Text(
                            text = (effectiveProfile?.nickname?.ifEmpty { effectiveProfile.username } ?: effectiveProfile?.username ?: effectiveSession?.username ?: "").replace("@", "").ifEmpty { "Guest" },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        val rawUsername = effectiveProfile?.username ?: effectiveSession?.username ?: ""
                        val displayHandle = rawUsername.lowercase().let { if (it.startsWith("@")) it else "@$it" }
                        Text(
                            text = displayHandle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        // Follower count mockup & Coins
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${ownFollowStats.first} pengikut • ${ownFollowStats.second} mengikuti",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.MonetizationOn,
                                contentDescription = "Koin",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${effectiveProfile?.coins ?: effectiveSession?.coins ?: 0}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Text(
                            text = effectiveProfile?.bio?.ifEmpty { "Belum ada bio singkat." }
                                ?: "Belum ada bio singkat.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )

                        // Action Buttons Row (Edit Profil & Creator)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { showEditProfileDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "Edit Profile",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            Button(
                                onClick = onCreatorClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF9800)
                                ),
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "Creator",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }

                // 2. Tabs ("Dibuat" & "Disimpan") (Spans 2 columns)
                item(span = { GridItemSpan(2) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val tab1Selected = activeTab == 0
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { activeTab = 0 }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = "Dibuat",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (tab1Selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .height(3.dp)
                                    .fillMaxWidth(0.5f)
                                    .clip(CircleShape)
                                    .background(if (tab1Selected) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                            )
                        }

                        // Divider vertikal tipis di tengah
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .align(Alignment.CenterVertically)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { activeTab = 1 }
                                .padding(vertical = 10.dp)
                        ) {
                            val tab2Selected = activeTab == 1
                            Text(
                                text = "Disimpan",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (tab2Selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .height(3.dp)
                                    .fillMaxWidth(0.5f)
                                    .clip(CircleShape)
                                    .background(if (tab2Selected) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                            )
                        }
                    }
                }

                // 3. Preset Items (Grid)
                val activePresets = if (activeTab == 0) createdPresets else savedPresets
                if (activePresets.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (activeTab == 0) "Belum ada preset yang dibuat." else "Belum ada preset yang disimpan.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    items(activePresets) { preset ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPresetClick(preset) }
                                .padding(8.dp)
                        ) {
                            // Rounded Preview Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                val firstUrl = remember(preset.preview_url) {
                                    preset.preview_url.split(",").firstOrNull()?.trim() ?: ""
                                }
                                if (firstUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = firstUrl,
                                        contentDescription = preset.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = preset.name.firstOrNull()?.toString() ?: "",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                // Price Overlay Badge
                                Surface(
                                    shape = RoundedCornerShape(bottomStart = 8.dp),
                                    color = if (preset.price == 0L) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Text(
                                        text = if (preset.price > 0) "${preset.price} Koin" else "FREE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            // Preset Name
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
                } // closes PullToRefreshBox
            } // closes Box
        }
    }

    // ── Edit Profile Dialog ──
    if (showEditProfileDialog && effectiveSession != null) {
        var editNickname by remember { mutableStateOf(effectiveProfile?.nickname?.ifEmpty { effectiveProfile.username } ?: effectiveSession?.username ?: "") }
        var editUsername by remember { mutableStateOf(effectiveProfile?.username?.lowercase()?.let { if (it.startsWith("@")) it else "@$it" } ?: effectiveSession?.username?.lowercase()?.let { if (it.startsWith("@")) it else "@$it" } ?: "") }
        var editBio by remember { mutableStateOf(effectiveProfile?.bio ?: "") }
        var editAvatar by remember { mutableStateOf(effectiveProfile?.avatarUrl ?: "") }
        var isUpdatingProfile by remember { mutableStateOf(false) }
        
        var isUsernameTakenState by remember { mutableStateOf(false) }
        var suggestedUsernames by remember { mutableStateOf<List<String>>(emptyList()) }
        var isCheckingUsername by remember { mutableStateOf(false) }

        LaunchedEffect(editUsername) {
            val trimmed = editUsername.trim().lowercase().replace(" ", "")
            val currentUsernameClean = (effectiveProfile?.username ?: "").lowercase().replace(" ", "")
            if (trimmed.length <= 1 || trimmed == currentUsernameClean) {
                isUsernameTakenState = false
                suggestedUsernames = emptyList()
                return@LaunchedEffect
            }
            delay(500)
            isCheckingUsername = true
            try {
                val taken = supabaseManager.isUsernameTaken(trimmed, effectiveSession.userId)
                isUsernameTakenState = taken
                if (taken) {
                    val random = java.util.Random()
                    val suggestions = mutableListOf<String>()
                    var attempts = 0
                    while (suggestions.size < 3 && attempts < 20) {
                        attempts++
                        val suffix = when (random.nextInt(3)) {
                            0 -> "${random.nextInt(900) + 100}"
                            1 -> "_bear"
                            else -> "mod${random.nextInt(90) + 10}"
                        }
                        val candidate = trimmed + suffix
                        if (!supabaseManager.isUsernameTaken(candidate, effectiveSession.userId) && !suggestions.contains(candidate)) {
                            suggestions.add(candidate)
                        }
                    }
                    suggestedUsernames = suggestions
                } else {
                    suggestedUsernames = emptyList()
                }
            } catch (_: Exception) {
            } finally {
                isCheckingUsername = false
            }
        }
        
        // Mocking login streak for UI purpose (this can be replaced with real backend logic later)
        val userLoginStreakWeeks = 2

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showEditProfileDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1E1E1E), // Dark modern theme
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Edit Profile",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color.White
                    )

                    OutlinedTextField(
                        value = editNickname,
                        onValueChange = { editNickname = it },
                        label = { Text("Nickname (Nama Tampilan)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF9800),
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedLabelColor = Color(0xFFFF9800),
                            unfocusedLabelColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { input ->
                            val cleaned = input.lowercase().replace(" ", "")
                            editUsername = if (cleaned.startsWith("@")) {
                                if (cleaned.length == 1) "@" else cleaned
                            } else {
                                "@$cleaned"
                            }
                        },
                        label = { Text("Username (Format: @handle)") },
                        isError = isUsernameTakenState,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF9800),
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedLabelColor = Color(0xFFFF9800),
                            unfocusedLabelColor = Color.Gray,
                            errorBorderColor = Color(0xFFFF5252),
                            errorLabelColor = Color(0xFFFF5252)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (isCheckingUsername) {
                        Text("Memeriksa ketersediaan handle...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    } else if (isUsernameTakenState) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Handle $editUsername sudah digunakan orang lain.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF5252))
                            if (suggestedUsernames.isNotEmpty()) {
                                Text("Opsi yang tersedia:", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    suggestedUsernames.forEach { suggestion ->
                                        AssistChip(
                                            onClick = { editUsername = suggestion },
                                            label = { Text("@$suggestion", color = Color.White, fontSize = 11.sp) },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = Color(0xFF2E2E2E)
                                            ),
                                            border = null
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it },
                        label = { Text("Biografi Pendek") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        singleLine = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF9800),
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedLabelColor = Color(0xFFFF9800),
                            unfocusedLabelColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text("Pilih Icon Profile", fontWeight = FontWeight.SemiBold, color = Color.White)
                    
                    val avatarList = listOf(
                        Triple("local:bear_avatar_1", "Armi Bear", 1),
                        Triple("local:bear_avatar_2", "Brave Bear", 2),
                        Triple("local:bear_avatar_3", "Cuddly Bear", 3),
                        Triple("local:bear_avatar_4", "Daring Bear", 4),
                        Triple("local:bear_avatar_5", "Epic Bear", 5),
                        Triple("local:bear_avatar_6", "Fierce Bear", 6),
                        Triple("local:bear_default", "Default Bear", 0),
                        Triple("local:bear_developer", "Developer Bear", 0),
                        Triple("local:bear_oppo", "Oppo Bear", 0),
                        Triple("local:bear_realme", "Realme Bear", 0),
                        Triple("local:bear_samsung", "Samsung Bear", 0),
                        Triple("local:bear_xiaomi", "Xiaomi Bear", 0)
                    )

                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(avatarList) { (avatarUrl, avatarName, requiredWeeks) ->
                            val isSelected = editAvatar == avatarUrl
                            val isLocked = userLoginStreakWeeks < requiredWeeks

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(80.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) Color(0xFFFF9800) else Color.DarkGray,
                                            shape = CircleShape
                                        )
                                        .clickable(enabled = !isLocked) {
                                            editAvatar = avatarUrl
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    RenderUserAvatar(
                                        avatarUrl = avatarUrl,
                                        fallbackUsername = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .alpha(if (isLocked) 0.4f else 1f)
                                    )
                                    
                                    if (isLocked) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Locked",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = avatarName,
                                    fontSize = 10.sp,
                                    color = if (isLocked) Color.Gray else Color.LightGray,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    lineHeight = 12.sp
                                )
                                if (isLocked) {
                                    Text(
                                        text = "Login $requiredWeeks mgg",
                                        fontSize = 9.sp,
                                        color = Color(0xFFFF5252),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = avatarList.find { it.first == editAvatar }?.second ?: editAvatar,
                        onValueChange = { editAvatar = it },
                        label = { Text("Type profile") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF9800),
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedLabelColor = Color(0xFFFF9800),
                            unfocusedLabelColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showEditProfileDialog = false },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Batal", color = Color.LightGray)
                        }
                        
                        Button(
                            onClick = {
                                val finalUsername = editUsername.trim().lowercase().replace(" ", "")
                                val finalNickname = editNickname.trim()
                                if (finalUsername.length <= 1 || finalNickname.isBlank()) return@Button
                                scope.launch {
                                    isUpdatingProfile = true
                                    // Di debug mode (mock session), skip API call server
                                    if (com.bearrushbuilder.app.BuildConfig.DEBUG && session == null) {
                                        Toast.makeText(context, "[DEBUG] Edit profil dinonaktifkan di debug mock session.", Toast.LENGTH_SHORT).show()
                                        showEditProfileDialog = false
                                        isUpdatingProfile = false
                                        return@launch
                                    }
                                    try {
                                        val updated = supabaseManager.updateProfile(
                                            userId = effectiveSession!!.userId,
                                            token = effectiveSession.accessToken,
                                            username = finalUsername,
                                            nickname = finalNickname,
                                            bio = editBio.trim(),
                                            avatarUrl = editAvatar.trim()
                                        )
                                        dataStoreManager.saveUserSession(
                                            UserSession(
                                                userId = effectiveSession.userId,
                                                email = effectiveSession.email,
                                                username = updated.username,
                                                accessToken = effectiveSession.accessToken,
                                                refreshToken = effectiveSession.refreshToken,
                                                coins = updated.coins
                                            )
                                        )
                                        onRefreshProfile()
                                        showEditProfileDialog = false
                                        Toast.makeText(context, "Profil berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Gagal mengupdate profil: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isUpdatingProfile = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF9800),
                                disabledContainerColor = Color(0xFFFF9800).copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isUpdatingProfile && !isCheckingUsername && !isUsernameTakenState && editUsername.isNotBlank() && editNickname.isNotBlank()
                        ) {
                            if (isUpdatingProfile) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Simpan", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── helper Composables ──

@Composable
fun RenderUserAvatar(avatarUrl: String, fallbackUsername: String? = null, modifier: Modifier = Modifier) {
    if (avatarUrl.isNotEmpty()) {
        if (avatarUrl.startsWith("local:")) {
            val resName = avatarUrl.removePrefix("local:")
            val context = LocalContext.current
            val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
            if (resId != 0) {
                Image(
                    painter = painterResource(id = resId),
                    contentDescription = "Avatar",
                    modifier = modifier,
                    contentScale = ContentScale.Crop
                )
                return
            }
        } else {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Avatar",
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
            return
        }
    }
    
    val fallbackPainter = if (fallbackUsername != null) {
        painterResource(id = getBrandAvatarForUsername(fallbackUsername))
    } else {
        painterResource(id = getBrandAvatarDrawableRes())
    }
    
    Image(
        painter = fallbackPainter,
        contentDescription = "Default Bear Avatar",
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
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
    val context = LocalContext.current
    var showYoutubeDialog by remember { mutableStateOf(false) }

    val openYoutube = {
        showYoutubeDialog = false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/@bearrushgo"))
        context.startActivity(intent)
    }

    if (showYoutubeDialog) {
        AlertDialog(
            onDismissRequest = { showYoutubeDialog = false },
            title = { Text("Cara Download APK", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Untuk mengunduh APK modifikasi, silakan kunjungi dan subscribe channel YouTube kami: Bear Rush Go.\n\n" +
                    "Link download APK selalu kami sediakan di deskripsi video terbaru kami!"
                )
            },
            confirmButton = {
                Button(
                    onClick = { openYoutube() },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Subscribe", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showYoutubeDialog = false }) {
                    Text("Nanti")
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showYoutubeDialog = true }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.geokar),
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.download_sba), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = "Super Bear Adventure Geokar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Button(
                onClick = { showYoutubeDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("APK", fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun SearchBar(searchQuery: String, onSearchQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = searchQuery, onValueChange = onSearchQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        placeholder = { Text(stringResource(R.string.search_hint), color = Color.White.copy(alpha = 0.4f)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.6f)) },
        singleLine = true, shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = Color(0xFF1E1E1E),
            unfocusedContainerColor = Color(0xFF161616),
            focusedBorderColor = Primary,
            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
        )
    )
}

@Composable
fun CategoriesSection(categories: List<Category>, selectedCategory: Category?, onCategorySelected: (Category?) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.categories), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { CategoryChip(name = stringResource(R.string.category_all), isSelected = selectedCategory == null, onClick = { onCategorySelected(null) }) }
            items(categories) { category -> 
                val displayName = when (category.name.lowercase()) {
                    "nature" -> stringResource(R.string.category_nature)
                    "structure", "structur", "bangunan" -> stringResource(R.string.category_structure)
                    else -> category.name
                }
                CategoryChip(name = displayName, isSelected = selectedCategory?.name == category.name, onClick = { onCategorySelected(category) }) 
            }
        }
    }
}

@Composable
fun PopularPresetsHeader(resultCount: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.popular_presets), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Text("$resultCount preset", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
fun PresetsRow(
    presets: List<Preset>,
    downloadStates: MutableMap<Long, Float>,
    downloadManager: DownloadManager?,
    commentsCountMap: Map<Long, Int> = emptyMap(),
    onCardClick: (Preset) -> Unit = {},
    onDownloadClick: (Preset) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        presets.forEach { preset ->
            PresetCard(
                preset = preset, modifier = Modifier.weight(1f),
                isDownloaded = downloadManager?.isDownloaded("${preset.name}.bin") == true,
                downloadProgress = downloadStates[preset.id],
                commentsCount = commentsCountMap[preset.id] ?: 0,
                onCardClick = { onCardClick(preset) },
                onDownloadClick = { onDownloadClick(preset) }
            )
        }
        if (presets.size < 2) Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun GoogleIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "G",
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            color = Color(0xFF4285F4)
        )
    }
}

fun getBrandAvatarForUsername(username: String): Int {
    val lower = username.lowercase(java.util.Locale.ROOT)
    return when {
        lower.contains("xiaomi") || lower.contains("redmi") || lower.contains("poco") -> R.drawable.bear_xiaomi
        lower.contains("samsung") -> R.drawable.bear_samsung
        lower.contains("oppo") -> R.drawable.bear_oppo
        lower.contains("realme") -> R.drawable.bear_realme
        else -> R.drawable.bear_default
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetDetailScreen(
    preset: Preset,
    session: UserSession?,
    profile: UserProfile?,
    supabaseManager: SupabaseManager,
    dataStoreManager: DataStoreManager,
    downloadStates: MutableMap<Long, Float>,
    downloadManager: DownloadManager?,
    ownedPresets: List<UserPresetLog>,
    commentsCountMap: Map<Long, Int>,
    onBack: () -> Unit,
    onRefreshProfile: () -> Unit,
    onLoveIncrement: (Long) -> Unit,
    onCommentAdded: (Long) -> Unit,
    detailViews: Long,
    detailLoves: Long,
    detailDownloads: Long,
    onViewsUpdate: (Long) -> Unit,
    onLovesUpdate: (Long) -> Unit,
    onDownloadsUpdate: (Long) -> Unit,
    confirmDownloadPreset: (Preset) -> Unit,
    presetsCatalog: List<Preset>,
    onPresetClick: (Preset) -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    val shakeOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    var comments by remember { mutableStateOf<List<PresetComment>>(emptyList()) }
    var isFetchingComments by remember { mutableStateOf(false) }
    var commentInput by remember { mutableStateOf("") }
    var isPostingComment by remember { mutableStateOf(false) }
    var isLoved by remember(preset) { mutableStateOf(false) }
    var viewProfileUserId by remember { mutableStateOf<String?>(null) }
    var showCreatorUploadScreen by remember { mutableStateOf(false) }

    var showDoubleTapHeart by remember { mutableStateOf(false) }
    val floatingHearts = remember { mutableStateListOf<FloatingHeart>() }
    var showBadWordDialog by remember { mutableStateOf(false) }
    
    var replyingToComment by remember { mutableStateOf<PresetComment?>(null) }
    var replyingToUsername by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    
    var imageOffset by remember { mutableStateOf(Offset.Zero) }
    var loveIconOffset by remember { mutableStateOf(Offset.Zero) }

    val fetchComments = {
        scope.launch {
            isFetchingComments = true
            comments = supabaseManager.getComments(preset.id, session?.accessToken)
            isFetchingComments = false
        }
    }

    LaunchedEffect(preset) {
        fetchComments()
        scope.launch {
            try {
                supabaseManager.incrementViews(preset.id, preset.views)
                onViewsUpdate(detailViews + 1)
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(replyingToComment) {
        if (replyingToComment != null) {
            focusRequester.requestFocus()
        }
    }

    if (showBadWordDialog) {
        AlertDialog(
            onDismissRequest = { showBadWordDialog = false },
            confirmButton = {
                TextButton(onClick = { showBadWordDialog = false }) {
                    Text("OK", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                }
            },
            title = { Text("Peringatan Bahasa", fontWeight = FontWeight.Bold) },
            text = { Text("Komentar Anda mengandung bahasa kasar atau tidak pantas. Harap berkomentar dengan sopan.") }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
        // Top Instagram Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Kembali",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Preset Details",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // 2. Product Details (non-scrollable)
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Instagram Header: User Avatar & Name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = getBrandAvatarForUsername(preset.author)),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = preset.author.ifBlank { "Creator" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    val catDisplay = when (preset.category.lowercase()) {
                        "nature" -> stringResource(R.string.category_nature)
                        "structure", "structur", "bangunan" -> stringResource(R.string.category_structure)
                        else -> preset.category
                    }
                    Text(
                        text = catDisplay,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Preview Image
            val detailPreviewUrls = remember(preset.preview_url) {
                preset.preview_url.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
            
            val heartScale by animateFloatAsState(
                targetValue = if (showDoubleTapHeart) 1.3f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "heartScale"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .offset(x = shakeOffset.value.dp)
                    .onGloballyPositioned { coords ->
                        imageOffset = coords.positionInRoot()
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { tapOffset ->
                                showDoubleTapHeart = true
                                if (!isLoved) {
                                    isLoved = true
                                    onLovesUpdate(detailLoves + 1)
                                    scope.launch {
                                        try {
                                            supabaseManager.incrementLoves(preset.id, preset.loves)
                                        } catch (_: Exception) {}
                                    }
                                    
                                    // Trigger preview image shake
                                    scope.launch {
                                        repeat(6) {
                                            shakeOffset.animateTo(-8f, androidx.compose.animation.core.tween(50))
                                            shakeOffset.animateTo(8f, androidx.compose.animation.core.tween(50))
                                        }
                                        shakeOffset.animateTo(0f, androidx.compose.animation.core.tween(50))
                                    }
                                }
                                // Trigger TikTok floating hearts burst
                                val startX = with(density) { (imageOffset.x + tapOffset.x).toDp().value }
                                val startY = with(density) { (imageOffset.y + tapOffset.y).toDp().value }
                                spawnHeartsBurst(scope, floatingHearts, startX, startY)

                                // Center heart animation timeout
                                scope.launch {
                                    delay(600)
                                    showDoubleTapHeart = false
                                }
                            }
                        )
                    }
            ) {
                if (detailPreviewUrls.size <= 1) {
                    AsyncImage(
                        model = detailPreviewUrls.firstOrNull() ?: "",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val detailPagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { detailPreviewUrls.size })
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = detailPagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        AsyncImage(
                            model = detailPreviewUrls[page],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repeat(detailPreviewUrls.size) { index ->
                            val dotColor = if (detailPagerState.currentPage == index)
                                Color(0xFFFF9800)
                            else
                                Color.White.copy(alpha = 0.5f)
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }
                    }
                }

                // Centered Instagram Big Double Tap Heart
                if (heartScale > 0.01f) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .scale(heartScale)
                            .size(80.dp)
                    )
                }
            }

            // Stats Interaction Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        isLoved = !isLoved
                        if (isLoved) {
                            onLovesUpdate(detailLoves + 1)
                            scope.launch {
                                try { supabaseManager.incrementLoves(preset.id, preset.loves) } catch (_: Exception) {}
                            }
                            // Trigger floating hearts burst
                            val startX = with(density) { loveIconOffset.x.toDp().value }
                            val startY = with(density) { loveIconOffset.y.toDp().value }
                            spawnHeartsBurst(scope, floatingHearts, startX, startY)
                             
                            // Trigger preview image shake
                            scope.launch {
                                repeat(6) {
                                    shakeOffset.animateTo(-8f, androidx.compose.animation.core.tween(50))
                                    shakeOffset.animateTo(8f, androidx.compose.animation.core.tween(50))
                                }
                                shakeOffset.animateTo(0f, androidx.compose.animation.core.tween(50))
                            }
                        } else {
                            onLovesUpdate((detailLoves - 1).coerceAtLeast(0))
                        }
                    },
                    modifier = Modifier.onGloballyPositioned { coords ->
                        loveIconOffset = coords.positionInRoot()
                    }
                ) {
                    Icon(
                        imageVector = if (isLoved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Love",
                        tint = if (isLoved) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.Comment,
                    contentDescription = "Comments",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${comments.size}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.weight(1f))

                // Views Stats
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Views",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$detailViews",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Downloads Stats
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Downloads",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$detailDownloads",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Likes Count Caption
            Text(
                text = "$detailLoves likes",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            // Description
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(preset.author.ifBlank { "Creator" })
                            append(" ")
                        }
                        append(preset.description.ifEmpty { stringResource(R.string.no_description) })
                    },
                    fontSize = 14.sp,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // CTA Download Button
            val isAlreadyOwned = ownedPresets.any { it.presetId == preset.id }
            val isPremium = !preset.is_free
            val downloadProgress = downloadStates[preset.id]
            val isDownloaded = downloadManager?.isDownloaded("${preset.name}.bin") == true

            Button(
                onClick = { confirmDownloadPreset(preset) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDownloaded)
                        Color(0xFF8E8E93)
                    else
                        Color(0xFFFF9800)
                ),
                enabled = downloadProgress == null
            ) {
                if (isDownloaded) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tersimpan",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                } else if (downloadProgress != null && downloadProgress in 0f..1f) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val btnText = if (session == null) {
                        "Login untuk Mengunduh"
                    } else if (isAlreadyOwned) {
                        "Milik Anda"
                    } else if (preset.price > 0) {
                        "${preset.price} Koin"
                    } else {
                        "FREE"
                    }
                    Text(
                        text = btnText,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
        }

        // 3. Comments Section (scrollable)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Comments Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.discussion, comments.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { fetchComments() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Komentar",
                            tint = Color(0xFFFF9800)
                        )
                    }
                }
            }

            // Comments List (Threaded)
            if (isFetchingComments) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFFF9800),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } else if (comments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_comments),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val parentComments = comments.filter { it.parentCommentId == null }.sortedBy { it.id ?: 0L }
                val repliesMap = comments.filter { it.parentCommentId != null }.groupBy { it.parentCommentId }

                parentComments.forEach { parent ->
                    item {
                        CommentRowItem(
                            comment = parent,
                            onReplyClick = {
                                replyingToComment = parent
                                replyingToUsername = parent.displayUsername.replace(" ", "").lowercase()
                                commentInput = ""
                            },
                            onUserClick = { clickedUserId ->
                                android.widget.Toast.makeText(context, "Membuka profil pembuat...", android.widget.Toast.LENGTH_SHORT).show()
                                viewProfileUserId = clickedUserId
                            }
                        )
                    }
                    val replies = (repliesMap[parent.id] ?: emptyList()).sortedBy { it.id ?: 0L }
                    replies.forEach { reply ->
                        item {
                            CommentRowItem(
                                comment = reply,
                                isReply = true,
                                onReplyClick = {
                                    replyingToComment = parent
                                    replyingToUsername = reply.displayUsername.replace(" ", "").lowercase()
                                    commentInput = ""
                                },
                                onUserClick = { clickedUserId ->
                                    android.widget.Toast.makeText(context, "Membuka profil pembuat...", android.widget.Toast.LENGTH_SHORT).show()
                                    viewProfileUserId = clickedUserId
                                }
                            )
                        }
                    }
                }
            }
        }

        // Input Field Bar at bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
        ) {
            replyingToComment?.let { replyComment ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Membalas @$replyingToUsername",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = {
                            replyingToComment = null
                            replyingToUsername = ""
                        },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Batal Balas",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = session == null) {
                            onNavigateToProfile()
                        }
                ) {
                    OutlinedTextField(
                        value = commentInput,
                        onValueChange = { commentInput = it },
                        placeholder = { Text(stringResource(R.string.write_comment), fontSize = 13.sp) },
                        enabled = session != null,
                        prefix = {
                            if (replyingToComment != null && replyingToUsername.isNotEmpty()) {
                                Text(
                                    text = "@$replyingToUsername ",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        maxLines = 3,
                        singleLine = false,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF9800),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        if (session == null) {
                            onNavigateToProfile()
                        } else {
                            val trimmed = commentInput.trim()
                            if (trimmed.isNotEmpty()) {
                                if (containsBadWords(trimmed)) {
                                    showBadWordDialog = true
                                    commentInput = ""
                                    replyingToComment = null
                                    return@IconButton
                                }
                                scope.launch {
                                    isPostingComment = true
                                    try {
                                        runWithTokenRefresh(session, supabaseManager, dataStoreManager) { token ->
                                            val finalComment = if (replyingToComment != null && replyingToUsername.isNotEmpty()) {
                                                "@$replyingToUsername $trimmed"
                                            } else {
                                                trimmed
                                            }
                                            val newComment = PresetComment(
                                                presetId = preset.id,
                                                userId = session?.userId,
                                                username = session?.username ?: "Guest",
                                                comment = finalComment,
                                                parentCommentId = replyingToComment?.id
                                            )
                                            supabaseManager.postComment(
                                                comment = newComment,
                                                token = token
                                            )
                                        }
                                        commentInput = ""
                                        replyingToComment = null
                                        replyingToUsername = ""
                                        fetchComments()
                                        onCommentAdded(preset.id)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Komentar gagal terkirim: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isPostingComment = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = session == null || (!isPostingComment && commentInput.trim().isNotEmpty())
                ) {
                    if (isPostingComment) {
                        CircularProgressIndicator(color = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.Send, contentDescription = "Kirim", tint = Color(0xFFFF9800))
                    }
                }
            }
        }
    }
    
    // Floating Hearts Overlay covering the entire screen
    FloatingHearts(
        hearts = floatingHearts,
        onRemoveHeart = { id ->
            floatingHearts.removeAll { it.id == id }
        }
    )

    if (viewProfileUserId != null) {
        BackHandler(onBack = { viewProfileUserId = null })
        UserProfileDetailScreen(
            userId = viewProfileUserId!!,
            currentUserId = session?.userId,
            token = session?.accessToken,
            supabaseManager = supabaseManager,
            presetsCatalog = presetsCatalog,
            onPresetClick = onPresetClick,
            onBack = { viewProfileUserId = null },
            onCreatorClick = { showCreatorUploadScreen = true }
        )
    }

    if (showCreatorUploadScreen) {
        BackHandler(onBack = { showCreatorUploadScreen = false })
        CreatorUploadScreen(
            currentUserId = session?.userId ?: "",
            token = session?.accessToken ?: "",
            username = profile?.username ?: session?.username ?: "",
            supabaseManager = supabaseManager,
            onBack = { showCreatorUploadScreen = false }
        )
    }
}
}
}

@Composable
fun CommentRowItem(
    comment: PresetComment,
    isReply: Boolean = false,
    onReplyClick: () -> Unit,
    onUserClick: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isReply) 52.dp else 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(if (isReply) 28.dp else 34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    val uid = comment.userId
                    if (!uid.isNullOrEmpty()) {
                        onUserClick(uid)
                    } else {
                        android.widget.Toast.makeText(context, "ID Pengguna Kosong (Komentar Tamu/Guest)!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = getBrandAvatarForUsername(comment.displayUsername)),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val commentUser = comment.displayUsername.lowercase()
                val displayHandle = if (commentUser.startsWith("@")) commentUser else "@$commentUser"
                Text(
                    text = displayHandle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF9800),
                    modifier = Modifier.clickable {
                        val uid = comment.userId
                        if (!uid.isNullOrEmpty()) {
                            onUserClick(uid)
                        } else {
                            android.widget.Toast.makeText(context, "ID Pengguna Kosong (Komentar Tamu/Guest)!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                val cleanDate = comment.createdAt?.split("T")?.firstOrNull() ?: ""
                Text(
                    text = cleanDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            
            Spacer(modifier = Modifier.height(2.dp))
            
            val commentText = comment.comment
            val annotatedComment = remember(commentText) {
                buildAnnotatedString {
                    if (commentText.startsWith("@")) {
                        val firstSpace = commentText.indexOf(' ')
                        if (firstSpace != -1) {
                            val mention = commentText.substring(0, firstSpace)
                            val rest = commentText.substring(firstSpace)
                            withStyle(style = SpanStyle(color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)) {
                                append(mention)
                            }
                            append(rest)
                        } else {
                            append(commentText)
                        }
                    } else {
                        append(commentText)
                    }
                }
            }
            Text(
                text = annotatedComment,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Balas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.clickable { onReplyClick() }
                )
            }
        }
    }
}

suspend fun <T> runWithTokenRefresh(
    session: UserSession?,
    supabaseManager: SupabaseManager,
    dataStoreManager: DataStoreManager,
    block: suspend (String) -> T
): T {
    val currentSession = session ?: throw Exception("Sesi kosong, silakan login terlebih dahulu.")
    try {
        return block(currentSession.accessToken)
    } catch (e: Exception) {
        val isJwtExpired = e.message?.contains("JWT expired", ignoreCase = true) == true
        if (isJwtExpired) {
            if (currentSession.refreshToken.isNotEmpty()) {
                try {
                    val refreshResp = supabaseManager.refreshToken(currentSession.refreshToken)
                    val newAccessToken = refreshResp.accessToken
                    val newRefreshToken = refreshResp.refreshToken
                    if (newAccessToken != null) {
                        val updatedSession = currentSession.copy(
                            accessToken = newAccessToken,
                            refreshToken = newRefreshToken ?: currentSession.refreshToken
                        )
                        dataStoreManager.saveUserSession(updatedSession)
                        return block(newAccessToken)
                    }
                } catch (refreshEx: Exception) {
                    dataStoreManager.clearUserSession()
                    throw Exception("Sesi Anda telah berakhir, silakan login kembali.", refreshEx)
                }
            } else {
                // Sesi lama sebelum update tidak punya refresh_token, hapus agar user diarahkan login ulang
                dataStoreManager.clearUserSession()
                throw Exception("Sesi Anda telah berakhir, silakan login kembali.")
            }
        }
        throw e
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    session: UserSession?,
    dataStoreManager: DataStoreManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDevModePopup by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Pengaturan",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // ponytail: Mode switch dihapus karena aplikasi dipaksa menggunakan mode gelap default
            if (session == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tidak ada pengaturan yang tersedia saat ini.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // Menu Developer Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDevModePopup = true }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeveloperMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Menu Developer",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 1.dp)

                        // Keluar Sesi Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        dataStoreManager.clearUserSession()
                                        Toast.makeText(context, "Keluar dari sesi berhasil.", Toast.LENGTH_SHORT).show()
                                        onBack() // Go back to profile screen after logging out
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = null,
                                tint = Color.Red
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Keluar Sesi",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color.Red
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDevModePopup) {
        AlertDialog(
            onDismissRequest = { showDevModePopup = false },
            confirmButton = {
                Button(onClick = { showDevModePopup = false }) { Text("OK", color = Color.White) }
            },
            title = { Text("Fitur Dalam Pengembangan", fontWeight = FontWeight.Bold) },
            text = {
                Text("Fitur Menu Developer sedang dalam pengembangan dan akan hadir di update selanjutnya!", textAlign = TextAlign.Center)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileDetailScreen(
    userId: String,
    currentUserId: String?,
    token: String?,
    supabaseManager: SupabaseManager,
    presetsCatalog: List<Preset>,
    onPresetClick: (Preset) -> Unit,
    onBack: () -> Unit,
    onCreatorClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var targetProfile by remember { mutableStateOf<UserProfile?>(null) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var followStats by remember { mutableStateOf<Pair<Int, Int>>(0 to 0) }
    var isFollowingState by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(userId) {
        isLoading = true
        errorMessage = null
        try {
            val loadedProfile = supabaseManager.getProfile(userId, token ?: "")
            targetProfile = loadedProfile
            
            val stats = supabaseManager.getFollowStats(userId, token ?: "")
            followStats = stats
            
            val following = if (currentUserId != null && token != null) {
                supabaseManager.isFollowing(followerId = currentUserId, followingId = userId, token = token)
            } else false
            isFollowingState = following
        } catch (e: Exception) {
            errorMessage = e.localizedMessage
        } finally {
            isLoading = false
        }
    }

    fun toggleFollow() {
        if (currentUserId == null || token == null) {
            Toast.makeText(context, "Silakan login terlebih dahulu untuk mengikuti!", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                try {
                    if (isFollowingState) {
                        supabaseManager.unfollowUser(followerId = currentUserId, followingId = userId, token = token)
                        isFollowingState = false
                        followStats = Pair((followStats.first - 1).coerceAtLeast(0), followStats.second)
                    } else {
                        supabaseManager.followUser(followerId = currentUserId, followingId = userId, token = token)
                        isFollowingState = true
                        followStats = Pair(followStats.first + 1, followStats.second)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Gagal memperbarui status ikuti: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val infiniteBgTransition = rememberInfiniteTransition(label = "detail_bg_slide")
        val bgSlide by infiniteBgTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(12000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "bg_slide_detail"
        )
        
        val patternPainter = painterResource(id = R.drawable.gaming_pattern_bg)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.5f)
                .drawBehind {
                    val tileHeightPx = 400.dp.toPx()
                    val intrinsicSize = patternPainter.intrinsicSize
                    val aspectRatio = if (intrinsicSize.height > 0) intrinsicSize.width / intrinsicSize.height else 1f
                    val tileWidthPx = tileHeightPx * aspectRatio
                    
                    val slideOffsetX = bgSlide * tileWidthPx
                    val slideOffsetY = bgSlide * tileHeightPx
                    
                    val startX = -tileWidthPx * 2 + slideOffsetX
                    val startY = -tileHeightPx * 2 + slideOffsetY
                    
                    var x = startX
                    while (x < size.width) {
                        var y = startY
                        while (y < size.height) {
                            translate(left = x, top = y) {
                                with(patternPainter) {
                                    draw(size = androidx.compose.ui.geometry.Size(tileWidthPx, tileHeightPx))
                                }
                            }
                            y += tileHeightPx
                        }
                        x += tileWidthPx
                    }
                }
        )
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFF9800))
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Gagal memuat profil: $errorMessage", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Kembali") }
                }
            }
        } else {
            val profile = targetProfile
            val createdPresets = remember(presetsCatalog, profile) {
                presetsCatalog.filter { 
                    val profileUser = (profile?.username ?: "").replace("@", "")
                    it.author.replace("@", "").equals(profileUser, ignoreCase = true) 
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Header (Spans 2 columns)
                item(span = { GridItemSpan(2) }) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Banner & Avatar Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.9f)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.bg_welcome_header),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2.4f)
                                    .align(Alignment.TopCenter)
                            )
                            
                            // Close Button at top-left
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                                    .size(36.dp)
                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Tutup",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            
                            // Avatar overlapping
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .align(Alignment.BottomCenter)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(3.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                RenderUserAvatar(
                                    avatarUrl = profile?.avatarUrl ?: "",
                                    fallbackUsername = profile?.username ?: "Guest",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // User Info (Name, Username, Bio, Stats)
                        Text(
                            text = profile?.nickname?.ifEmpty { profile.username } ?: profile?.username ?: "Guest",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        val rawDetailUsername = profile?.username ?: "guest"
                        val displayDetailHandle = rawDetailUsername.lowercase().let { if (it.startsWith("@")) it else "@$it" }
                        Text(
                            text = displayDetailHandle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        // Follower count & Coins
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${followStats.first} pengikut • ${followStats.second} mengikuti",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.MonetizationOn,
                                contentDescription = "Koin",
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${profile?.coins ?: 100}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Text(
                            text = profile?.bio?.ifEmpty { "Belum ada bio singkat." }
                                ?: "Belum ada bio singkat.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )

                        // Follow/Edit Button Row
                        if (currentUserId != null) {
                            Row(
                                modifier = Modifier.padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (currentUserId == userId) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                                    ) {
                                        Button(
                                            onClick = { showEditProfileDialog = true },
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                            shape = RoundedCornerShape(24.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text(
                                                text = "Edit Profile",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }

                                        Button(
                                            onClick = onCreatorClick,
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFFF9800)
                                            ),
                                            shape = RoundedCornerShape(24.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text(
                                                text = "Creator",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { toggleFollow() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isFollowingState) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFFF9800)
                                        ),
                                        shape = RoundedCornerShape(24.dp),
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = if (isFollowingState) "Mengikuti" else "Ikuti",
                                            color = if (isFollowingState) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Title header for posts
                item(span = { GridItemSpan(2) }) {
                    Text(
                        text = "Preset yang Dibuat",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }

                // Grid Items
                if (createdPresets.isEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Belum ada preset yang dibuat.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    items(createdPresets) { preset ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPresetClick(preset) }
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                val firstUrl = remember(preset.preview_url) {
                                    preset.preview_url.split(",").firstOrNull()?.trim() ?: ""
                                }
                                if (firstUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = firstUrl,
                                        contentDescription = preset.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = preset.name.firstOrNull()?.toString() ?: "",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                Surface(
                                    shape = RoundedCornerShape(bottomStart = 8.dp),
                                    color = if (preset.price == 0L) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Text(
                                        text = if (preset.price > 0) "${preset.price} Koin" else "FREE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        
        if (showEditProfileDialog && targetProfile != null && token != null) {
            val dataStoreManager = remember { com.bearrushbuilder.app.data.DataStoreManager(context) }
            var editNickname by remember { mutableStateOf(targetProfile?.nickname?.ifEmpty { targetProfile?.username ?: "" } ?: "") }
            var editUsername by remember { mutableStateOf(targetProfile?.username?.lowercase()?.let { if (it.startsWith("@")) it else "@$it" } ?: "") }
            var editBio by remember { mutableStateOf(targetProfile?.bio ?: "") }
            var editAvatar by remember { mutableStateOf(targetProfile?.avatarUrl ?: "") }
            var isUpdatingProfile by remember { mutableStateOf(false) }
            
            var isUsernameTakenState by remember { mutableStateOf(false) }
            var suggestedUsernames by remember { mutableStateOf<List<String>>(emptyList()) }
            var isCheckingUsername by remember { mutableStateOf(false) }

            LaunchedEffect(editUsername) {
                val trimmed = editUsername.trim().lowercase().replace(" ", "")
                val currentUsernameClean = (targetProfile?.username ?: "").lowercase().replace(" ", "")
                if (trimmed.length <= 1 || trimmed == currentUsernameClean) {
                    isUsernameTakenState = false
                    suggestedUsernames = emptyList()
                    return@LaunchedEffect
                }
                delay(500)
                isCheckingUsername = true
                try {
                    val taken = supabaseManager.isUsernameTaken(trimmed, currentUserId)
                    isUsernameTakenState = taken
                    if (taken) {
                        val random = java.util.Random()
                        val suggestions = mutableListOf<String>()
                        var attempts = 0
                        while (suggestions.size < 3 && attempts < 20) {
                            attempts++
                            val suffix = when (random.nextInt(3)) {
                                0 -> "${random.nextInt(900) + 100}"
                                1 -> "_bear"
                                else -> "mod${random.nextInt(90) + 10}"
                            }
                            val candidate = trimmed + suffix
                            if (!supabaseManager.isUsernameTaken(candidate, currentUserId) && !suggestions.contains(candidate)) {
                                suggestions.add(candidate)
                            }
                        }
                        suggestedUsernames = suggestions
                    } else {
                        suggestedUsernames = emptyList()
                    }
                } catch (_: Exception) {
                } finally {
                    isCheckingUsername = false
                }
            }

            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showEditProfileDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1E1E1E),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Edit Profile",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color.White
                        )

                        OutlinedTextField(
                            value = editNickname,
                            onValueChange = { editNickname = it },
                            label = { Text("Nickname (Nama Tampilan)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFF9800),
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedLabelColor = Color(0xFFFF9800),
                                unfocusedLabelColor = Color.Gray
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = editUsername,
                            onValueChange = { input ->
                                val cleaned = input.lowercase().replace(" ", "")
                                editUsername = if (cleaned.startsWith("@")) {
                                    if (cleaned.length == 1) "@" else cleaned
                                } else {
                                    "@$cleaned"
                                }
                            },
                            label = { Text("Username (Format: @handle)") },
                            isError = isUsernameTakenState,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFF9800),
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedLabelColor = Color(0xFFFF9800),
                                unfocusedLabelColor = Color.Gray,
                                errorBorderColor = Color(0xFFFF5252),
                                errorLabelColor = Color(0xFFFF5252)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (isCheckingUsername) {
                            Text("Memeriksa ketersediaan handle...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        } else if (isUsernameTakenState) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Handle $editUsername sudah digunakan orang lain.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF5252))
                                if (suggestedUsernames.isNotEmpty()) {
                                    Text("Opsi yang tersedia:", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        suggestedUsernames.forEach { suggestion ->
                                            AssistChip(
                                                onClick = { editUsername = suggestion },
                                                label = { Text(suggestion, color = Color.White, fontSize = 11.sp) },
                                                colors = AssistChipDefaults.assistChipColors(
                                                    containerColor = Color(0xFF2E2E2E)
                                                ),
                                                border = null
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = editBio,
                            onValueChange = { editBio = it },
                            label = { Text("Biografi Pendek") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            singleLine = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFF9800),
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedLabelColor = Color(0xFFFF9800),
                                unfocusedLabelColor = Color.Gray
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Text("Pilih Icon Profile", fontWeight = FontWeight.SemiBold, color = Color.White)
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val icons = listOf("bear_avatar_1", "bear_avatar_2", "bear_avatar_3", "bear_avatar_4")
                            icons.forEach { iconName ->
                                val isSelected = editAvatar == iconName
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color(0xFFFF9800) else Color(0xFF2E2E2E))
                                        .clickable { editAvatar = iconName }
                                        .padding(if (isSelected) 3.dp else 0.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E1E1E)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = getBrandAvatarForUsername(iconName)),
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showEditProfileDialog = false },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.LightGray)
                            ) {
                                Text("Batal", color = Color.LightGray)
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    val finalUsername = editUsername.trim().lowercase().replace(" ", "")
                                    val finalNickname = editNickname.trim()
                                    if (finalUsername.length <= 1 || finalNickname.isBlank()) return@Button
                                    scope.launch {
                                        isUpdatingProfile = true
                                        try {
                                            val updated = supabaseManager.updateProfile(
                                                userId = userId,
                                                token = token,
                                                username = finalUsername,
                                                nickname = finalNickname,
                                                bio = editBio.trim(),
                                                avatarUrl = editAvatar.trim()
                                            )
                                            val currentSession = dataStoreManager.userSession.firstOrNull()
                                            dataStoreManager.saveUserSession(
                                                UserSession(
                                                    userId = userId,
                                                    email = currentSession?.email ?: "",
                                                    username = updated.username,
                                                    accessToken = token,
                                                    refreshToken = currentSession?.refreshToken ?: "",
                                                    coins = updated.coins
                                                )
                                            )
                                            // Reload profile on detail screen
                                            val loadedProfile = supabaseManager.getProfile(userId, token)
                                            targetProfile = loadedProfile
                                            showEditProfileDialog = false
                                            Toast.makeText(context, "Profil berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Gagal mengupdate profil: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isUpdatingProfile = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF9800),
                                    disabledContainerColor = Color(0xFFFF9800).copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isUpdatingProfile && !isCheckingUsername && !isUsernameTakenState && editUsername.isNotBlank() && editNickname.isNotBlank()
                            ) {
                                if (isUpdatingProfile) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Simpan", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreatorUploadScreen(
    currentUserId: String,
    token: String,
    username: String,
    supabaseManager: SupabaseManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Nature") }
    var isFree by remember { mutableStateOf(true) }
    var priceString by remember { mutableStateOf("0") }
    var youtubeUrl by remember { mutableStateOf("") }

    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedFileSize by remember { mutableStateOf(0L) }

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    var isUploading by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            val (fName, fSize) = getFileNameAndSize(context, uri)
            selectedFileName = fName
            selectedFileSize = fSize
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        val infiniteBgTransition = rememberInfiniteTransition(label = "upload_bg_slide")
        val bgSlide by infiniteBgTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(12000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "bg_slide_upload"
        )
        
        val patternPainter = painterResource(id = R.drawable.gaming_pattern_bg)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.3f)
                .drawBehind {
                    val tileHeightPx = 400.dp.toPx()
                    val intrinsicSize = patternPainter.intrinsicSize
                    val aspectRatio = if (intrinsicSize.height > 0) intrinsicSize.width / intrinsicSize.height else 1f
                    val tileWidthPx = tileHeightPx * aspectRatio
                    
                    val slideOffsetX = bgSlide * tileWidthPx
                    val slideOffsetY = bgSlide * tileHeightPx
                    
                    val startX = -tileWidthPx * 2 + slideOffsetX
                    val startY = -tileHeightPx * 2 + slideOffsetY
                    
                    var x = startX
                    while (x < size.width) {
                        var y = startY
                        while (y < size.height) {
                            translate(left = x, top = y) {
                                with(patternPainter) {
                                    draw(size = androidx.compose.ui.geometry.Size(tileWidthPx, tileHeightPx))
                                }
                            }
                            y += tileHeightPx
                        }
                        x += tileWidthPx
                    }
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Kembali",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Unggah Preset Baru",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Bagikan preset kreasi terbaikmu",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "1. Berkas Preset (.bin)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White
                )

                Surface(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, if (selectedFileUri != null) Color(0xFF4CAF50) else Color.DarkGray)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (selectedFileUri != null) Icons.Default.CheckCircle else Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = if (selectedFileUri != null) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (selectedFileUri != null) {
                                Text(
                                    text = selectedFileName,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Ukuran: ${String.format("%.2f", selectedFileSize / 1024.0)} KB",
                                    fontSize = 12.sp,
                                    color = Color.LightGray
                                )
                            } else {
                                Text(
                                    text = "Pilih berkas preset (.bin)",
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Format file bin builder Bear Rush",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                        if (selectedFileUri != null) {
                            TextButton(
                                onClick = { filePickerLauncher.launch("*/*") }
                            ) {
                                Text("Ganti", color = Color(0xFFFF9800))
                            }
                        }
                    }
                }

                Text(
                    text = "2. Gambar Preview",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White
                )

                Surface(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, if (selectedImageUri != null) Color(0xFF4CAF50) else Color.DarkGray)
                ) {
                    if (selectedImageUri != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Preview Gambar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                                        )
                                    )
                            )
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Terpilih", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                                ) {
                                    Text("Ganti", color = Color(0xFFFF9800), fontSize = 11.sp)
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Pilih Gambar Preview",
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Gunakan aspek rasio 16:9 (Landscape) agar maksimal",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }

                Text(
                    text = "3. Informasi Detail",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Preset") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF9800),
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedLabelColor = Color(0xFFFF9800),
                        unfocusedLabelColor = Color.Gray
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Deskripsi Singkat") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    singleLine = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF9800),
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedLabelColor = Color(0xFFFF9800),
                        unfocusedLabelColor = Color.Gray
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Kategori", fontSize = 12.sp, color = Color.Gray)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val categories = listOf("Nature", "Structure", "Gameplay")
                        categories.forEach { cat ->
                            val isSelected = selectedCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(if (isSelected) Color(0xFFFF9800) else Color(0xFF1E1E1E))
                                    .border(1.dp, if (isSelected) Color.Transparent else Color.DarkGray, RoundedCornerShape(24.dp))
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) Color.White else Color.LightGray,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tipe Lisensi", fontSize = 12.sp, color = Color.Gray)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isFree) Color(0xFFFF9800).copy(alpha = 0.15f) else Color(0xFF1E1E1E))
                                .border(1.dp, if (isFree) Color(0xFFFF9800) else Color.DarkGray, RoundedCornerShape(12.dp))
                                .clickable { isFree = true }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("GRATIS", color = if (isFree) Color(0xFFFF9800) else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Siapa saja bisa download", color = Color.Gray, fontSize = 10.sp)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (!isFree) Color(0xFFFF9800).copy(alpha = 0.15f) else Color(0xFF1E1E1E))
                                .border(1.dp, if (!isFree) Color(0xFFFF9800) else Color.DarkGray, RoundedCornerShape(12.dp))
                                .clickable { isFree = false }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("PREMIUM", color = if (!isFree) Color(0xFFFF9800) else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Tentukan harga koin", color = Color.Gray, fontSize = 10.sp)
                            }
                        }
                    }
                }

                if (!isFree) {
                    OutlinedTextField(
                        value = priceString,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                priceString = input
                            }
                        },
                        label = { Text("Harga Koin") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.MonetizationOn,
                                contentDescription = null,
                                tint = Color(0xFFFFB300)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF9800),
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.LightGray,
                            focusedLabelColor = Color(0xFFFF9800),
                            unfocusedLabelColor = Color.Gray
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = youtubeUrl,
                    onValueChange = { youtubeUrl = it },
                    label = { Text("URL Video YouTube (Opsional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF9800),
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedLabelColor = Color(0xFFFF9800),
                        unfocusedLabelColor = Color.Gray
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    val fileUri = selectedFileUri
                    val imageUri = selectedImageUri
                    if (name.isBlank() || description.isBlank() || fileUri == null || imageUri == null) {
                        Toast.makeText(context, "Harap lengkapi semua data wajib!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        isUploading = true
                        try {
                            val fileBytes = getBytesFromUri(context, fileUri)
                                ?: throw Exception("Gagal membaca file preset")
                            val imageBytes = getBytesFromUri(context, imageUri)
                                ?: throw Exception("Gagal membaca gambar preview")

                            val uniqueId = java.util.UUID.randomUUID().toString()
                            val fileExtension = selectedFileName.substringAfterLast('.', "bin")
                            val remoteFilePath = "files/${uniqueId}.${fileExtension}"
                            val remoteImagePath = "previews/${uniqueId}.jpg"

                            // Upload preset file
                            val downloadUrl = supabaseManager.uploadStorageFile(
                                bucket = "presets",
                                path = remoteFilePath,
                                bytes = fileBytes,
                                mimeType = "application/octet-stream",
                                token = token
                            )

                            // Upload preview image
                            val previewUrl = supabaseManager.uploadStorageFile(
                                bucket = "presets",
                                path = remoteImagePath,
                                bytes = imageBytes,
                                mimeType = "image/jpeg",
                                token = token
                            )

                            // Insert Preset into Database Table
                            val insertRequest = com.bearrushbuilder.app.data.PresetInsertRequest(
                                name = name,
                                description = description,
                                category = selectedCategory,
                                previewUrl = previewUrl,
                                downloadUrl = downloadUrl,
                                isFree = isFree,
                                price = priceString.toLongOrNull() ?: 0L,
                                author = username,
                                youtubeUrl = youtubeUrl
                            )
                            supabaseManager.insertPreset(insertRequest, token)

                            Toast.makeText(context, "Preset berhasil diunggah!", Toast.LENGTH_SHORT).show()
                            onBack()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Gagal mengunggah: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        } finally {
                            isUploading = false
                        }
                    }
                },
                enabled = !isUploading && name.isNotBlank() && description.isNotBlank() && selectedFileUri != null && selectedImageUri != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9800),
                    disabledContainerColor = Color(0xFFFF9800).copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Unggah Preset",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

fun getFileNameAndSize(context: android.content.Context, uri: Uri): Pair<String, Long> {
    var name = "Unknown"
    var size = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex)
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }
    } catch (_: Exception) {}
    return name to size
}

fun getBytesFromUri(context: android.content.Context, uri: Uri): ByteArray? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.readBytes()
        }
    } catch (e: Exception) {
        null
    }
}