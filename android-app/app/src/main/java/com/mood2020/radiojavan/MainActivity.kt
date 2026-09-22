package com.mood2020.radiojavan

import android.Manifest
import android.app.DownloadManager
import android.content.res.ColorStateList
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Html
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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

class MainActivity : AppCompatActivity() {
    private lateinit var tokenInput: TextInputEditText
    private lateinit var linkInput: TextInputEditText
    private lateinit var tokenStatus: TextView
    private lateinit var detectedStatus: TextView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var startButton: MaterialButton
    private lateinit var downloadButton: MaterialButton
    private lateinit var releaseButton: MaterialButton
    private lateinit var refreshPodcastsButton: MaterialButton
    private lateinit var podcastList: LinearLayout
    private var pendingAsset: Asset? = null
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
        window.statusBarColor = Color.rgb(16, 19, 31)
        window.navigationBarColor = Color.rgb(16, 19, 31)
        try {
            buildUi()
        } catch (error: Throwable) {
            showStartupError(error)
        }
    }

    private fun showStartupError(error: Throwable) {
        val message = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(dp(24), dp(24), dp(24), dp(24))
            text = "برنامه هنگام شروع با خطا روبه‌رو شد.\n\n${error::class.java.simpleName}: ${error.message ?: "بدون توضیح"}\n\nداده برنامه را پاک کن و دوباره اجرا کن. اگر خطا باقی ماند، همین متن را ارسال کن."
        }
        val container = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(16, 19, 31))
            addView(message)
        }
        setContentView(container)
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            background = gradient(intArrayOf(Color.rgb(10, 13, 25), Color.rgb(22, 20, 42)), GradientDrawable.Orientation.TL_BR)
            clipToPadding = false
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(26), dp(20), dp(42))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        scroll.addView(root)

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            contentDescription = "Radio Javan"
            layoutParams = LinearLayout.LayoutParams(dp(70), dp(70)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(8) }
        }
        root.addView(logo)
        root.addView(badge("●  RADIO JAVAN · ANDROID"))
        root.addView(heading("دانلودر رادیو جوان", 30).apply { setPadding(0, dp(7), 0, 0) })
        root.addView(label("لینک را بده، فایل آماده می‌شود.", 16, Color.rgb(170, 180, 202)))
        root.addView(space(18))

        val tokenCard = card()
        val tokenColumn = column()
        tokenColumn.addView(heading("اتصال امن به GitHub", 19))
        tokenColumn.addView(label("توکن فقط یک‌بار لازم است و رمزنگاری‌شده روی همین گوشی ذخیره می‌شود.", 13, Color.rgb(170, 180, 202)))
        tokenColumn.addView(space(10))
        val tokenField = field("GitHub token", true)
        tokenInput = tokenField.input
        tokenInput.setText(securePrefs.getString("github_token", ""))
        tokenColumn.addView(tokenField.layout)
        val saveToken = button("ذخیره توکن")
        tokenColumn.addView(saveToken)
        tokenStatus = label("", 12, Color.rgb(94, 224, 172))
        tokenColumn.addView(tokenStatus)
        tokenCard.addView(tokenColumn)
        root.addView(tokenCard)
        saveToken.setOnClickListener { saveToken() }
        if (!securePrefs.getString("github_token", "").isNullOrBlank()) {
            tokenStatus.text = "توکن ذخیره شده است. برای تغییر، مقدار جدید وارد کن."
        }

        root.addView(space(14))
        val linkCard = card()
        val linkColumn = column()
        linkColumn.addView(heading("لینک آهنگ یا پادکست", 19))
        linkColumn.addView(label("هر سه فرمت لینک Radio Javan پذیرفته می‌شود.", 13, Color.rgb(170, 180, 202)))
        linkColumn.addView(space(10))
        val linkField = field("https://play.radiojavan.com/podcast/...", false)
        linkInput = linkField.input
        linkColumn.addView(linkField.layout)
        detectedStatus = label("نوع لینک بعد از ورود نمایش داده می‌شود.", 12, Color.rgb(170, 180, 202))
        linkColumn.addView(detectedStatus)
        startButton = button("شروع دانلود")
        linkColumn.addView(startButton)
        progress = ProgressBar(this).apply { visibility = View.GONE }
        linkColumn.addView(progress, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER })
        status = label("", 13, Color.rgb(170, 180, 202))
        linkColumn.addView(status)
        downloadButton = button("دانلود فایل آماده")
        downloadButton.visibility = View.GONE
        linkColumn.addView(downloadButton)
        releaseButton = secondaryButton("باز کردن Release")
        releaseButton.visibility = View.GONE
        linkColumn.addView(releaseButton)
        linkCard.addView(linkColumn)
        root.addView(linkCard)

        linkInput.setOnFocusChangeListener { _, _ -> updateDetected() }
        linkInput.addTextChangedListener { updateDetected() }
        startButton.setOnClickListener { startDownload() }

        root.addView(space(14))
        val podcastCard = card()
        val podcastColumn = column()
        podcastColumn.addView(heading("جدیدترین پادکست‌ها", 19))
        podcastColumn.addView(label("فهرست مستقیم از Radio Javan · با یک لمس دانلود کن", 13, Color.rgb(170, 180, 202)))
        refreshPodcastsButton = secondaryButton("به‌روزرسانی فهرست")
        podcastColumn.addView(refreshPodcastsButton)
        podcastList = column().apply { setPadding(0, dp(4), 0, 0) }
        podcastColumn.addView(podcastList)
        podcastCard.addView(podcastColumn)
        root.addView(podcastCard)
        refreshPodcastsButton.setOnClickListener { loadLatestPodcasts() }

        root.addView(space(14))
        val helpCard = card()
        val help = column()
        help.addView(heading("سه نوع لینک", 17))
        help.addView(label("۱. لینک مستقیم play.radiojavan.com\n۲. لینک کوتاه rj.app\n۳. لینک اشتراکی /redirect", 13, Color.rgb(190, 199, 220)))
        helpCard.addView(help)
        root.addView(helpCard)
        setContentView(scroll)
        loadLatestPodcasts()
    }

    private fun loadLatestPodcasts() {
        refreshPodcastsButton.isEnabled = false
        podcastList.removeAllViews()
        podcastList.addView(label("در حال دریافت پادکست‌های جدید...", 13, Color.rgb(170, 180, 202)))
        lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { RadioJavanCatalog.fetchLatest() }
                podcastList.removeAllViews()
                if (items.isEmpty()) {
                    podcastList.addView(label("موردی پیدا نشد؛ بعداً دوباره تلاش کن.", 13, Color.rgb(255, 194, 157)))
                } else {
                    items.forEach { addPodcastRow(it) }
                }
            } catch (error: Exception) {
                podcastList.removeAllViews()
                podcastList.addView(label("فهرست موقتاً در دسترس نیست. لینک دستی همچنان فعال است.", 13, Color.rgb(255, 119, 131)))
            } finally {
                refreshPodcastsButton.isEnabled = true
            }
        }
    }

    private fun addPodcastRow(item: PodcastItem) {
        val row = MaterialCardView(this).apply {
            radius = dp(14).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.rgb(29, 36, 56))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        }
        val content = column().apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
        content.addView(label(item.title, 15, Color.WHITE))
        content.addView(label("Podcast · Radio Javan", 12, Color.rgb(170, 180, 202)))
        val download = secondaryButton("دانلود همین پادکست")
        content.addView(download)
        download.setOnClickListener {
            linkInput.setText(item.url)
            startDownload()
        }
        row.addView(content)
        podcastList.addView(row)
    }

    private fun saveToken() {
        val value = tokenInput.text?.toString()?.trim().orEmpty()
        if (value.length < 20 || !value.startsWith("github_")) {
            tokenStatus.setTextColor(Color.rgb(255, 119, 131))
            tokenStatus.text = "توکن معتبر GitHub وارد کن."
            return
        }
        securePrefs.edit().putString("github_token", value).apply()
        tokenStatus.setTextColor(Color.rgb(94, 224, 172))
        tokenStatus.text = "توکن با موفقیت و به‌صورت رمزنگاری‌شده ذخیره شد."
    }

    private fun updateDetected() {
        val kind = classify(linkInput.text?.toString().orEmpty())
        detectedStatus.text = kind
        detectedStatus.setTextColor(if (kind.startsWith("لینک نامعتبر")) Color.rgb(255, 119, 131) else Color.rgb(170, 180, 202))
    }

    private fun startDownload() {
        val token = securePrefs.getString("github_token", "").orEmpty()
        val link = linkInput.text?.toString()?.trim().orEmpty()
        if (token.isBlank()) {
            status.text = "ابتدا توکن GitHub را ذخیره کن."
            status.setTextColor(Color.rgb(255, 119, 131))
            return
        }
        if (classify(link).startsWith("لینک نامعتبر")) {
            status.text = "یکی از سه نوع لینک معتبر Radio Javan را وارد کن."
            status.setTextColor(Color.rgb(255, 119, 131))
            return
        }

        setBusy(true)
        lifecycleScope.launch {
            try {
                val api = GitHubApi(token)
                val startedAt = System.currentTimeMillis()
                setStatus("در حال بررسی لینک و اجرای GitHub Actions...", false)
                val knownRuns = withContext(Dispatchers.IO) { api.listRuns().map { it.id }.toSet() }
                withContext(Dispatchers.IO) { api.dispatch(link) }
                val run = waitForNewRun(api, knownRuns)
                val completed = waitForCompletion(api, run.id)
                if (completed.conclusion != "success") {
                    throw IOException("اجرای Workflow موفق نبود: ${completed.conclusion ?: "نامشخص"}")
                }
                setStatus("دانلود تمام شد؛ در حال دریافت لینک Release...", false)
                val found = waitForAsset(api, startedAt)
                showAsset(found.asset, found.releaseUrl)
            } catch (error: Exception) {
                setStatus(error.message ?: "خطای ناشناخته رخ داد.", true)
            } finally {
                setBusy(false)
            }
        }
    }

    private suspend fun waitForNewRun(api: GitHubApi, known: Set<Long>): RunInfo {
        repeat(30) {
            val run = withContext(Dispatchers.IO) {
                // Run IDs are monotonic; this avoids relying on the phone clock.
                api.listRuns().firstOrNull { it.id !in known }
            }
            if (run != null) return run
            setStatus("در انتظار شروع Workflow...", false)
            delay(3_500)
        }
        throw IOException("اجرای جدید GitHub Actions پیدا نشد.")
    }

    private suspend fun waitForCompletion(api: GitHubApi, id: Long): RunInfo {
        repeat(240) {
            val run = withContext(Dispatchers.IO) { api.getRun(id) }
            if (run.status == "completed") return run
            setStatus(if (run.status == "queued") "Workflow در صف GitHub است..." else "در حال دانلود و انتشار فایل...", false)
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

    private fun showAsset(asset: Asset, releaseUrl: String) {
        setStatus("فایل آماده است: ${asset.name}", false)
        downloadButton.visibility = View.VISIBLE
        releaseButton.visibility = View.VISIBLE
        downloadButton.setOnClickListener { enqueueDownload(asset) }
        releaseButton.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))) }
    }

    private fun enqueueDownload(asset: Asset) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingAsset = asset
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
        setStatus("دانلود شروع شد؛ پوشه Downloads را بررسی کن.", false)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            pendingAsset?.let { enqueueDownload(it) }
            pendingAsset = null
        }
    }

    private fun setBusy(busy: Boolean) {
        startButton.isEnabled = !busy
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        if (busy) {
            downloadButton.visibility = View.GONE
            releaseButton.visibility = View.GONE
        }
    }

    private fun setStatus(message: String, error: Boolean) {
        status.text = message
        status.setTextColor(if (error) Color.rgb(255, 119, 131) else Color.rgb(170, 180, 202))
    }

    private fun classify(value: String): String {
        val uri = runCatching { Uri.parse(value.trim()) }.getOrNull()
        val host = uri?.host.orEmpty()
        if (uri?.scheme != "https") return "لینک نامعتبر: فقط HTTPS"
        if (host == "rj.app" || host == "www.rj.app") return "نوع لینک: کوتاه rj.app"
        val deep = uri.getQueryParameter("r").orEmpty()
        if (host == "play.radiojavan.com" && uri.path?.trimEnd('/') == "/redirect" && deep.matches(Regex("radiojavan://(podcast|song)/[A-Za-z0-9_-]+"))) return "نوع لینک: اشتراکی Radio Javan"
        if (host in setOf("radiojavan.com", "www.radiojavan.com", "play.radiojavan.com", "www.play.radiojavan.com")) return "نوع لینک: مستقیم Radio Javan"
        return "لینک نامعتبر: دامنه شناخته نشد"
    }

    private fun card() = MaterialCardView(this).apply {
        radius = dp(22).toFloat()
        cardElevation = 0f
        strokeWidth = dp(1)
        strokeColor = Color.rgb(48, 58, 85)
        setCardBackgroundColor(Color.rgb(23, 28, 44))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)) }

    private fun field(hint: String, password: Boolean): InputField {
        val layout = TextInputLayout(this).apply { this.hint = hint; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE; layoutParams = LinearLayout.LayoutParams(-1, -2) }
        val input = TextInputEditText(layout.context).apply {
            inputType = if (password) 0x81 else 0x11
            textDirection = View.TEXT_DIRECTION_LTR
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        layout.addView(input)
        return InputField(layout, input)
    }

    private fun button(text: String) = MaterialButton(this).apply {
        this.text = text
        setTextColor(Color.rgb(25, 20, 25))
        backgroundTintList = ColorStateList.valueOf(Color.rgb(255, 138, 91))
        cornerRadius = dp(15)
        minimumHeight = dp(50)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
    }

    private fun secondaryButton(text: String) = MaterialButton(this).apply {
        this.text = text
        setTextColor(Color.rgb(220, 211, 255))
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        strokeWidth = dp(1)
        strokeColor = ColorStateList.valueOf(Color.rgb(112, 91, 180))
        cornerRadius = dp(15)
        minimumHeight = dp(48)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
    }

    private fun heading(text: String, size: Int) = label(text, size, Color.WHITE).apply {
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun badge(text: String) = TextView(this).apply {
        this.text = text
        textSize = 11f
        setTextColor(Color.rgb(255, 208, 178))
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(Color.rgb(48, 34, 48), dp(50).toFloat())
        setPadding(dp(12), dp(5), dp(12), dp(5))
        layoutParams = LinearLayout.LayoutParams(-2, -2)
    }

    private fun label(text: String, size: Int, color: Int) = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(color)
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        includeFontPadding = false
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun gradient(colors: IntArray, orientation: GradientDrawable.Orientation) = GradientDrawable(orientation, colors).apply {
        cornerRadius = dp(24).toFloat()
    }

    private fun space(height: Int) = Space(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

private data class RunInfo(val id: Long, val status: String, val conclusion: String?, val createdAt: Long, val htmlUrl: String)
private data class Asset(val name: String, val url: String, val size: Long, val updatedAt: Long)
private data class Release(val htmlUrl: String, val assets: List<Asset>)
private data class ReleaseAssetResult(val asset: Asset, val releaseUrl: String)
private data class InputField(val layout: TextInputLayout, val input: TextInputEditText)
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

private fun TextInputEditText.addTextChangedListener(action: () -> Unit) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = action()
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    })
}
