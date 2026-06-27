package com.bearrushmod

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
import com.bearrushmod.data.AdsManager
import com.bearrushmod.data.DataStoreManager
import com.bearrushmod.data.DownloadManager
import com.bearrushmod.data.PresetRepository
import com.bearrushmod.data.SupabaseManager
import com.bearrushmod.data.UpdateManager
import com.bearrushmod.model.Category
import com.bearrushmod.model.Preset
import com.bearrushmod.ui.screens.MainScreen
import com.bearrushmod.ui.theme.BearRushModTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var repository: PresetRepository
    private val downloadManager = DownloadManager()
    private val supabaseManager = SupabaseManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = PresetRepository(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        )

        val dataStoreManager = DataStoreManager(this)

        hideSystemUI()

        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        // ponytail: butuh MANAGE_EXTERNAL_STORAGE karena game baca dari folder eksternal
        // Upgrade path: minta game di-update pake scoped storage, atau pake MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                    startActivity(this)
                }
            }
        }

        // ── Cek update dari GitHub ──
        lifecycleScope.launch {
            UpdateManager.checkAndShowUpdate(this@MainActivity, BuildConfig.VERSION_CODE)
        }

        setContent {
            val darkTheme by dataStoreManager.isDarkMode.collectAsState(initial = false)

            LaunchedEffect(darkTheme) {
                window?.let {
                    androidx.core.view.WindowInsetsControllerCompat(it, it.decorView).isAppearanceLightStatusBars = !darkTheme
                }
            }

            BearRushModTheme(darkTheme = darkTheme) {
                var presets by remember { mutableStateOf<List<Preset>>(emptyList()) }
                var selectedCategory by remember { mutableStateOf<Category?>(null) }
                var isLoading by remember { mutableStateOf(true) }
                var isRefreshing by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }

                // ── Interstitial saat app dibuka ──
                LaunchedEffect(Unit) {
                    AdsManager.showInterstitial(this@MainActivity)
                }

                // ── Initial load ──
                LaunchedEffect(Unit) {
                    try {
                        presets = repository.getPresets()
                    } catch (e: Exception) {
                        error = e.message
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
                            val fresh = repository.getPresets()
                            if (fresh.isNotEmpty()) presets = fresh
                        } catch (_: Exception) {
                            // ponytail: silent retry — jangan ganggu UX kalau cuma network glitch
                        }
                    }
                }

                // ── Loading screen fullscreen gelap ──
                if (isLoading) {
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
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    error = null
                                    isLoading = true
                                    lifecycleScope.launch {
                                        try {
                                            presets = repository.getPresets()
                                        } catch (e: Exception) {
                                            error = e.message
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
                        darkTheme = darkTheme,
                        onThemeToggle = {
                            lifecycleScope.launch {
                                dataStoreManager.saveDarkMode(!darkTheme)
                            }
                        },
                        onRefresh = {
                            isRefreshing = true
                            lifecycleScope.launch {
                                try {
                                    val fresh = repository.getPresets()
                                    if (fresh.isNotEmpty()) presets = fresh
                                } catch (_: Exception) { }
                                isRefreshing = false
                            }
                        },
                        isRefreshing = isRefreshing,
                        downloadManager = downloadManager,
                        dataStoreManager = dataStoreManager,
                        supabaseManager = supabaseManager
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.statusBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }
}