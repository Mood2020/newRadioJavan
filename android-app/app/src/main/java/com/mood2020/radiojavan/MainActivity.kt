package com.mood2020.radiojavan

import android.Manifest
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Html
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private const val REQUEST_STORAGE = 42

private val Ink = Color(0xFF090C17)
private val SurfaceDark = Color(0xFF141A2B)
private val SurfaceSoft = Color(0xFF222D47)
private val Orange = Color(0xFFFF956B)
private val Purple = Color(0xFF9B83FF)
private val Mint = Color(0xFF63E6B1)
private val Muted = Color(0xFF9BA7C1)
private val Outline = Color(0xFF34415F)

private enum class AppTab(val label: String, val title: String, val icon: ImageVector) {
    Home("خانه", "رادیو جوان", Icons.Outlined.Home),
    Podcasts("پادکست", "جدیدترین پادکست‌ها", Icons.Outlined.LibraryMusic),
    Downloads("دانلود", "ساخت فایل جدید", Icons.Outlined.Download),
    Settings("تنظیمات", "امنیت و اتصال", Icons.Outlined.Settings),
}

class MainActivity : AppCompatActivity() {
    private var pendingAsset: Asset? = null
    private var pendingDownloadStatus: ((String, Boolean) -> Unit)? = null
    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            this,
            "secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        try {
            setContent { RadioJavanApp() }
        } catch (error: Throwable) {
            showStartupError(error)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun RadioJavanApp() {
        RadioJavanContent()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun RadioJavanContent() {
        val context = LocalContext.current
        val storedToken = remember { readStoredToken() }
        var selectedTabName by rememberSaveable { mutableStateOf(AppTab.Home.name) }
        var tokenDraft by rememberSaveable { mutableStateOf(storedToken) }
        var savedToken by rememberSaveable { mutableStateOf(storedToken) }
        var tokenMessage by rememberSaveable { mutableStateOf("") }
        var tokenMessageIsError by rememberSaveable { mutableStateOf(false) }
        var link by rememberSaveable { mutableStateOf("") }
        var statusMessage by rememberSaveable { mutableStateOf("") }
        var statusIsError by rememberSaveable { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var asset by remember { mutableStateOf<Asset?>(null) }
        var releaseUrl by remember { mutableStateOf("") }
        var podcasts by remember { mutableStateOf<List<PodcastItem>>(emptyList()) }
        var podcastLoading by remember { mutableStateOf(true) }
        var podcastError by remember { mutableStateOf(false) }
        var podcastRefresh by rememberSaveable { mutableStateOf(0) }
        val selectedTab = AppTab.values().firstOrNull { it.name == selectedTabName } ?: AppTab.Home

        LaunchedEffect(podcastRefresh) {
            podcastLoading = true
            podcastError = false
            runCatching { withContext(Dispatchers.IO) { RadioJavanCatalog.fetchLatest() } }
                .onSuccess { podcasts = it }
                .onFailure { podcastError = true }
            podcastLoading = false
        }

        fun saveToken() {
            val value = tokenDraft.trim()
            if (value.length < 20 || !value.startsWith("github_")) {
                tokenMessageIsError = true
                tokenMessage = "توکن معتبر GitHub وارد کن."
                return
            }
            val persisted = runCatching { securePrefs.edit().putString("github_token", value).apply() }
            if (persisted.isFailure) {
                tokenMessageIsError = true
                tokenMessage = "ذخیره امن روی این گوشی در دسترس نیست؛ داده برنامه را پاک و دوباره امتحان کن."
                return
            }
            savedToken = value
            tokenMessageIsError = false
            tokenMessage = "توکن رمزنگاری‌شده روی همین گوشی ذخیره شد."
        }

        fun beginDownload() {
            val cleanLink = link.trim()
            if (savedToken.isBlank()) {
                selectedTabName = AppTab.Settings.name
                statusIsError = true
                statusMessage = "ابتدا توکن GitHub را در تنظیمات ذخیره کن."
                return
            }
            if (classifyLink(cleanLink).startsWith("لینک نامعتبر")) {
                statusIsError = true
                statusMessage = "یکی از سه نوع لینک معتبر Radio Javan را وارد کن."
                return
            }
            busy = true
            asset = null
            releaseUrl = ""
            statusIsError = false
            statusMessage = "در حال بررسی لینک و اجرای GitHub Actions..."
            runDownload(
                link = cleanLink,
                token = savedToken,
                onStatus = { message, error -> statusMessage = message; statusIsError = error },
                onSuccess = { result ->
                    asset = result.asset
                    releaseUrl = result.releaseUrl
                    statusIsError = false
                    statusMessage = "فایل آماده است: ${result.asset.name}"
                },
                onFailure = { message -> statusIsError = true; statusMessage = message },
                onFinished = { busy = false },
            )
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Ink,
                    surface = SurfaceDark,
                    primary = Orange,
                    secondary = Purple,
                    tertiary = Mint,
                ),
            ) {
                Scaffold(
                    containerColor = Ink,
                    topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(selectedTab.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("RADIO JAVAN · 2026", color = Muted, fontSize = 9.sp, letterSpacing = 1.2.sp)
                            }
                        },
                        navigationIcon = {
                            Image(
                                painter = painterResource(R.drawable.ic_logo_compose),
                                contentDescription = "Radio Javan",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)),
                            )
                        },
                        actions = {
                            IconButton(onClick = { podcastRefresh++ }) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "به‌روزرسانی", tint = Muted)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Ink),
                        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
                    )
                    },
                    bottomBar = {
                    NavigationBar(
                        containerColor = SurfaceDark,
                        tonalElevation = 0.dp,
                    ) {
                        AppTab.values().forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTabName = tab.name },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Ink,
                                    selectedTextColor = Orange,
                                    indicatorColor = Orange,
                                    unselectedIconColor = Muted,
                                    unselectedTextColor = Muted,
                                ),
                            )
                        }
                    }
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
                        Crossfade(targetState = selectedTab, label = "tab-transition") { tab ->
                            when (tab) {
                            AppTab.Home -> HomeScreen(
                                onDownload = { selectedTabName = AppTab.Downloads.name },
                                onPodcasts = { selectedTabName = AppTab.Podcasts.name },
                            )
                            AppTab.Podcasts -> PodcastsScreen(
                                items = podcasts,
                                loading = podcastLoading,
                                error = podcastError,
                                onRefresh = { podcastRefresh++ },
                                onChoose = { item ->
                                    link = item.url
                                    statusMessage = "لینک پادکست آماده شد."
                                    statusIsError = false
                                    selectedTabName = AppTab.Downloads.name
                                },
                            )
                            AppTab.Downloads -> DownloadScreen(
                                link = link,
                                onLinkChange = { link = it },
                                detected = classifyLink(link),
                                busy = busy,
                                statusMessage = statusMessage,
                                statusIsError = statusIsError,
                                asset = asset,
                                releaseUrl = releaseUrl,
                                onStart = { beginDownload() },
                                onDownloadAsset = { selected -> enqueueDownload(selected) { message, error -> statusMessage = message; statusIsError = error } },
                                onOpenRelease = { if (releaseUrl.isNotBlank()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))) },
                            )
                            AppTab.Settings -> SettingsScreen(
                                token = tokenDraft,
                                onTokenChange = { tokenDraft = it },
                                message = tokenMessage,
                                messageIsError = tokenMessageIsError,
                                onSave = { saveToken() },
                            )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun readStoredToken(): String = runCatching {
        securePrefs.getString("github_token", "").orEmpty()
    }.getOrDefault("")

    private fun showStartupError(error: Throwable) {
        val message = android.widget.TextView(this).apply {
            setTextColor(AndroidColor.WHITE)
            textSize = 15f
            setPadding(dp(24), dp(24), dp(24), dp(24))
            text = "برنامه هنگام شروع با خطا روبه‌رو شد.\n\n${error::class.java.simpleName}: ${error.message ?: "بدون توضیح"}\n\nداده برنامه را پاک کن و دوباره اجرا کن. اگر خطا باقی ماند، همین متن را ارسال کن."
        }
        val container = android.widget.ScrollView(this).apply {
            setBackgroundColor(AndroidColor.rgb(9, 12, 23))
            addView(message)
        }
        setContentView(container)
    }

    private fun runDownload(
        link: String,
        token: String,
        onStatus: (String, Boolean) -> Unit,
        onSuccess: (ReleaseAssetResult) -> Unit,
        onFailure: (String) -> Unit,
        onFinished: () -> Unit,
    ) {
        lifecycleScope.launch {
            try {
                val api = GitHubApi(token)
                val startedAt = System.currentTimeMillis()
                onStatus("در حال بررسی لینک و اجرای GitHub Actions...", false)
                val knownRuns = withContext(Dispatchers.IO) { api.listRuns().map { it.id }.toSet() }
                withContext(Dispatchers.IO) { api.dispatch(link) }
                val run = waitForNewRun(api, knownRuns, onStatus)
                val completed = waitForCompletion(api, run.id, onStatus)
                if (completed.conclusion != "success") {
                    throw IOException("اجرای Workflow موفق نبود: ${completed.conclusion ?: "نامشخص"}")
                }
                onStatus("دانلود تمام شد؛ در حال دریافت لینک Release...", false)
                onSuccess(waitForAsset(api, startedAt))
            } catch (error: Exception) {
                onFailure(error.message ?: "خطای ناشناخته رخ داد.")
            } finally {
                onFinished()
            }
        }
    }

    private suspend fun waitForNewRun(api: GitHubApi, known: Set<Long>, onStatus: (String, Boolean) -> Unit): RunInfo {
        repeat(30) {
            val run = withContext(Dispatchers.IO) { api.listRuns().firstOrNull { it.id !in known } }
            if (run != null) return run
            onStatus("در انتظار شروع Workflow...", false)
            delay(3_500)
        }
        throw IOException("اجرای جدید GitHub Actions پیدا نشد.")
    }

    private suspend fun waitForCompletion(api: GitHubApi, id: Long, onStatus: (String, Boolean) -> Unit): RunInfo {
        repeat(240) {
            val run = withContext(Dispatchers.IO) { api.getRun(id) }
            if (run.status == "completed") return run
            onStatus(if (run.status == "queued") "Workflow در صف GitHub است..." else "در حال دانلود و انتشار فایل...", false)
            delay(5_000)
        }
        throw IOException("اجرای Workflow بیش از ۲۰ دقیقه طول کشید.")
    }

    private suspend fun waitForAsset(api: GitHubApi, startedAt: Long): ReleaseAssetResult {
        repeat(20) {
            val release = withContext(Dispatchers.IO) { api.getRelease() }
            val assets = release.assets.sortedByDescending { it.updatedAt }
            val fresh = assets.firstOrNull { it.updatedAt >= startedAt - 15_000 } ?: assets.firstOrNull()
            if (fresh != null) return ReleaseAssetResult(fresh, release.htmlUrl)
            delay(3_000)
        }
        throw IOException("فایل Release هنوز قابل مشاهده نیست.")
    }

    private fun enqueueDownload(asset: Asset, onStatus: (String, Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingAsset = asset
            pendingDownloadStatus = onStatus
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_STORAGE)
            return
        }
        val request = DownloadManager.Request(Uri.parse(asset.url))
            .setTitle(asset.name)
            .setDescription("Radio Javan Downloader")
            .setMimeType("audio/mpeg")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, asset.name)
        (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        onStatus("دانلود شروع شد؛ پوشه Downloads را بررسی کن.", false)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE) {
            val asset = pendingAsset
            val status = pendingDownloadStatus
            pendingAsset = null
            pendingDownloadStatus = null
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && asset != null) {
                enqueueDownload(asset, status ?: { _, _ -> })
            } else {
                status?.invoke("اجازه ذخیره فایل داده نشد.", true)
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

@Composable
private fun HomeScreen(onDownload: () -> Unit, onPodcasts: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HeroCard(onDownload = onDownload, onPodcasts = onPodcasts)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(Modifier.weight(1f), "۳ فرمت", "پشتیبانی لینک", Icons.Outlined.Link)
                MetricCard(Modifier.weight(1f), "۱۲ آیتم", "پادکست تازه", Icons.Outlined.GraphicEq)
                MetricCard(Modifier.weight(1f), "امن", "ذخیره توکن", Icons.Outlined.Security)
            }
        }
        item { SectionTitle("چطور کار می‌کند؟", "سه مرحله تا فایل آماده") }
        item {
            FlowCard(1, "لینک را وارد کن", "هر لینک معتبر Radio Javan را در تب دانلود قرار بده.", Icons.Outlined.Link)
        }
        item {
            FlowCard(2, "پردازش ابری", "GitHub Actions فایل را بدون سرور جداگانه آماده می‌کند.", Icons.Outlined.Bolt)
        }
        item {
            FlowCard(3, "دانلود مستقیم", "فایل آماده از Release به پوشه Downloads گوشی می‌رود.", Icons.Outlined.CloudDownload)
        }
        item {
            InfoCard("طراحی شده برای استفاده امن", "توکن فقط روی همین دستگاه و به‌صورت رمزنگاری‌شده نگهداری می‌شود.", Icons.Outlined.Lock, Mint)
        }
    }
}

@Composable
private fun HeroCard(onDownload: () -> Unit, onPodcasts: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF33275C), Color(0xFF1A284A), Color(0xFF1D3C43))))
            .padding(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(62.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.ic_logo_compose), "Radio Javan", Modifier.size(52.dp))
                }
                Spacer(Modifier.width(13.dp))
                Column {
                    Text("YOUR AUDIO DROP", color = Orange, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                    Text("رادیو جوان، ساده‌تر و سریع‌تر", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                "هر چیزی که می‌خواهی گوش بدهی را از یک لینک به یک فایل آماده تبدیل کن.",
                color = Color(0xFFD4D9EA),
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Ink),
                ) { Text("شروع دانلود", fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = onPodcasts,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF9C8CE1)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) { Text("پادکست‌ها") }
            }
        }
    }
}

@Composable
private fun MetricCard(modifier: Modifier, value: String, label: String, icon: ImageVector) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, Outline),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, contentDescription = null, tint = Orange, modifier = Modifier.size(19.dp))
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(label, color = Muted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun FlowCard(number: Int, title: String, description: String, icon: ImageVector) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Outline),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Orange, Purple))),
                contentAlignment = Alignment.Center,
            ) { Text(number.toString().padStart(2, '0'), color = Ink, fontWeight = FontWeight.Black) }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(description, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
            Icon(icon, contentDescription = null, tint = Purple, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun InfoCard(title: String, message: String, icon: ImageVector, tint: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.30f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(message, color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun PodcastsScreen(
    items: List<PodcastItem>,
    loading: Boolean,
    error: Boolean,
    onRefresh: () -> Unit,
    onChoose: (PodcastItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("پادکست‌های تازه", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("آخرین اپیزودهای Radio Javan در یک نگاه", color = Muted, fontSize = 13.sp)
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "به‌روزرسانی", tint = Orange) }
            }
        }
        item {
            if (loading) {
                StatusCard("در حال دریافت فهرست جدید...", false, loading = true)
            } else if (error) {
                StatusCard("فهرست موقتاً در دسترس نیست؛ لینک دستی همچنان فعال است.", true)
            } else {
                StatusPill("●  ${items.size} اپیزود آماده انتخاب", Mint)
            }
        }
        items(items, key = { it.url }) { item -> PodcastCard(item, items.indexOf(item) + 1, onChoose) }
    }
}

@Composable
private fun PodcastCard(item: PodcastItem, index: Int, onChoose: (PodcastItem) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(23.dp),
        border = BorderStroke(1.dp, Outline),
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(SurfaceSoft),
                    contentAlignment = Alignment.Center,
                ) { Text(index.toString().padStart(2, '0'), color = Orange, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 21.sp)
                    Text("PODCAST  ·  RADIO JAVAN", color = Muted, fontSize = 9.sp, letterSpacing = 1.1.sp)
                }
                Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = Purple)
            }
            OutlinedButton(
                onClick = { onChoose(item) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF62559B)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD7CCFF)),
            ) { Text("آماده‌سازی این پادکست") }
        }
    }
}

@Composable
private fun DownloadScreen(
    link: String,
    onLinkChange: (String) -> Unit,
    detected: String,
    busy: Boolean,
    statusMessage: String,
    statusIsError: Boolean,
    asset: Asset?,
    releaseUrl: String,
    onStart: () -> Unit,
    onDownloadAsset: (Asset) -> Unit,
    onOpenRelease: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("فایل جدید", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("لینک را وارد کن؛ بقیه کار خودکار انجام می‌شود.", color = Muted, fontSize = 13.sp)
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, Outline),
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("لینک Radio Javan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        OutlinedTextField(
                            value = link,
                            onValueChange = onLinkChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("https://play.radiojavan.com/...") },
                            leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            shape = RoundedCornerShape(17.dp),
                        )
                    }
                    StatusPill(detected, if (detected.startsWith("لینک نامعتبر")) Orange else Mint)
                    Button(
                        onClick = onStart,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(17.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Ink),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(19.dp), color = Ink, strokeWidth = 2.dp)
                            Spacer(Modifier.width(9.dp))
                            Text("در حال پردازش...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Outlined.Bolt, contentDescription = null)
                            Spacer(Modifier.width(7.dp))
                            Text("شروع دانلود", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item {
            AnimatedVisibility(statusMessage.isNotBlank()) {
                StatusCard(statusMessage, statusIsError, loading = busy)
            }
        }
        if (asset != null) {
            item { AssetCard(asset, releaseUrl, onDownloadAsset, onOpenRelease) }
        }
        item {
            InfoCard("پشتیبانی از همه لینک‌های اصلی", "لینک مستقیم، لینک کوتاه rj.app و لینک اشتراکی /redirect پذیرفته می‌شوند.", Icons.Outlined.Info, Purple)
        }
    }
}

@Composable
private fun AssetCard(asset: Asset, releaseUrl: String, onDownload: (Asset) -> Unit, onOpenRelease: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Mint.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, Mint.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Mint, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("فایل آماده دانلود است", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(asset.name, color = Muted, fontSize = 12.sp)
                }
            }
            Button(
                onClick = { onDownload(asset) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
            ) { Icon(Icons.Outlined.Download, null); Spacer(Modifier.width(7.dp)); Text("دانلود به گوشی", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = onOpenRelease,
                enabled = releaseUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Mint),
            ) { Icon(Icons.Outlined.OpenInNew, null); Spacer(Modifier.width(7.dp)); Text("باز کردن Release") }
        }
    }
}

@Composable
private fun SettingsScreen(
    token: String,
    onTokenChange: (String) -> Unit,
    message: String,
    messageIsError: Boolean,
    onSave: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("اتصال و امنیت", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("تنظیمات یک‌بار انجام می‌شوند و بعد فراموششان کن.", color = Muted, fontSize = 13.sp)
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, Outline),
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = Mint)
                        Spacer(Modifier.width(8.dp))
                        Text("توکن GitHub", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Text("توکن فقط برای اجرای Workflow استفاده می‌شود و روی سرور دیگری ارسال نمی‌شود.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        OutlinedTextField(
                            value = token,
                            onValueChange = onTokenChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("GitHub token") },
                            leadingIcon = { Icon(Icons.Outlined.Security, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(17.dp),
                        )
                    }
                    Button(
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Ink),
                    ) { Text("ذخیره امن توکن", fontWeight = FontWeight.Bold) }
                    if (message.isNotBlank()) {
                        Text(message, color = if (messageIsError) Orange else Mint, fontSize = 12.sp)
                    }
                }
            }
        }
        item { InfoCard("حریم خصوصی روشن", "این اپ سرور شخصی ندارد. توکن رمزنگاری‌شده می‌ماند و ارتباطات فقط با GitHub و Radio Javan از طریق HTTPS انجام می‌شوند.", Icons.Outlined.Security, Mint) }
        item { InfoCard("برای انتشار در Google Play", "نسخه نهایی باید با AAB امضاشده منتشر شود و سیاست حقوق محتوا و Data safety رعایت شود.", Icons.Outlined.Info, Purple) }
    }
}

@Composable
private fun StatusPill(message: String, tint: Color) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(50.dp)).background(tint.copy(alpha = 0.10f)).padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = tint, fontSize = 11.sp, maxLines = 2)
    }
}

@Composable
private fun StatusCard(message: String, error: Boolean, loading: Boolean = false) {
    val tint = if (error) Orange else Purple
    Card(
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.28f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = tint, strokeWidth = 2.dp)
            } else {
                Icon(if (error) Icons.Outlined.ErrorOutline else Icons.Outlined.AutoAwesome, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(message, color = Color.White, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

private fun classifyLink(value: String): String {
    if (value.isBlank()) return "نوع لینک بعد از ورود نمایش داده می‌شود."
    val uri = runCatching { Uri.parse(value.trim()) }.getOrNull()
    val host = uri?.host.orEmpty()
    if (uri?.scheme != "https") return "لینک نامعتبر: فقط HTTPS"
    if (host == "rj.app" || host == "www.rj.app") return "نوع لینک: کوتاه rj.app"
    val deep = uri.getQueryParameter("r").orEmpty()
    if (host == "play.radiojavan.com" && uri.path?.trimEnd('/') == "/redirect" && deep.matches(Regex("radiojavan://(podcast|song)/[A-Za-z0-9_-]+"))) return "نوع لینک: اشتراکی Radio Javan"
    if (host in setOf("radiojavan.com", "www.radiojavan.com", "play.radiojavan.com", "www.play.radiojavan.com")) return "نوع لینک: مستقیم Radio Javan"
    return "لینک نامعتبر: دامنه شناخته نشد"
}

private data class RunInfo(val id: Long, val status: String, val conclusion: String?, val createdAt: Long, val htmlUrl: String)
private data class Asset(val name: String, val url: String, val size: Long, val updatedAt: Long)
private data class Release(val htmlUrl: String, val assets: List<Asset>)
private data class ReleaseAssetResult(val asset: Asset, val releaseUrl: String)
private data class PodcastItem(val title: String, val url: String)

private object RadioJavanCatalog {
    private const val CATALOG_URL = "https://play.radiojavan.com/special/podcasts"
    private val podcastLink = Regex(
        """<a[^>]+href=[\"'](/podcast/[^\"'/?#]+)[\"'][^>]*>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun fetchLatest(): List<PodcastItem> {
        val connection = (URL(CATALOG_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("User-Agent", "RadioJavanDownloader-Android")
        }
        return try {
            if (connection.responseCode !in 200..299) throw IOException("Radio Javan returned HTTP ${connection.responseCode}")
            parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    fun parse(html: String): List<PodcastItem> {
        val unique = linkedMapOf<String, PodcastItem>()
        podcastLink.findAll(html).forEach { match ->
            val path = match.groupValues[1]
            val title = Html.fromHtml(match.groupValues[2], Html.FROM_HTML_MODE_LEGACY)
                .toString()
                .replace(Regex("\\s+"), " ")
                .trim()
            if (title.isNotBlank()) unique.putIfAbsent(path, PodcastItem(title, "https://play.radiojavan.com$path"))
        }
        return unique.values.take(12)
    }
}

private class GitHubApi(private val token: String) {
    companion object { private const val BASE = "https://api.github.com/repos/Mood2020/newRadioJavan" }

    fun dispatch(link: String) {
        request("/actions/workflows/radiojavan.yml/dispatches", "POST", JSONObject().apply { put("ref", "main"); put("inputs", JSONObject().put("podcast_url", link)) }.toString())
    }

    fun listRuns(): List<RunInfo> {
        val array = request("/actions/runs?event=workflow_dispatch&branch=main&per_page=30").getJSONArray("workflow_runs")
        return (0 until array.length()).map { parseRun(array.getJSONObject(it)) }
    }

    fun getRun(id: Long): RunInfo = parseRun(request("/actions/runs/$id"))

    fun getRelease(): Release {
        val json = request("/releases/tags/radiojavan-downloads")
        val assets = json.optJSONArray("assets") ?: org.json.JSONArray()
        return Release(json.optString("html_url"), (0 until assets.length()).map { parseAsset(assets.getJSONObject(it)) })
    }

    private fun parseRun(json: JSONObject) = RunInfo(json.getLong("id"), json.optString("status"), json.optString("conclusion").ifBlank { null }, parseTime(json.optString("created_at")), json.optString("html_url"))
    private fun parseAsset(json: JSONObject) = Asset(json.optString("name"), json.optString("browser_download_url"), json.optLong("size"), parseTime(json.optString("updated_at")))
    private fun parseTime(value: String) = runCatching { OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli() }.getOrDefault(0L)

    private fun request(path: String, method: String = "GET", body: String? = null): JSONObject {
        val connection = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 20_000; readTimeout = 30_000; doInput = true
            setRequestProperty("Accept", "application/vnd.github+json"); setRequestProperty("X-GitHub-Api-Version", "2022-11-28"); setRequestProperty("Authorization", "Bearer $token"); setRequestProperty("User-Agent", "RadioJavanDownloader-Android")
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json") }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException(runCatching { JSONObject(text).optString("message") }.getOrDefault("GitHub API error $code"))
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally { connection.disconnect() }
    }
}
