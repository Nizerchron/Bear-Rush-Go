package com.bearrushmod.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.bearrushmod.R
import com.bearrushmod.data.*
import com.bearrushmod.model.Category
import com.bearrushmod.model.Preset
import com.bearrushmod.ui.components.CategoryChip
import com.bearrushmod.ui.components.PresetCard
import com.bearrushmod.ui.components.scaleOnPress
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    darkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    downloadManager: DownloadManager? = null,
    dataStoreManager: DataStoreManager,
    supabaseManager: SupabaseManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(ScreenTab.CATALOG) }

    // Supabase Auth and User States
    val session by dataStoreManager.userSession.collectAsState(initial = null)
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var ownedPresets by remember { mutableStateOf<List<UserPresetLog>>(emptyList()) }
    var isLoadingProfile by remember { mutableStateOf(false) }

    // Download flow states
    val downloadStates = remember { mutableStateMapOf<Long, Float>() }
    var searchQuery by remember { mutableStateOf("") }
    var detailPreset by remember { mutableStateOf<Preset?>(null) }
    var confirmDownloadPreset by remember { mutableStateOf<Preset?>(null) }

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

    LaunchedEffect(presets) {
        commentsCountMap = supabaseManager.getAllCommentsCountMap()
    }

    // Sync profile and downloaded presets when user session changes
    LaunchedEffect(session) {
        val currentSession = session
        if (currentSession != null) {
            isLoadingProfile = true
            try {
                profile = supabaseManager.getProfile(currentSession.userId, currentSession.accessToken, currentSession.email, currentSession.username)
                ownedPresets = supabaseManager.getOwnedPresets(currentSession.userId, currentSession.accessToken)
            } catch (e: Exception) {
                if (e.message?.contains("Jwt is expired", ignoreCase = true) == true) {
                    dataStoreManager.clearUserSession()
                    profile = null
                }
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
                        val freshProfile = supabaseManager.getProfile(session!!.userId, token, session!!.email, session!!.username)
                        profile = freshProfile
                        dataStoreManager.updateCoins(freshProfile.coins)
                        ownedPresets = supabaseManager.getOwnedPresets(session!!.userId, token)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Sinkronisasi gagal: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val filteredPresets = remember(presets, selectedCategory, searchQuery) {
        presets
            .filter { selectedCategory == null || it.category == selectedCategory.name }
            .filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    if (detailPreset != null) {
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
            confirmDownloadPreset = { confirmDownloadPreset = it }
        )
    } else {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
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
        },
        floatingActionButton = {
            val hapticFeedback = LocalHapticFeedback.current
            val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

            SmallFloatingActionButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
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
                        onDownloadClick = { preset ->
                            if (downloadManager != null && downloadStates[preset.id] == null) {
                                // Validasi koin & auth jika premium
                                val isOwned = ownedPresets.any { it.presetId == preset.id }
                                var canProceed = true
                                if (!preset.is_free && !isOwned) {
                                    val currentSession = session
                                    if (currentSession == null) {
                                        Toast.makeText(context, "Silakan login di tab Profil untuk mengunduh preset premium!", Toast.LENGTH_LONG).show()
                                        currentTab = ScreenTab.PROFILE
                                        canProceed = false
                                    } else {
                                        val userCoins = profile?.coins ?: 0
                                        if (userCoins < 10) {
                                            Toast.makeText(context, "Koin tidak cukup (butuh 10 koin). Silakan isi ulang di Toko Koin!", Toast.LENGTH_LONG).show()
                                            currentTab = ScreenTab.SHOP
                                            canProceed = false
                                        }
                                    }
                                }
                                if (canProceed) {
                                    confirmDownloadPreset = preset
                                }
                            }
                        }
                    )
                }
                ScreenTab.SHOP -> {
                    ShopTab(
                        session = session,
                        profile = profile,
                        supabaseManager = supabaseManager,
                        dataStoreManager = dataStoreManager,
                        onRefreshProfile = refreshProfileData,
                        onNavigateToProfile = { currentTab = ScreenTab.PROFILE }
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
                        onPresetClick = { preset -> detailPreset = preset }
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
                        // Tonton iklan (ads wajib sebelum unduh)
                        AdsManager.showInterstitial(context as Activity,
                            onCompleted = {
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
                                                    scope.launch {
                                                        try {
                                                            // Increment downloads count in Supabase DB
                                                            supabaseManager.incrementDownloads(p.id, p.downloads)
                                                            if (detailPreset?.id == p.id) {
                                                                detailDownloads += 1
                                                            }

                                                            // Log download ke database Supabase
                                                            val currentSession = session
                                                            if (currentSession != null) {
                                                                supabaseManager.logDownload(
                                                                    token = currentSession.accessToken,
                                                                    log = UserPresetLog(
                                                                        userId = currentSession.userId,
                                                                        presetId = p.id,
                                                                        presetName = p.name,
                                                                        presetPreviewUrl = p.preview_url,
                                                                        presetCategory = p.category
                                                                    )
                                                                )
                                                                // Potong koin jika premium dan belum dimiliki sebelumnya
                                                                if (isPremium && !isAlreadyOwned) {
                                                                    val newCoins = (profile?.coins ?: 10) - 10
                                                                    supabaseManager.updateCoins(
                                                                        userId = currentSession.userId,
                                                                        token = currentSession.accessToken,
                                                                        newCoins = newCoins
                                                                    )
                                                                    dataStoreManager.updateCoins(newCoins)
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
                            },
                            onSkipped = {
                                downloadStates[p.id] = Float.POSITIVE_INFINITY
                                Toast.makeText(context, "Iklan harus ditonton sampai selesai untuk mengunduh!", Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
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
                    if (isPremium && !isAlreadyOwned) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Mod ini adalah Premium dan akan memotong 10 Koin milik Anda.",
                            color = Color(0xFFFF9800),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Setelah konfirmasi, tonton iklan sampai selesai ya 😊\n\n" +
                               "File disimpan di folder:\n" +
                               "📁 Geokar_Mods/SBA/saved_scenes/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
    Column(modifier = Modifier.fillMaxSize()) {
        WelcomeHeader()
        Spacer(modifier = Modifier.height(12.dp))
        DownloadSbaButton(downloadManager = downloadManager)
        Spacer(modifier = Modifier.height(12.dp))
        SearchBar(searchQuery = searchQuery, onSearchQueryChange = onSearchQueryChange)
        Spacer(modifier = Modifier.height(16.dp))
        CategoriesSection(
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected
        )
        Spacer(modifier = Modifier.height(16.dp))
        PopularPresetsHeader(resultCount = presets.size)
        Spacer(modifier = Modifier.height(8.dp))

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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                items(presets.chunked(2)) { rowItems ->
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
        }
    }
}

@Composable
fun ShopTab(
    session: UserSession?,
    profile: UserProfile?,
    supabaseManager: SupabaseManager,
    dataStoreManager: DataStoreManager,
    onRefreshProfile: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var purchaseProgressPackage by remember { mutableStateOf<CoinPackage?>(null) }
    var successPackageCoins by remember { mutableStateOf<Int?>(null) }

    val packages = listOf(
        CoinPackage(50, "Rp 10.000", "Beli 50 Koin super untuk modifikasi SBA Anda!"),
        CoinPackage(100, "Rp 18.000", "Paket Populer! Beli 100 koin dengan harga hemat."),
        CoinPackage(250, "Rp 40.000", "Penawaran Terbaik! Beli 250 koin sepuasnya.")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        Text(
            text = "Toko Koin Bear",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF9800)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Koin digunakan untuk mengunduh preset eksklusif premium (10 koin/preset).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Balance Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFFF9800)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.MonetizationOn,
                    contentDescription = "Coin icon",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (session != null) "${profile?.coins ?: 100} Koin" else "Login untuk melihat koin",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
                if (session != null) {
                    Text(
                        text = "Username: ${session.username}",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onNavigateToProfile,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text("Login Sekarang", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Pilih Paket Isi Ulang",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(packages) { pkg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = session != null) {
                            purchaseProgressPackage = pkg
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MonetizationOn,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "+${pkg.coins} Koin",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = pkg.desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = pkg.price,
                            color = Color(0xFFFF9800),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }

    // ── Payment loading simulation dialog ──
    purchaseProgressPackage?.let { pkg ->
        LaunchedEffect(pkg) {
            delay(1500)
            val currentSession = session
            if (currentSession != null) {
                try {
                    val currentCoins = profile?.coins ?: 100
                    val newCoins = currentCoins + pkg.coins
                    supabaseManager.updateCoins(currentSession.userId, currentSession.accessToken, newCoins)
                    dataStoreManager.updateCoins(newCoins)
                    successPackageCoins = pkg.coins
                    onRefreshProfile()
                } catch (e: Exception) {
                    Toast.makeText(context, "Pembayaran gagal diproses: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
            purchaseProgressPackage = null
        }

        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Memproses Pembayaran", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color(0xFFFF9800))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Sedang mensimulasikan gerbang pembayaran aman. Mohon tunggu...",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        )
    }

    // ── Purchase success feedback dialog ──
    successPackageCoins?.let { coins ->
        AlertDialog(
            onDismissRequest = { successPackageCoins = null },
            confirmButton = {
                Button(
                    onClick = { successPackageCoins = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                ) { Text("OK", color = Color.White) }
            },
            title = { Text("Pembayaran Sukses!", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.Green,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Selamat! Paket berisi +$coins koin berhasil dibeli dan ditambahkan ke profil Anda.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
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
    val size: Float
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
            durationMillis = 1500,
            easing = LinearOutSlowInEasing
        ),
        finishedListener = { onFinished() },
        label = "heartProgress"
    )

    // Calculate position
    val yOffset = heart.startY - (progress * 380f)
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
    floatingHearts: MutableList<FloatingHeart>,
    startX: Float,
    startY: Float
) {
    val heartColors = listOf(
        Color(0xFFE91E63), // Pink
        Color(0xFFFF2E93), // Neon Pink
        Color(0xFFFF4646), // Neon Red
        Color(0xFFB02EFF), // Electric Purple
        Color(0xFFFF85A1), // Light Rose
        Color(0xFFFF0A54), // Hot Pink
        Color(0xFFFF5E36), // Coral Orange
        Color(0xFFFFC107), // Gold/Yellow
        Color(0xFF00E676), // Neon Green
        Color(0xFF00B0FF)  // Neon Blue
    )
    
    val currentMs = System.currentTimeMillis()
    repeat(12) { index ->
        val id = currentMs + index
        val angleDeg = kotlin.random.Random.nextInt(210, 331)
        val angleRad = angleDeg * (Math.PI / 180f)
        val speed = 2f + kotlin.random.Random.nextFloat() * 3f
        val speedX = kotlin.math.cos(angleRad).toFloat() * speed
        val amplitude = 15f + kotlin.random.Random.nextFloat() * 30f
        val frequency = 1.5f + kotlin.random.Random.nextFloat() * 1.5f
        val size = 0.7f + kotlin.random.Random.nextFloat() * 0.9f
        val rotation = kotlin.random.Random.nextFloat() * 360f
        val rotationSpeed = -180f + kotlin.random.Random.nextFloat() * 360f
        val color = heartColors[kotlin.random.Random.nextInt(heartColors.size)]
        
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
                size = size
            )
        )
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
    onPresetClick: (Preset) -> Unit
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

    if (session == null) {
        val gso = remember {
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(com.bearrushmod.BuildConfig.GOOGLE_WEB_CLIENT_ID)
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
                            val realProfile = supabaseManager.getProfile(userId, token, userEmail, userUsername)
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
                authError = "Google Sign In gagal: ${e.localizedMessage} (Status Code: ${e.statusCode})"
            } catch (e: Exception) {
                authError = "Error: ${e.localizedMessage}"
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F0F)), // Dark background matching the screenshot
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Image with bg_welcome_header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
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
                                        Color(0xFF0F0F0F)
                                    ),
                                    startY = 150f
                                )
                            )
                    )
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
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = authError ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
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
                                        val finalUsername = usernameInput.ifBlank { emailInput.split("@").first() }
                                        val signupResp = supabaseManager.signUp(emailInput, finalUsername, passwordInput)
                                        val token = signupResp.accessToken
                                        if (token != null) {
                                            val sessionObj = UserSession(
                                                userId = signupResp.user?.id ?: "",
                                                email = emailInput,
                                                username = finalUsername,
                                                accessToken = token,
                                                refreshToken = signupResp.refreshToken ?: "",
                                                coins = 100
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
                                        val realProfile = supabaseManager.getProfile(userId, token, emailInput, usernameInput)
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
        // Mode Terautentikasi -> Tampilkan Halaman Profil User
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Profil Anda",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { onRefreshProfile() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Profil", tint = Color(0xFFFF9800))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoadingProfile) {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFFF9800))
                }
            } else {
                // Info Profil Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar Circle
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                val avatarUrl = profile?.avatarUrl ?: ""
                                if (avatarUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = "Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = getBrandAvatarDrawableRes()),
                                        contentDescription = "Default Bear Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = profile?.username ?: session.username,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = session.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = profile?.bio?.ifEmpty { "Belum ada bio singkat. Edit profil untuk menambahkannya!" }
                                ?: "Belum ada bio singkat.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MonetizationOn, contentDescription = "Koin", tint = Color(0xFFFFB300))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${profile?.coins ?: session.coins} Koin",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            TextButton(
                                onClick = { showEditProfileDialog = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF9800))
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit Profil")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Preset yang Diunduh (${ownedPresets.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (ownedPresets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Belum ada preset yang diunduh. Kunjungi katalog kami!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(ownedPresets) { p ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val matchedPreset = presetsCatalog.find { it.id == p.presetId }
                                    if (matchedPreset != null) {
                                        onPresetClick(matchedPreset)
                                    } else {
                                        onPresetClick(
                                            Preset(
                                                id = p.presetId,
                                                name = p.presetName,
                                                category = p.presetCategory,
                                                preview_url = p.presetPreviewUrl
                                            )
                                        )
                                    }
                                },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = p.presetPreviewUrl.split(",").firstOrNull()?.trim() ?: "",
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = p.presetName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val catDisplay = when (p.presetCategory.lowercase()) {
                                        "nature" -> stringResource(R.string.category_nature)
                                        "structure", "structur", "bangunan" -> stringResource(R.string.category_structure)
                                        else -> p.presetCategory
                                    }
                                    Text(
                                        text = catDisplay,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Diunduh",
                                    tint = Color.Green,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        dataStoreManager.clearUserSession()
                        Toast.makeText(context, "Keluar dari sesi berhasil.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Keluar Sesi", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }

    // ── Edit Profile Dialog ──
    if (showEditProfileDialog && session != null) {
        var editUsername by remember { mutableStateOf(profile?.username ?: session.username) }
        var editBio by remember { mutableStateOf(profile?.bio ?: "") }
        var editAvatar by remember { mutableStateOf(profile?.avatarUrl ?: "") }
        var isUpdatingProfile by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        if (editUsername.isBlank()) return@Button
                        scope.launch {
                            isUpdatingProfile = true
                            try {
                                val updated = supabaseManager.updateProfile(
                                    userId = session.userId,
                                    token = session.accessToken,
                                    username = editUsername.trim(),
                                    bio = editBio.trim(),
                                    avatarUrl = editAvatar.trim()
                                )
                                // Sync local DataStore
                                dataStoreManager.saveUserSession(
                                    UserSession(
                                        userId = session.userId,
                                        email = session.email,
                                        username = updated.username,
                                        accessToken = session.accessToken,
                                        refreshToken = session.refreshToken,
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    enabled = !isUpdatingProfile
                ) {
                    if (isUpdatingProfile) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text("Simpan", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) { Text("Batal") }
            },
            title = { Text("Edit Profil Anda", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { editUsername = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF9800))
                    )
                    OutlinedTextField(
                        value = editAvatar,
                        onValueChange = { editAvatar = it },
                        label = { Text("URL Gambar Avatar (Opsional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF9800))
                    )
                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it },
                        label = { Text("Biografi Pendek") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        singleLine = false,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFFF9800))
                    )
                }
            }
        )
    }
}

// ── helper Composables ──

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
        Text(stringResource(R.string.popular_presets), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("$resultCount items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    confirmDownloadPreset: (Preset) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    var comments by remember { mutableStateOf<List<PresetComment>>(emptyList()) }
    var isFetchingComments by remember { mutableStateOf(false) }
    var commentInput by remember { mutableStateOf("") }
    var isPostingComment by remember { mutableStateOf(false) }
    var isLoved by remember(preset) { mutableStateOf(false) }

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
            comments = supabaseManager.getComments(preset.id)
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

        // Main scrollable content
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Instagram Header: User Avatar & Name
            item {
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
            }

            // Preview Image
            item {
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
                                    }
                                    // Trigger TikTok floating hearts burst
                                    val startX = with(density) { (imageOffset.x + tapOffset.x).toDp().value }
                                    val startY = with(density) { (imageOffset.y + tapOffset.y).toDp().value }
                                    spawnHeartsBurst(floatingHearts, startX, startY)

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
            }

            // Stats Interaction Bar
            item {
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
                                spawnHeartsBurst(floatingHearts, startX, startY)
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
            }

            // Description
            item {
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
            }

            // CTA Download Button
            item {
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
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else
                            Color(0xFFFF9800)
                    ),
                    enabled = downloadProgress == null
                ) {
                    if (isDownloaded) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Downloaded (Klik untuk buka file)",
                            color = MaterialTheme.colorScheme.primary,
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
                        val btnText = if (isPremium && !isAlreadyOwned) "Unduh (Butuh 10 Koin)" else "Unduh Preset (Gratis)"
                        Text(
                            text = btnText,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }

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
                val parentComments = comments.filter { it.parentCommentId == null }
                val repliesMap = comments.filter { it.parentCommentId != null }.groupBy { it.parentCommentId }

                parentComments.forEach { parent ->
                    item {
                        CommentRowItem(
                            comment = parent,
                            onReplyClick = {
                                replyingToComment = parent
                                replyingToUsername = parent.username.replace(" ", "").lowercase()
                                commentInput = ""
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
                                    replyingToUsername = reply.username.replace(" ", "").lowercase()
                                    commentInput = ""
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
                OutlinedTextField(
                    value = commentInput,
                    onValueChange = { commentInput = it },
                    placeholder = { Text(stringResource(R.string.write_comment), fontSize = 13.sp) },
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
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    maxLines = 3,
                    singleLine = false,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF9800),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = {
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
                    },
                    enabled = !isPostingComment && commentInput.trim().isNotEmpty()
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
}
}
}

@Composable
fun CommentRowItem(
    comment: PresetComment,
    isReply: Boolean = false,
    onReplyClick: () -> Unit
) {
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
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = getBrandAvatarForUsername(comment.username)),
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
                Text(
                    text = comment.username,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF9800)
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