package com.bearrushbuilder.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.bearrushbuilder.app.data.AdsManager
import com.bearrushbuilder.app.data.DataStoreManager
import com.bearrushbuilder.app.data.DownloadManager
import com.bearrushbuilder.app.data.PresetRepository
import com.bearrushbuilder.app.data.PlayUpdateManager
import com.bearrushbuilder.app.data.SupabaseManager
import com.bearrushbuilder.app.model.Category
import com.bearrushbuilder.app.model.Preset
import com.bearrushbuilder.app.ui.screens.MainScreen
import com.bearrushbuilder.app.ui.theme.BearRushModTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import com.bearrushbuilder.app.data.UpdateCheckWorker

class MainActivity : ComponentActivity() {
    private lateinit var repository: PresetRepository
    private lateinit var downloadManager: DownloadManager
    private lateinit var supabaseManager: SupabaseManager
    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // ⚠️ super.onCreate() HARUS dipanggil PERTAMA sebelum apapun
        super.onCreate(savedInstanceState)

        // Inisialisasi setelah super.onCreate()
        downloadManager = DownloadManager()
        supabaseManager = SupabaseManager()

        try {
            billingManager = BillingManager(this)
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "BillingManager initialization failed: ${t.message}", t)
        }

        try {
            repository = PresetRepository(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseKey = BuildConfig.SUPABASE_KEY
            )
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "PresetRepository initialization failed: ${t.message}", t)
        }

        val dataStoreManager = DataStoreManager(this)

        try {
            // Preload interstitial ad as early as possible
            AdsManager.preloadAd(this)
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "AdsManager preload failed: ${t.message}", t)
        }

        hideSystemUI()

        try {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = 
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Failed to set transparent bar colors: ${t.message}", t)
        }

        requestNotificationPermission()
        scheduleBackgroundUpdateCheck()

        setContent {
            val darkTheme = true
            val isSystemNavVisible = remember { mutableStateOf(false) }

            DisposableEffect(Unit) {
                val listener: (View, android.view.WindowInsets) -> android.view.WindowInsets = { view, insets ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        isSystemNavVisible.value = insets.isVisible(android.view.WindowInsets.Type.navigationBars())
                    }
                    view.onApplyWindowInsets(insets)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.decorView.setOnApplyWindowInsetsListener(listener)
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
                        isSystemNavVisible.value = (visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0
                    }
                }
                
                onDispose {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        window.decorView.setOnApplyWindowInsetsListener(null)
                    } else {
                        @Suppress("DEPRECATION")
                        window.decorView.setOnSystemUiVisibilityChangeListener(null)
                    }
                }
            }

            LaunchedEffect(darkTheme) {
                try {
                    window?.let {
                        androidx.core.view.WindowInsetsControllerCompat(it, it.decorView).isAppearanceLightStatusBars = false
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("MainActivity", "WindowInsetsControllerCompat failed: ${t.message}", t)
                }
            }

            BearRushModTheme(darkTheme = true) {
                var presets by remember { mutableStateOf<List<Preset>>(emptyList()) }
                var selectedCategory by remember { mutableStateOf<Category?>(null) }
                var isLoading by remember { mutableStateOf(true) }
                var isRefreshing by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }
                var isBanned by remember { mutableStateOf(false) }
                val deviceId = remember { android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID) }

                // ── Interstitial saat app dibuka ──
                LaunchedEffect(Unit) {
                    try {
                        AdsManager.showInterstitial(this@MainActivity)
                    } catch (t: Throwable) {
                        android.util.Log.e("MainActivity", "AdsManager showInterstitial failed: ${t.message}", t)
                    }
                }

                // ── Initial load ──
                LaunchedEffect(Unit) {
                    try {
                        if (supabaseManager.isDeviceBanned(deviceId)) {
                            isBanned = true
                            isLoading = false
                            return@LaunchedEffect
                        }
                    } catch (e: Throwable) {
                        android.util.Log.e("MainActivity", "Device ban check failed: ${e.message}")
                    }

                    // Cek update Google Play secara asinkron setelah UI dimuat
                    try {
                        PlayUpdateManager.checkAndStartUpdate(this@MainActivity)
                    } catch (t: Throwable) {
                        android.util.Log.e("MainActivity", "PlayUpdateManager check failed: ${t.message}", t)
                    }

                    try {
                        if (::repository.isInitialized) {
                            presets = repository.getPresets()
                        } else {
                            throw Exception("PresetRepository not initialized")
                        }
                    } catch (e: Throwable) {
                        error = "${e.javaClass.simpleName}: ${e.message}"
                        android.util.Log.e("MainActivity", "Initial load failed", e)
                    } finally {
                        isLoading = false
                    }
                }

                // ── Auto-reload tiap 15 detik ──
                // ponytail: polling 15s — tidak efisien, tapi sesuai request awal
                // Upgrade path: ganti ke WebSocket realtime dari Supabase
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(15_000)
                        try {
                            if (::repository.isInitialized) {
                                val fresh = repository.getPresets()
                                if (fresh.isNotEmpty()) presets = fresh
                            }
                        } catch (_: Throwable) {
                            // ponytail: silent retry — jangan ganggu UX kalau cuma network glitch
                        }
                    }
                }

                // ── Loading screen fullscreen gelap ──
                if (isBanned) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF110000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "⛔ BANNED ⛔",
                                color = Color.Red,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Akses Anda telah diblokir secara permanen dari server kami karena pelanggaran fatal.",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 16.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(
                                text = "Device ID: $deviceId",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Full Screen Image Cover
                        Image(
                            painter = painterResource(id = R.drawable.bear_splash),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Bottom black gradient overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.9f)
                                        )
                                    )
                                )
                        )
                        
                        // Bottom Loading Content
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Memuat...",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            CircularProgressIndicator(
                                color = Color(0xFFFF9800),
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 3.dp
                            )
                        }
                    }
                } else if (error != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF121212)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Gagal memuat data",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = error ?: "",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    error = null
                                    isLoading = true
                                    lifecycleScope.launch {
                                        try {
                                            if (::repository.isInitialized) {
                                                presets = repository.getPresets()
                                            } else {
                                                // Re-initialize repository
                                                repository = PresetRepository(
                                                    supabaseUrl = BuildConfig.SUPABASE_URL,
                                                    supabaseKey = BuildConfig.SUPABASE_KEY
                                                )
                                                presets = repository.getPresets()
                                            }
                                        } catch (e: Throwable) {
                                            error = "${e.javaClass.simpleName}: ${e.message}"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                            ) {
                                Text("Coba Lagi", color = Color.White)
                            }
                        }
                    }
                } else {
                    MainScreen(
                        presets = presets,
                        categories = PresetRepository.categories,
                        selectedCategory = selectedCategory,
                        onCategorySelected = { selectedCategory = it },
                        onRefresh = {
                            isRefreshing = true
                            lifecycleScope.launch {
                                try {
                                    if (::repository.isInitialized) {
                                        val fresh = repository.getPresets()
                                        if (fresh.isNotEmpty()) presets = fresh
                                    }
                                } catch (_: Throwable) { }
                                isRefreshing = false
                            }
                        },
                        isRefreshing = isRefreshing,
                        downloadManager = downloadManager,
                        dataStoreManager = dataStoreManager,
                        supabaseManager = supabaseManager,
                        billingManager = if (::billingManager.isInitialized) billingManager else null,
                        isSystemNavVisible = isSystemNavVisible.value
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            PlayUpdateManager.resumeInProgressUpdate(this)
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "PlayUpdateManager resume failed: ${t.message}", t)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let { controller ->
                    controller.hide(android.view.WindowInsets.Type.systemBars())
                    controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            }
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "hideSystemUI failed: ${t.message}", t)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    private fun scheduleBackgroundUpdateCheck() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "bear_rush_update_check",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            android.util.Log.d("MainActivity", "WorkManager update check worker scheduled successfully.")
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Failed to schedule background update check: ${t.message}", t)
        }
    }
}