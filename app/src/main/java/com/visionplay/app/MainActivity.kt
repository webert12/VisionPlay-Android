package com.visionplay.app

import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.KeyStore
import android.text.Editable
import android.text.TextWatcher

private data class MediaEntry(
    val name: String,
    val url: String,
    val group: String,
    val section: String,
    val itemId: String = "",
    val extension: String = "",
    val seriesId: String = "",
    val releaseDate: String = "",
    val plot: String = "",
    val year: String = "",
    val director: String = "",
    val cast: String = "",
    val posterUrl: String = "",
    val addedAt: String = "",
    val epgChannelId: String = "",
    val currentProgram: String = "",
    val currentProgramTime: String = "",
    val currentProgramDescription: String = "",
    val nextProgram: String = "",
    val nextProgramTime: String = ""
)

private data class EpgProgram(val title: String, val time: String, val description: String = "")

class MainActivity : AppCompatActivity() {
    private val bg = Color.rgb(5, 9, 17)
    private val panel = Color.rgb(15, 23, 38)
    private val panel2 = Color.rgb(20, 31, 49)
    private val blue = Color.rgb(28, 137, 255)
    private val cyan = Color.rgb(64, 190, 255)
    private val white = Color.WHITE
    private val muted = Color.rgb(154, 169, 190)
    private val io = Executors.newSingleThreadExecutor()
    private val imageIo = Executors.newFixedThreadPool(3)

    private lateinit var root: LinearLayout
    private lateinit var urlInput: EditText
    private lateinit var userInput: EditText
    private lateinit var passInput: EditText
    private lateinit var loadingText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var contentBox: LinearLayout
    private lateinit var playerView: PlayerView
    private lateinit var searchInput: EditText

    private var player: ExoPlayer? = null
    private val entries = mutableListOf<MediaEntry>()
    private var activeSection = "INICIO"
    private var selectedFolder: String? = null
    private var visibleLimit = 100
    private var fullScreenMode = false
    private var detailScreenVisible = false
    private var playbackControlsVisible = true
    private var currentPlaying: MediaEntry? = null
    private var currentPlaybackQueue: List<MediaEntry> = emptyList()
    private var currentPlaybackIndex = -1
    private var activeSeriesForEpisodes: MediaEntry? = null
    private var episodeEntries: MutableList<MediaEntry> = mutableListOf()
    private var selectedSeason: String? = null
    private val playbackHandler = Handler(Looper.getMainLooper())
    private var playbackSeekBar: SeekBar? = null
    private var playbackTimeLabel: TextView? = null
    private var playbackOverlay: LinearLayout? = null
    private val playbackTicker = object : Runnable {
        override fun run() {
            val exo = player
            if (exo != null && fullScreenMode) {
                val duration = exo.duration.coerceAtLeast(0L)
                val position = exo.currentPosition.coerceAtLeast(0L)
                playbackSeekBar?.let { bar ->
                    if (duration > 0) {
                        bar.max = 1000
                        bar.progress = ((position.toDouble() / duration) * 1000).toInt().coerceIn(0, 1000)
                    } else { bar.max = 1000; bar.progress = 0 }
                }
                playbackTimeLabel?.text = "${formatTime(position)} / ${if (duration > 0) formatTime(duration) else "--:--"}"
                currentPlaying?.let { savePlaybackProgress(it, position) }
                playbackHandler.postDelayed(this, 1000)
            }
        }
    }
    @Volatile private var isLoading = false

    // Saved profile password is encrypted with an Android Keystore AES key.
    private var serverBase = ""
    private var username = ""
    private var password = ""

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadM3uFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        initializePlayer()
        showLogin()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().also { exo ->
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    runOnUiThread { toast("Falha na reprodução. Confira o link, a conexão e o formato do fluxo.") }
                }
            })
        }
    }

    private fun baseScreen(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bg)
        setPadding(dp(18), dp(18), dp(18), dp(12))
    }

    private fun showLogin(error: String? = null) {
        root = baseScreen()
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(brandHeader(), matchWrap())
        body.addView(TextView(this).apply {
            text = "Seu entretenimento, em um só lugar"
            setTextColor(muted); textSize = 15f
            setPadding(0, dp(6), 0, dp(18))
        }, matchWrap())
        val saved = loadSavedAccount()
        if (saved != null) {
            body.addView(makeButton("👤  CONTINUAR COMO ${saved.second}") {
                connectXtream(saved.first, saved.second, saved.third)
            }, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(18) })
        }
        body.addView(sectionLabel("CONECTAR AO SEU SERVIDOR"), matchWrap())
        urlInput = input("URL base ou link completo Xtream/M3U", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        userInput = input("Usuário", InputType.TYPE_CLASS_TEXT)
        passInput = input("Senha", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        body.addView(urlInput, fieldParams())
        body.addView(userInput, fieldParams())
        body.addView(passInput, fieldParams())
        body.addView(makeButton("CONECTAR E CARREGAR CONTEÚDOS") {
            val raw = urlInput.text.toString().trim()
            val user = userInput.text.toString().trim()
            val pass = passInput.text.toString()
            if (raw.isBlank() || user.isBlank() || pass.isBlank()) {
                toast("Preencha a URL, o usuário e a senha.")
                return@makeButton
            }
            connectXtream(raw, user, pass)
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
        body.addView(makeButton("IMPORTAR ARQUIVO M3U") {
            pickFile.launch(arrayOf("*/*", "text/*", "application/octet-stream"))
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        if (!error.isNullOrBlank()) {
            body.addView(TextView(this).apply {
                text = error
                setTextColor(Color.rgb(255, 130, 130)); textSize = 13f
                setPadding(0, dp(14), 0, dp(6))
            }, matchWrap())
        }
        body.addView(TextView(this).apply {
            text = "Sua conta pode ficar salva neste aparelho. A senha é protegida com criptografia local; prefira HTTPS quando disponível."
            setTextColor(muted); textSize = 12f
            setPadding(0, dp(22), 0, dp(10))
        }, matchWrap())
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun showLoading(message: String = "Conectando ao servidor...") {
        root = baseScreen()
        root.gravity = Gravity.CENTER
        val logo = brandHeader().apply { gravity = Gravity.CENTER }
        root.addView(logo, matchWrap())
        root.addView(TextView(this).apply {
            text = "PREPARANDO SUA EXPERIÊNCIA"
            setTextColor(white); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setPadding(0, dp(28), 0, dp(18))
        }, matchWrap())
        progress = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(blue)
        }
        root.addView(progress, LinearLayout.LayoutParams(dp(58), dp(58)).apply { gravity = Gravity.CENTER })
        loadingText = TextView(this).apply {
            text = message; setTextColor(muted); textSize = 14f
            gravity = Gravity.CENTER; setPadding(0, dp(20), 0, 0)
        }
        root.addView(loadingText, matchWrap())
        setContentView(root)
    }

    private fun updateLoading(message: String) {
        runOnUiThread {
            if (!isFinishing && ::loadingText.isInitialized) loadingText.text = message
        }
    }

    private fun connectXtream(rawServer: String, user: String, pass: String) {
        if (isLoading) return
        val normalized = normalizeServer(rawServer)
        if (normalized == null) {
            toast("Informe uma URL válida começando com http:// ou https://")
            return
        }
        isLoading = true
        serverBase = normalized
        username = user
        password = pass
        showLoading("Validando usuário e senha...")
        io.execute {
            try {
                val auth = fetchJsonObject(apiUrl())
                val userInfo = auth.getAsJsonObject("user_info")
                val authOk = userInfo?.get("auth")?.asString == "1"
                val status = userInfo?.get("status")?.asString.orEmpty()
                if (!authOk || status.equals("Disabled", true)) {
                    throw IllegalStateException("Acesso recusado. Confira usuário, senha e validade da conta.")
                }
                runCatching { saveAccount(normalized, user, pass) }

                val loaded = mutableListOf<MediaEntry>()
                val warnings = mutableListOf<String>()
                updateLoading("Canais ao vivo: baixando catálogo completo...")
                try {
                    val categories = try { fetchCategories("get_live_categories") } catch (_: Exception) { emptyMap() }
                    val items = fetchItems("get_live_streams", "Canais ao vivo", categories, "live")
                    loaded.addAll(items)
                    if (items.isEmpty()) warnings += "Canais: o servidor retornou 0 itens"
                } catch (e: Exception) {
                    warnings += "Canais: ${safeError(e)}"
                }

                updateLoading("Filmes: baixando catálogo completo. Isso pode levar alguns minutos...")
                try {
                    val categories = try { fetchCategories("get_vod_categories") } catch (_: Exception) { emptyMap() }
                    val items = fetchItems("get_vod_streams", "Filmes", categories, "movie")
                    loaded.addAll(items)
                    if (items.isEmpty()) warnings += "Filmes: o servidor retornou 0 itens"
                } catch (e: Exception) {
                    warnings += "Filmes: ${safeError(e)}"
                }

                updateLoading("Séries: baixando catálogo completo. Aguarde...")
                try {
                    val categories = try { fetchCategories("get_series_categories") } catch (_: Exception) { emptyMap() }
                    val items = fetchItems("get_series", "Séries", categories, "series")
                    loaded.addAll(items)
                    if (items.isEmpty()) warnings += "Séries: o servidor retornou 0 itens"
                } catch (e: Exception) {
                    warnings += "Séries: ${safeError(e)}"
                }

                if (loaded.isEmpty()) {
                    throw IllegalStateException("O acesso foi validado, mas o servidor não retornou catálogos. Confirme com o fornecedor se a API e os pacotes estão habilitados.")
                }
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    entries.clear()
                    entries.addAll(loaded)
                    isLoading = false
                    activeSection = "INICIO"
                    selectedFolder = null
                    searchInputTextReset()
                    showHome()
                    val counts = "${entries.count { it.section == "Canais ao vivo" }} canais, ${entries.count { it.section == "Filmes" }} filmes e ${entries.count { it.section == "Séries" }} séries"
                    toast("Catálogo carregado: $counts" + if (warnings.isNotEmpty()) " • Alguns catálogos falharam: ${warnings.joinToString("; ").take(250)}" else "")
                }
            } catch (e: Exception) {
                val message = safeError(e)
                runOnUiThread {
                    isLoading = false
                    showLogin(message)
                }
            }
        }
    }

    private fun normalizeServer(raw: String): String? {
        var value = raw.trim().trimEnd('/')
        // Accept a base URL or a complete Xtream/M3U endpoint, but store only the server base.
        value = value.substringBefore("?").trimEnd('/')
        listOf("/player_api.php", "/get.php", "/xmltv.php", "/panel_api.php").forEach { suffix ->
            if (value.endsWith(suffix, true)) value = value.dropLast(suffix.length).trimEnd('/')
        }
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        if ((uri.scheme != "http" && uri.scheme != "https") || uri.host.isNullOrBlank()) return null
        return value
    }

    private fun apiUrl(action: String? = null, extraKey: String? = null, extraValue: String? = null): String {
        val builder = Uri.parse("$serverBase/player_api.php").buildUpon()
            .appendQueryParameter("username", username)
            .appendQueryParameter("password", password)
        if (!action.isNullOrBlank()) builder.appendQueryParameter("action", action)
        if (!extraKey.isNullOrBlank() && extraValue != null) builder.appendQueryParameter(extraKey, extraValue)
        return builder.build().toString()
    }

    private fun openConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 120000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "VisionPlayTV/1.1 Android")
            setRequestProperty("Accept", "application/json, text/plain, */*")
        }
    }

    private fun fetchJsonObject(url: String): JsonObject {
        val connection = openConnection(url)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Servidor respondeu HTTP $code.")
            InputStreamReader(connection.inputStream, StandardCharsets.UTF_8).use { reader ->
                val parsed = JsonParser.parseReader(reader)
                if (!parsed.isJsonObject) throw IllegalStateException("Resposta inesperada da API. Verifique o endereço do servidor.")
                return parsed.asJsonObject
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchCategories(action: String): Map<String, String> {
        val connection = openConnection(apiUrl(action))
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code ao consultar $action")
            val map = LinkedHashMap<String, String>()
            JsonReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                reader.isLenient = true
                reader.beginArray()
                // Always consume the full response; stopping early makes endArray() fail on large catalogs.
                while (reader.hasNext()) {
                    var id = ""
                    var name = "Outros"
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "category_id" -> id = reader.nextStringSafe()
                            "category_name" -> name = reader.nextStringSafe()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    if (id.isNotBlank()) map[id] = name
                }
                reader.endArray()
            }
            return map
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchItems(action: String, section: String, categories: Map<String, String>, type: String): List<MediaEntry> {
        val connection = openConnection(apiUrl(action))
        val result = ArrayList<MediaEntry>()
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code ao consultar $action")
            JsonReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                reader.isLenient = true
                reader.beginArray()
                // Keep the entire metadata catalog in memory (not the video files). The UI renders only
                // the selected folder/search results, so catalogs with tens of thousands of entries work.
                while (reader.hasNext()) {
                    var id = ""
                    var name = "Conteúdo"
                    var categoryId = ""
                    var plot = ""
                    var releaseDate = ""
                    var year = ""
                    var director = ""
                    var cast = ""
                    var posterUrl = ""
                    var addedAt = ""
                    var epgChannelId = ""
                    var extension = when (type) { "live" -> "ts"; "movie" -> "mp4"; else -> "mp4" }
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "stream_id", "series_id" -> id = reader.nextStringSafe()
                            "name", "title" -> name = reader.nextStringSafe()
                            "category_id" -> categoryId = reader.nextStringSafe()
                            "container_extension" -> extension = reader.nextStringSafe().ifBlank { extension }
                            "plot", "description" -> plot = reader.nextStringSafe()
                            "releaseDate", "release_date" -> releaseDate = reader.nextStringSafe()
                            "year" -> year = reader.nextStringSafe()
                            "director" -> director = reader.nextStringSafe()
                            "cast", "actors" -> cast = reader.nextStringSafe()
                            "stream_icon", "cover", "cover_big", "movie_image" -> posterUrl = reader.nextStringSafe().ifBlank { posterUrl }
                            "added" -> addedAt = reader.nextStringSafe()
                            "epg_channel_id" -> epgChannelId = reader.nextStringSafe()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    if (id.isNotBlank()) {
                        val category = categories[categoryId] ?: "Outros"
                        val url = when (type) {
                            "live" -> streamUrl("live", id, "ts")
                            "movie" -> streamUrl("movie", id, extension)
                            else -> "" // Episodes are fetched only when the series is opened.
                        }
                        result.add(MediaEntry(name, url, category, section, id, extension, if (type == "series") id else "", releaseDate, plot, year, director, cast, posterUrl, formatAddedDate(addedAt), epgChannelId))
                    }
                }
                reader.endArray()
            }
        } finally {
            connection.disconnect()
        }
        return result
    }

    private fun JsonReader.nextStringSafe(): String {
        return when (peek()) {
            com.google.gson.stream.JsonToken.STRING -> nextString()
            com.google.gson.stream.JsonToken.NUMBER -> nextString()
            com.google.gson.stream.JsonToken.NULL -> { nextNull(); "" }
            else -> { skipValue(); "" }
        }
    }

    private fun streamUrl(kind: String, id: String, extension: String): String {
        fun pathPart(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        return "$serverBase/$kind/${pathPart(username)}/${pathPart(password)}/$id.$extension"
    }

    private fun showHome() {
        detailScreenVisible = false
        fullScreenMode = false
        playbackHandler.removeCallbacks(playbackTicker)
        playerViewSafeDetach()
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        root = baseScreen()
        root.setPadding(dp(20), dp(14), dp(20), dp(14))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), dp(10))
        }
        val miniLogo = TextView(this).apply {
            text = "▶"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(white)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(blue); cornerRadius = dp(8).toFloat()
            }
        }
        header.addView(miniLogo, LinearLayout.LayoutParams(dp(38), dp(32)).apply { marginEnd = dp(10) })
        header.addView(TextView(this).apply {
            text = "VISION PLAY TV"; textSize = 20f; setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD; letterSpacing = .03f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(makeSmallButton("↻") { refreshCatalogManually() })
        header.addView(makeSmallButton("CONTA") { stopPlayback(); showLogin() })
        root.addView(header, matchWrap())

        root.addView(TextView(this).apply {
            text = "O QUE VOCÊ QUER ASSISTIR?"; textSize = 12f; setTextColor(muted)
            typeface = Typeface.DEFAULT_BOLD; letterSpacing = .12f
            setPadding(dp(4), dp(8), 0, dp(14))
        }, matchWrap())

        val cards = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val sections = listOf(
            Triple("CANAIS AO VIVO", "${entries.count { it.section == "Canais ao vivo" }} canais", "◉") to "Canais ao vivo",
            Triple("FILMES", "${entries.count { it.section == "Filmes" }} títulos", "▶") to "Filmes",
            Triple("SÉRIES", "${entries.count { it.section == "Séries" }} séries", "▤") to "Séries"
        )
        sections.forEach { pair ->
            val cardData = pair.first
            val section = pair.second
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(panel); cornerRadius = dp(16).toFloat(); setStroke(dp(1), Color.rgb(31, 57, 86))
                }
                addView(TextView(this@MainActivity).apply {
                    text = cardData.third; textSize = 34f; gravity = Gravity.CENTER; setTextColor(cyan)
                }, LinearLayout.LayoutParams(-1, 0, 1f))
                addView(TextView(this@MainActivity).apply {
                    text = cardData.first; textSize = 14f; gravity = Gravity.CENTER
                    setTextColor(white); typeface = Typeface.DEFAULT_BOLD
                }, matchWrap())
                addView(TextView(this@MainActivity).apply {
                    text = cardData.second; textSize = 11f; gravity = Gravity.CENTER
                    setTextColor(muted); setPadding(0, dp(6), 0, 0)
                }, matchWrap())
                isFocusable = true
                setOnClickListener {
                    activeSection = section
                    selectedFolder = null
                    visibleLimit = 100
                    searchQuery = ""
                    showCatalog()
                }
            }
            cards.addView(card, LinearLayout.LayoutParams(0, -1, 1f).apply {
                marginEnd = if (section != "Séries") dp(12) else 0
            })
        }
        root.addView(cards, LinearLayout.LayoutParams(-1, dp(128)).apply { bottomMargin = dp(10) })

        val homeScroll = ScrollView(this).apply { isFillViewport = false; clipToPadding = false }
        val homeRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val favorites = loadFavoriteEntries()
        if (favorites.isNotEmpty()) addPosterRail(homeRows, "MEUS FAVORITOS", favorites)
        val newReleases = entries.filter { it.section == "Filmes" || it.section == "Séries" }
            .sortedWith(compareBy<MediaEntry> { if (it.addedAt.isBlank()) 1 else 0 }.thenByDescending { it.addedAt }).take(30)
        if (newReleases.isNotEmpty()) addPosterRail(homeRows, "ADICIONADOS RECENTEMENTE", newReleases)
        homeScroll.addView(homeRows)
        root.addView(homeScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun refreshCatalogManually() {
        if (isLoading) { toast("Aguarde a operação atual terminar."); return }
        val saved = loadSavedAccount()
        if (saved == null) { toast("Conecte sua conta para atualizar os catálogos."); showLogin(); return }
        toast("Atualizando catálogos do servidor...")
        connectXtream(saved.first, saved.second, saved.third)
    }

    private fun addPosterRail(parent: LinearLayout, title: String, items: List<MediaEntry>) {
        parent.addView(TextView(this).apply {
            text = title; setTextColor(white); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .06f; setPadding(dp(2), dp(8), 0, dp(8))
        }, matchWrap())
        val horizontal = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        items.distinctBy { favoriteKey(it) }.take(24).forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(5), dp(5), dp(5), dp(5))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(panel); cornerRadius = dp(10).toFloat(); setStroke(dp(1), Color.rgb(31, 57, 86))
                }
                val poster = ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(panel2)
                }
                addView(poster, LinearLayout.LayoutParams(dp(116), dp(142)))
                if (item.posterUrl.isNotBlank()) loadPoster(item.posterUrl, poster) else {
                    poster.setImageResource(when (item.section) {
                        "Canais ao vivo" -> android.R.drawable.ic_media_play
                        "Filmes" -> android.R.drawable.ic_menu_gallery
                        else -> android.R.drawable.ic_menu_slideshow
                    })
                    poster.scaleType = ImageView.ScaleType.CENTER
                }
                addView(TextView(this@MainActivity).apply {
                    text = item.name; setTextColor(white); textSize = 10f; maxLines = 2
                    setPadding(0, dp(5), 0, 0)
                }, LinearLayout.LayoutParams(dp(116), dp(34)))
                val pos = getPlaybackPosition(item)
                val duration = getPlaybackDuration(item)
                if (duration > 0 && pos > 0 && (item.section == "Filmes" || item.section == "Episódios")) {
                    val progressBar = ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 1000; progress = ((pos.toDouble() / duration) * 1000).toInt().coerceIn(0, 1000)
                        progressTintList = android.content.res.ColorStateList.valueOf(cyan)
                        progressBackgroundTintList = android.content.res.ColorStateList.valueOf(panel2)
                    }
                    addView(progressBar, LinearLayout.LayoutParams(dp(116), dp(3)).apply { topMargin = dp(3) })
                }
                setOnClickListener {
                    when (item.section) {
                        "Episódios", "Canais ao vivo", "Filmes" -> if (title == "CONTINUAR ASSISTINDO" || item.section == "Episódios") play(item) else openContentDetails(item)
                        else -> openContentDetails(item)
                    }
                }
                isFocusable = true
            }
            horizontal.addView(card, LinearLayout.LayoutParams(dp(126), -2).apply { marginEnd = dp(8) })
        }
        val horizontalScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; addView(horizontal)
        }
        parent.addView(horizontalScroll, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun favoriteKey(item: MediaEntry): String = "${item.section}|${item.itemId.ifBlank { item.name }}|${item.name}"

    private fun toggleFavorite(item: MediaEntry) {
        val prefs = getSharedPreferences("visionplay_library", MODE_PRIVATE)
        val saved = prefs.getStringSet("favorites", emptySet())?.toMutableSet() ?: mutableSetOf()
        val key = favoriteKey(item)
        if (!saved.add(key)) { saved.remove(key); toast("Removido dos favoritos") } else toast("Adicionado aos favoritos")
        prefs.edit().putStringSet("favorites", saved).apply()
    }

    private fun isFavorite(item: MediaEntry): Boolean = getSharedPreferences("visionplay_library", MODE_PRIVATE)
        .getStringSet("favorites", emptySet())?.contains(favoriteKey(item)) == true

    private fun loadFavoriteEntries(): List<MediaEntry> {
        val saved = getSharedPreferences("visionplay_library", MODE_PRIVATE).getStringSet("favorites", emptySet()) ?: emptySet()
        return entries.filter { favoriteKey(it) in saved }
    }

    private fun historyToken(item: MediaEntry): String = Base64.encodeToString(
        favoriteKey(item).toByteArray(StandardCharsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )

    private fun saveRecentEntry(item: MediaEntry) {
        val prefs = getSharedPreferences("visionplay_library", MODE_PRIVATE)
        val token = historyToken(item)
        val old = prefs.getString("recent_history", "").orEmpty().split("\n").filter { it.isNotBlank() && it != token }
        val history = (listOf(token) + old).take(120)
        // Do not persist stream URLs: they contain account credentials. Rebuild them after login.
        val safeItem = item.copy(url = "")
        prefs.edit()
            .putString("history_item_$token", com.google.gson.Gson().toJson(safeItem))
            .putString("recent_history", history.joinToString("\n"))
            .apply()
    }

    private fun savePlaybackProgress(item: MediaEntry, position: Long, duration: Long = player?.duration ?: 0L) {
        val prefs = getSharedPreferences("visionplay_library", MODE_PRIVATE)
        val token = historyToken(item)
        prefs.edit()
            .putString("resume_key", favoriteKey(item)) // backwards compatibility with earlier versions
            .putLong("resume_position", position)
            .putLong("progress_$token", position.coerceAtLeast(0L))
            .putLong("duration_$token", duration.coerceAtLeast(0L))
            .apply()
    }

    private fun getPlaybackPosition(item: MediaEntry): Long {
        val prefs = getSharedPreferences("visionplay_library", MODE_PRIVATE)
        val token = historyToken(item)
        if (prefs.contains("progress_$token")) return prefs.getLong("progress_$token", 0L)
        return if (prefs.getString("resume_key", null) == favoriteKey(item)) prefs.getLong("resume_position", 0L) else 0L
    }

    private fun getPlaybackDuration(item: MediaEntry): Long = getSharedPreferences("visionplay_library", MODE_PRIVATE)
        .getLong("duration_${historyToken(item)}", 0L)

    private fun loadRecentEntries(): List<MediaEntry> {
        val prefs = getSharedPreferences("visionplay_library", MODE_PRIVATE)
        val tokens = prefs.getString("recent_history", "").orEmpty().split("\n").filter { it.isNotBlank() }
        return tokens.mapNotNull { token ->
            val stored = runCatching {
                com.google.gson.Gson().fromJson(prefs.getString("history_item_$token", null), MediaEntry::class.java)
            }.getOrNull() ?: return@mapNotNull null
            val catalogMatch = entries.firstOrNull { favoriteKey(it) == favoriteKey(stored) }
            val item = catalogMatch ?: stored
            when (item.section) {
                "Canais ao vivo" -> item.copy(url = streamUrl("live", item.itemId, "ts"))
                "Filmes" -> item.copy(url = streamUrl("movie", item.itemId, item.extension.ifBlank { "mp4" }))
                "Episódios" -> item.copy(url = streamUrl("series", item.itemId, item.extension.ifBlank { "mp4" }))
                else -> item
            }
        }
    }

    private fun loadContinueWatchingEntries(): List<MediaEntry> = loadRecentEntries().filter { item ->
        (item.section == "Filmes" || item.section == "Episódios") && getPlaybackPosition(item) > 5000L && item.url.isNotBlank()
    }

    private fun loadPlaybackEntry(): MediaEntry? = loadContinueWatchingEntries().firstOrNull()

    private fun showCatalog() {
        detailScreenVisible = false
        fullScreenMode = false
        playbackHandler.removeCallbacks(playbackTicker)
        playerViewSafeDetach()
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        root = baseScreen()
        root.setPadding(dp(16), dp(10), dp(16), dp(10))

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(makeSmallButton("‹  INÍCIO") { selectedFolder = null; searchQuery = ""; showHome() })
        header.addView(TextView(this).apply {
            text = when (activeSection) { "Canais ao vivo" -> "CANAIS AO VIVO"; "Filmes" -> "FILMES"; "Séries" -> "SÉRIES"; else -> activeSection.uppercase() }
            textSize = 18f; setTextColor(white); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        header.addView(makeSmallButton("⌂") { selectedFolder = null; searchQuery = ""; showHome() })
        root.addView(header, matchWrap())

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Canais ao vivo", "Filmes", "Séries").forEach { section ->
            tabs.addView(makeSmallButton(when (section) { "Canais ao vivo" -> "AO VIVO"; else -> section.uppercase() }) {
                activeSection = section; selectedFolder = null; visibleLimit = 100; searchQuery = ""; showCatalog()
            }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(6) })
        }
        root.addView(tabs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        searchInput = input("Buscar nesta seção...", InputType.TYPE_CLASS_TEXT).apply {
            setText(searchQuery)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s?.toString().orEmpty(); selectedFolder = null; visibleLimit = 100
                    if (::contentBox.isInitialized) showSection(activeSection)
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        root.addView(searchInput, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(7); bottomMargin = dp(6) })
        contentBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        scroll.addView(contentBox)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        showSection(activeSection)
    }

    private var searchQuery: String = ""

    private fun searchInputTextReset() { searchQuery = "" }

    private val RECENT_FOLDER = "★ VISTOS RECENTEMENTE"

    private fun recentForSection(section: String): List<MediaEntry> = loadRecentEntries().filter { item ->
        when (section) {
            "Canais ao vivo" -> item.section == "Canais ao vivo"
            "Filmes" -> item.section == "Filmes"
            "Séries" -> item.section == "Séries" || item.section == "Episódios"
            else -> item.section == section
        }
    }.distinctBy { favoriteKey(it) }

    private fun showSection(section: String) {
        activeSection = section
        if (!::contentBox.isInitialized || fullScreenMode) return
        contentBox.removeAllViews()
        val all = if (section == "Episódios") episodeEntries.toList() else entries.filter { it.section == section }
        val query = searchQuery.trim()
        val filtered = if (query.isBlank()) all else all.filter {
            it.name.contains(query, true) || it.group.contains(query, true) || folderFor(it).contains(query, true)
        }
        contentBox.addView(TextView(this).apply {
            text = when (section) {
                "Canais ao vivo" -> "${all.size} canais disponíveis"
                "Filmes" -> "${all.size} filmes disponíveis"
                "Séries" -> "${all.size} séries disponíveis"
                else -> "$section  •  ${all.size} itens"
            }
            setTextColor(white); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(2), dp(9), 0, dp(9))
        }, matchWrap())

        if (section != "Episódios" && query.isBlank() && selectedFolder == null) {
            val recentCount = recentForSection(section).size
            if (recentCount > 0) {
                contentBox.addView(makeFolderRow("VISTOS RECENTEMENTE", recentCount, {
                    selectedFolder = RECENT_FOLDER
                    visibleLimit = 100
                    showSection(section)
                }), LinearLayout.LayoutParams(-1, dp(64)).apply { bottomMargin = dp(7) })
            }
        }

        if (section != "Episódios" && query.isBlank() && selectedFolder == RECENT_FOLDER) {
            contentBox.addView(makeSmallButton("‹  TODAS AS PASTAS") {
                selectedFolder = null
                showSection(section)
            }, LinearLayout.LayoutParams(-1, dp(40)).apply { bottomMargin = dp(8) })
            val recentItems = recentForSection(section)
            val page = recentItems.take(visibleLimit)
            page.forEach { item ->
                val progress = getPlaybackPosition(item)
                val duration = getPlaybackDuration(item)
                val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                row.addView(makeMediaRow(item), LinearLayout.LayoutParams(-1, dp(72)))
                if ((item.section == "Filmes" || item.section == "Episódios") && progress > 5000L) {
                    val progressText = if (duration > 0L) "Continuar • ${formatTime(progress)} / ${formatTime(duration)}" else "Continuar de onde parou"
                    row.addView(TextView(this).apply {
                        text = progressText
                        setTextColor(cyan); textSize = 11f
                        setPadding(dp(12), dp(3), dp(12), dp(4))
                        setOnClickListener { play(item) }
                    }, matchWrap())
                }
                contentBox.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
            }
            if (recentItems.size > page.size) contentBox.addView(makeFolderRow("CARREGAR MAIS", recentItems.size - page.size, {
                visibleLimit += 100
                showSection(section)
            }), LinearLayout.LayoutParams(-1, dp(58)))
            if (recentItems.isEmpty()) contentBox.addView(TextView(this).apply {
                text = "Você ainda não assistiu a conteúdos desta seção."
                setTextColor(muted); setPadding(dp(14), dp(18), dp(14), dp(18)); setBackgroundColor(panel)
            }, matchWrap())
            return
        }

        if (section == "Episódios") {
            val series = activeSeriesForEpisodes
            contentBox.addView(TextView(this).apply {
                text = series?.name ?: "Episódios da série"
                setTextColor(cyan); textSize = 15f; typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(2), dp(4), 0, dp(10))
            }, matchWrap())
            if (selectedSeason == null) {
                val seasons = all.groupBy { it.group }.entries.sortedBy { seasonNumber(it.key) }
                if (seasons.isEmpty()) {
                    contentBox.addView(TextView(this).apply {
                        text = "O servidor não retornou episódios para esta série. Tente novamente ou confira se a API disponibiliza os episódios."
                        setTextColor(muted); textSize = 14f; setPadding(dp(14), dp(18), dp(14), dp(18)); setBackgroundColor(panel)
                    }, matchWrap())
                } else {
                    contentBox.addView(TextView(this).apply {
                        text = "ESCOLHA UMA TEMPORADA"; setTextColor(muted); textSize = 11f; typeface = Typeface.DEFAULT_BOLD
                        setPadding(dp(2), dp(4), 0, dp(8))
                    }, matchWrap())
                    seasons.forEach { season ->
                        contentBox.addView(makeSeasonCard(season.key, season.value.size, series?.posterUrl.orEmpty()) {
                            selectedSeason = season.key
                            showSection("Episódios")
                        }, LinearLayout.LayoutParams(-1, dp(74)).apply { bottomMargin = dp(8) })
                    }
                }
            } else {
                contentBox.addView(makeSmallButton("‹  TODAS AS TEMPORADAS") {
                    selectedSeason = null
                    showSection("Episódios")
                }, LinearLayout.LayoutParams(-1, dp(40)).apply { bottomMargin = dp(8) })
                val seasonEpisodes = all.filter { it.group == selectedSeason }
                    .sortedWith(compareBy<MediaEntry> { episodeNumber(it.name) }.thenBy { it.name })
                seasonEpisodes.forEach { episode ->
                    contentBox.addView(makeMediaRow(episode), LinearLayout.LayoutParams(-1, dp(72)).apply { bottomMargin = dp(7) })
                }
                if (seasonEpisodes.isEmpty()) {
                    contentBox.addView(TextView(this).apply { text = "Nenhum episódio encontrado nesta temporada."; setTextColor(muted); setPadding(dp(12), dp(16), dp(12), dp(16)) }, matchWrap())
                }
            }
            return
        }

        if (filtered.isEmpty()) {
            contentBox.addView(TextView(this).apply {
                text = if (all.isEmpty()) "O servidor não disponibilizou itens nesta seção. Confira se seu plano inclui este catálogo." else "Nenhum resultado encontrado. Tente outro termo."
                setTextColor(muted); textSize = 14f; setPadding(dp(14), dp(18), dp(14), dp(18)); setBackgroundColor(panel)
            }, matchWrap())
            return
        }
        if (query.isBlank() && selectedFolder == null && section != "Episódios") {
            val folders = filtered.groupBy { folderFor(it) }.entries.sortedWith(Comparator { a, b ->
                val priorityDifference = folderPriority(a.key) - folderPriority(b.key)
                if (priorityDifference != 0) priorityDifference
                else if (a.value.size != b.value.size) b.value.size.compareTo(a.value.size)
                else a.key.compareTo(b.key, ignoreCase = true)
            })
            folders.forEach { folderEntry ->
                contentBox.addView(makeFolderRow(folderEntry.key, folderEntry.value.size, {
                    selectedFolder = folderEntry.key; visibleLimit = 100; showSection(section)
                }, folderEntry.value.firstOrNull { it.posterUrl.isNotBlank() }?.posterUrl.orEmpty()), LinearLayout.LayoutParams(-1, dp(64)).apply { bottomMargin = dp(6) })
            }
            return
        }
        if (query.isBlank() && selectedFolder != null) {
            contentBox.addView(makeSmallButton("‹  TODAS AS PASTAS") { selectedFolder = null; showSection(section) },
                LinearLayout.LayoutParams(-1, dp(40)).apply { bottomMargin = dp(8) })
        }
        val visible = if (query.isBlank() && selectedFolder != null) filtered.filter { folderFor(it) == selectedFolder } else filtered
        val page = visible.take(visibleLimit)
        page.forEach { item -> contentBox.addView(makeMediaRow(item), LinearLayout.LayoutParams(-1, dp(72)).apply { bottomMargin = dp(6) }) }
        if (visible.size > page.size) contentBox.addView(makeFolderRow("Carregar mais conteúdos", visible.size - page.size, action = {
            visibleLimit += 100; showSection(section)
        }), LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(4); bottomMargin = dp(8) })
        if (page.isEmpty() && visible.isEmpty()) contentBox.addView(TextView(this).apply {
            text = "Nenhum conteúdo nesta pasta."; setTextColor(muted); textSize = 14f
            setPadding(dp(14), dp(18), dp(14), dp(18)); setBackgroundColor(panel)
        }, matchWrap())
    }

    private fun seasonNumber(value: String): Int = Regex("\\d+").find(value)?.value?.toIntOrNull() ?: Int.MAX_VALUE

    private fun episodeNumber(value: String): Int {
        val patterns = listOf(Regex("(?i)\\bS\\d+\\s*E(\\d+)\\b"), Regex("(?i)epis[oó]dio\\s*(\\d+)"), Regex("(?i)\\bE(\\d+)\\b"))
        return patterns.firstNotNullOfOrNull { it.find(value)?.groupValues?.getOrNull(1)?.toIntOrNull() } ?: Int.MAX_VALUE
    }

    private fun makeSeasonCard(name: String, count: Int, posterUrl: String, action: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(8), dp(12), dp(8))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(panel); cornerRadius = dp(12).toFloat(); setStroke(dp(1), Color.rgb(36, 62, 91))
        }
        val thumb = ImageView(this@MainActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(panel2) }
        addView(thumb, LinearLayout.LayoutParams(dp(54), dp(54)).apply { marginEnd = dp(12) })
        if (posterUrl.isNotBlank()) loadPoster(posterUrl, thumb) else thumb.setImageResource(android.R.drawable.ic_menu_gallery)
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply { text = name; setTextColor(white); textSize = 15f; typeface = Typeface.DEFAULT_BOLD }, matchWrap())
            addView(TextView(this@MainActivity).apply { text = "$count episódios"; setTextColor(muted); textSize = 12f; setPadding(0, dp(4), 0, 0) }, matchWrap())
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(this@MainActivity).apply { text = "›"; setTextColor(cyan); textSize = 28f }, matchWrap())
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun folderPriority(name: String): Int {
        val value = name.lowercase()
        return when {
            listOf("adicionados recentemente", "adicionado recentemente", "recentes", "recentemente adicionados", "recently added", "recent additions").any { it in value } -> 0
            listOf("lançamento", "lancamento", "lançamentos", "lancamentos", "novidades", "new releases", "new movies", "últimos lançamentos", "ultimos lancamentos").any { it in value } -> 1
            else -> 2
        }
    }

    private fun folderFor(item: MediaEntry): String {
        val text = (item.name + " " + item.group).lowercase()
        return when (item.section) {
            "Canais ao vivo" -> {
                val station = when {
                    Regex("\\b(globo|rede globo)\\b").containsMatchIn(text) -> "Globo"
                    Regex("\\b(sbt)\\b").containsMatchIn(text) -> "SBT"
                    Regex("\\b(record|record tv)\\b").containsMatchIn(text) -> "Record"
                    Regex("\\b(band|bandeirantes)\\b").containsMatchIn(text) -> "Band"
                    Regex("\\b(rede tv|redetv)\\b").containsMatchIn(text) -> "RedeTV!"
                    Regex("\\b(cultura)\\b").containsMatchIn(text) -> "TV Cultura"
                    Regex("\\b(espn)\\b").containsMatchIn(text) -> "ESPN"
                    Regex("\\b(sportv|spor tv)\\b").containsMatchIn(text) -> "SporTV"
                    Regex("\\b(fox sports)\\b").containsMatchIn(text) -> "Fox Sports"
                    Regex("\\b(telecine)\\b").containsMatchIn(text) -> "Telecine"
                    Regex("\\b(hbo)\\b").containsMatchIn(text) -> "HBO"
                    Regex("\\b(discovery)\\b").containsMatchIn(text) -> "Discovery"
                    Regex("\\b(cartoon|nickelodeon|disney junior|gloob)\\b").containsMatchIn(text) -> "Infantil"
                    else -> item.group.ifBlank { "Outros canais" }
                }
                val region = when {
                    Regex("(capital|nacional|s\u00e3o paulo capital|rio capital)").containsMatchIn(text) -> "Capital / Nacional"
                    Regex("(regional|interior|interiorana|local| mg|\\bmg\\b|\\bsp\\b|\\brj\\b|\\bba\\b|\\bpr\\b|\\bsc\\b|\\brs\\b|\\bgo\\b|\\bpe\\b|\\bce\\b)").containsMatchIn(text) -> "Regional"
                    else -> "Geral"
                }
                "$station • $region"
            }
            "Filmes" -> item.group.ifBlank { "Outros filmes" }
            "Séries" -> {
                val platform = when {
                    "netflix" in text -> "Netflix"
                    "hbo" in text || "max original" in text || Regex("\\bmax\\b").containsMatchIn(text) -> "HBO / Max"
                    "prime video" in text || "amazon" in text -> "Prime Video / Amazon"
                    "globoplay" in text || "globo play" in text -> "Globoplay"
                    "disney+" in text || "disney plus" in text -> "Disney+"
                    "paramount" in text -> "Paramount+"
                    "apple tv" in text -> "Apple TV+"
                    "crunchyroll" in text -> "Crunchyroll"
                    "peacock" in text -> "Peacock"
                    "star+" in text || "star plus" in text -> "Star+"
                    else -> "Outras séries • ${item.group.ifBlank { "Sem categoria" }}"
                }
                platform
            }
            else -> item.group.ifBlank { "Outros" }
        }
    }

    private fun makeFolderRow(name: String, count: Int, action: () -> Unit, iconUrl: String = ""): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(8), dp(12), dp(8)); setBackgroundColor(panel)
        val symbol = ImageView(this@MainActivity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = android.graphics.drawable.GradientDrawable().apply { setColor(panel2); cornerRadius = dp(8).toFloat() }
            val brandLogo = when {
                name.contains("Netflix", true) -> "https://logo.clearbit.com/netflix.com"
                name.contains("HBO", true) || name.contains("Max", true) -> "https://logo.clearbit.com/max.com"
                name.contains("Prime Video", true) || name.contains("Amazon", true) -> "https://logo.clearbit.com/primevideo.com"
                name.contains("Globoplay", true) || name.contains("Globo Play", true) -> "https://logo.clearbit.com/globoplay.globo.com"
                name.contains("Disney", true) -> "https://logo.clearbit.com/disneyplus.com"
                name.contains("Paramount", true) -> "https://logo.clearbit.com/paramountplus.com"
                name.contains("Apple TV", true) -> "https://logo.clearbit.com/tv.apple.com"
                name.contains("Crunchyroll", true) -> "https://logo.clearbit.com/crunchyroll.com"
                name.contains("Globo", true) -> "https://logo.clearbit.com/globo.com"
                name.contains("SBT", true) -> "https://logo.clearbit.com/sbt.com.br"
                name.contains("Record", true) -> "https://logo.clearbit.com/r7.com"
                name.contains("Band", true) -> "https://logo.clearbit.com/band.uol.com.br"
                else -> ""
            }
            val chosenLogo = iconUrl.ifBlank { brandLogo }
            if (chosenLogo.isNotBlank()) loadPoster(chosenLogo, this)
            else setImageResource(when {
                name.contains("Carregar mais", true) -> android.R.drawable.ic_input_add
                else -> android.R.drawable.ic_menu_gallery
            })
        }
        addView(symbol, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = name; setTextColor(white); textSize = 14f; typeface = Typeface.DEFAULT_BOLD; maxLines = 2
            }, matchWrap())
            addView(TextView(this@MainActivity).apply {
                text = "$count conteúdos"; setTextColor(muted); textSize = 11f; setPadding(0, dp(3), 0, 0)
            }, matchWrap())
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(this@MainActivity).apply { text = "›"; textSize = 28f; setTextColor(cyan) })
        setOnClickListener { action() }
    }

    private fun makeMediaRow(item: MediaEntry): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(6), dp(8), dp(6))
        setBackgroundColor(panel)
        val thumbnail = ImageView(this@MainActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(panel2) }
        addView(thumbnail, LinearLayout.LayoutParams(dp(44), dp(54)).apply { marginEnd = dp(10) })
        if (item.posterUrl.isNotBlank()) loadPoster(item.posterUrl, thumbnail) else {
            thumbnail.setImageResource(when (item.section) { "Canais ao vivo" -> android.R.drawable.ic_media_play; "Filmes" -> android.R.drawable.ic_menu_gallery; else -> android.R.drawable.ic_menu_slideshow })
            thumbnail.scaleType = ImageView.ScaleType.CENTER
        }
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = item.name; setTextColor(white); textSize = 13f; maxLines = 2; typeface = Typeface.DEFAULT_BOLD
            }, matchWrap())
            addView(TextView(this@MainActivity).apply {
                text = when (item.section) { "Séries" -> "Série • toque para ver temporadas"; "Episódios" -> item.group; else -> item.group }
                setTextColor(muted); textSize = 10f; maxLines = 1; setPadding(0, dp(2), 0, 0)
            }, matchWrap())
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(makeSmallButton(when (item.section) { "Séries" -> "TEMPORADAS"; "Episódios" -> "ASSISTIR"; else -> "DETALHES" }) {
            if (item.section == "Episódios") play(item) else openContentDetails(item)
        })
        setOnClickListener { if (item.section == "Episódios") play(item) else openContentDetails(item) }
    }

    private fun openContentDetails(item: MediaEntry) {
        if (isLoading) return
        isLoading = true
        // Keep the current catalog visible while querying metadata; opening a title must not
        // look like a full catalog refresh. Only the explicit refresh button reloads catalogs.
        io.execute {
            try {
                var enriched = item
                when (item.section) {
                    "Filmes" -> {
                        val obj = runCatching { fetchJsonObject(apiUrl("get_vod_info", "vod_id", item.itemId)) }.getOrNull()
                        if (obj != null) {
                        val info = obj.getAsJsonObject("info") ?: JsonObject()
                        val data = obj.getAsJsonObject("movie_data") ?: JsonObject()
                        enriched = item.copy(
                            plot = firstNonBlank(info, "plot", "description").ifBlank { item.plot },
                            releaseDate = firstNonBlank(info, "release_date", "releaseDate", "releasedate").ifBlank { item.releaseDate },
                            year = firstNonBlank(info, "year").ifBlank { item.year },
                            director = firstNonBlank(info, "director").ifBlank { item.director },
                            cast = firstNonBlank(info, "cast", "actors").ifBlank { item.cast },
                            posterUrl = firstNonBlank(info, "movie_image", "cover_big", "cover").ifBlank { item.posterUrl },
                            addedAt = firstNonBlank(data, "added").let { formatAddedDate(it) }.ifBlank { item.addedAt },
                            group = firstNonBlank(info, "genre").ifBlank { item.group }
                        )
                        }
                    }
                    "Séries" -> {
                        val obj = runCatching { fetchJsonObject(apiUrl("get_series_info", "series_id", item.seriesId)) }.getOrNull()
                        if (obj != null) {
                        val info = obj.getAsJsonObject("info") ?: JsonObject()
                        enriched = item.copy(
                            plot = firstNonBlank(info, "plot", "description").ifBlank { item.plot },
                            releaseDate = firstNonBlank(info, "releaseDate", "release_date", "releasedate").ifBlank { item.releaseDate },
                            year = firstNonBlank(info, "year").ifBlank { item.year },
                            director = firstNonBlank(info, "director").ifBlank { item.director },
                            cast = firstNonBlank(info, "cast", "actors").ifBlank { item.cast },
                            posterUrl = firstNonBlank(info, "cover", "cover_big", "movie_image").ifBlank { item.posterUrl },
                            group = firstNonBlank(info, "genre").ifBlank { item.group }
                        )
                        }
                    }
                    "Canais ao vivo" -> {
                        val obj = runCatching { fetchJsonObject(apiUrl("get_short_epg", "stream_id", item.itemId)) }.getOrNull()
                        val epg = obj?.getAsJsonArray("epg_list")
                        val programs = mutableListOf<EpgProgram>()
                        if (epg != null) for (row in epg) {
                            if (!row.isJsonObject) continue
                            val e = row.asJsonObject
                            val title = firstNonBlank(e, "title", "name")
                            if (title.isBlank()) continue
                            val start = firstNonBlank(e, "start", "start_time", "start_timestamp")
                            val end = firstNonBlank(e, "end", "end_time", "stop_timestamp")
                            val time = listOf(start, end).filter { it.isNotBlank() }.joinToString(" – ")
                            programs.add(EpgProgram(title, time, firstNonBlank(e, "description", "plot")))
                        }
                        val now = programs.getOrNull(0)
                        val next = programs.getOrNull(1)
                        enriched = item.copy(
                            currentProgram = now?.title.orEmpty(),
                            currentProgramTime = now?.time.orEmpty(),
                            currentProgramDescription = now?.description.orEmpty(),
                            nextProgram = next?.title.orEmpty(),
                            nextProgramTime = next?.time.orEmpty()
                        )
                    }
                }
                val finalItem = enriched
                runOnUiThread {
                    isLoading = false
                    if (!isFinishing) showContentDetails(finalItem)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    isLoading = false
                    showCatalog()
                    toast("Não foi possível carregar os detalhes: ${safeError(e)}")
                }
            }
        }
    }

    private fun firstNonBlank(obj: JsonObject, vararg keys: String): String {
        for (key in keys) {
            val value = obj.get(key) ?: continue
            if (!value.isJsonNull) {
                val text = runCatching { value.asString }.getOrDefault("").trim()
                if (text.isNotBlank() && text != "null") return text
            }
        }
        return ""
    }

    private fun formatAddedDate(value: String): String {
        val raw = value.trim()
        if (raw.isBlank() || raw == "0") return ""
        val epoch = raw.toLongOrNull()
        if (epoch != null && raw.length >= 9) {
            return runCatching {
                java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(if (epoch > 9999999999L) epoch else epoch * 1000L))
            }.getOrDefault(raw)
        }
        return raw
    }

    private fun showContentDetails(item: MediaEntry) {
        detailScreenVisible = true
        fullScreenMode = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        root = baseScreen().apply { setPadding(dp(18), dp(10), dp(18), dp(12)) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(makeSmallButton("‹  VOLTAR") { detailScreenVisible = false; showCatalog() })
        header.addView(TextView(this).apply {
            text = when (item.section) { "Canais ao vivo" -> "DETALHES DO CANAL"; "Filmes" -> "DETALHES DO FILME"; else -> "DETALHES DA SÉRIE" }
            setTextColor(white); textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        root.addView(header, matchWrap())
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, dp(12)) }
        val hero = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP; setPadding(dp(12), dp(12), dp(12), dp(12)); setBackgroundColor(panel) }
        if (item.posterUrl.isNotBlank()) {
            val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(panel2) }
            hero.addView(image, LinearLayout.LayoutParams(dp(128), dp(184)).apply { marginEnd = dp(14) })
            loadPoster(item.posterUrl, image)
        } else {
            hero.addView(TextView(this).apply { text = if (item.section == "Canais ao vivo") "●" else "▶"; textSize = 42f; gravity = Gravity.CENTER; setTextColor(cyan); setBackgroundColor(panel2) }, LinearLayout.LayoutParams(dp(84), dp(110)).apply { marginEnd = dp(14) })
        }
        val facts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        facts.addView(TextView(this@MainActivity).apply { text = item.name; setTextColor(white); textSize = 21f; typeface = Typeface.DEFAULT_BOLD }, matchWrap())
        val meta = listOfNotNull(
            item.year.takeIf { it.isNotBlank() }?.let { "Ano: $it" },
            item.releaseDate.takeIf { it.isNotBlank() }?.let { "Lançamento: $it" },
            item.addedAt.takeIf { it.isNotBlank() }?.let { "Adicionado: $it" },
            item.group.takeIf { it.isNotBlank() }?.let { "Categoria: $it" },
            item.director.takeIf { it.isNotBlank() }?.let { "Direção: $it" },
            item.cast.takeIf { it.isNotBlank() }?.let { "Elenco: $it" }
        ).joinToString("\n")
        if (meta.isNotBlank()) facts.addView(TextView(this@MainActivity).apply { text = meta; setTextColor(muted); textSize = 12f; setPadding(0, dp(9), 0, 0) }, matchWrap())
        hero.addView(facts, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(hero, matchWrap())
        if (item.section == "Canais ao vivo") {
            body.addView(sectionLabel("●  NO AR / PROGRAMAÇÃO"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
            body.addView(TextView(this).apply {
                text = if (item.currentProgram.isNotBlank()) "AGORA: ${item.currentProgram}" + item.currentProgramTime.takeIf { it.isNotBlank() }?.let { "  •  $it" }.orEmpty() else "A programação atual não foi disponibilizada pelo servidor."
                setTextColor(white); textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setPadding(dp(12), dp(12), dp(12), dp(8)); setBackgroundColor(panel)
            }, matchWrap())
            if (item.currentProgramDescription.isNotBlank()) body.addView(TextView(this).apply { text = item.currentProgramDescription; setTextColor(muted); textSize = 13f; setPadding(dp(12), dp(4), dp(12), dp(12)); setBackgroundColor(panel) }, matchWrap())
            if (item.nextProgram.isNotBlank()) body.addView(TextView(this).apply { text = "A SEGUIR: ${item.nextProgram}" + item.nextProgramTime.takeIf { it.isNotBlank() }?.let { "  •  $it" }.orEmpty(); setTextColor(cyan); textSize = 13f; setPadding(dp(12), dp(10), dp(12), dp(10)) }, matchWrap())
        } else {
            body.addView(sectionLabel("SINOPSE"), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
            body.addView(TextView(this).apply {
                text = item.plot.ifBlank { "O servidor não forneceu sinopse para este título." }
                setTextColor(if (item.plot.isBlank()) muted else white); textSize = 14f; setLineSpacing(dp(3).toFloat(), 1f)
                setPadding(dp(14), dp(12), dp(14), dp(12)); setBackgroundColor(panel)
            }, matchWrap())
        }
        body.addView(makeSmallButton(if (isFavorite(item)) "♥  REMOVER DOS FAVORITOS" else "♡  ADICIONAR AOS FAVORITOS") {
            toggleFavorite(item)
            showContentDetails(item)
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(12) })
        val actionLabel = when (item.section) { "Canais ao vivo" -> "▶  ASSISTIR CANAL"; "Filmes" -> "▶  ASSISTIR FILME"; else -> "▤  VER TEMPORADAS E EPISÓDIOS" }
        body.addView(makeButton(actionLabel) {
            detailScreenVisible = false
            if (item.section == "Séries") loadSeriesEpisodes(item) else play(item)
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(18) })
        body.addView(makeSmallButton("VOLTAR AO CATÁLOGO") { detailScreenVisible = false; showCatalog() }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun loadPoster(url: String, target: ImageView) {
        imageIo.execute {
            try {
                val connection = openConnection(url)
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                val bitmap = connection.inputStream.use { BitmapFactory.decodeStream(it) }
                connection.disconnect()
                if (bitmap != null) runOnUiThread { if (!isFinishing) target.setImageBitmap(bitmap) }
            } catch (_: Exception) { }
        }
    }

    private fun loadSeriesEpisodes(series: MediaEntry) {
        if (isLoading) return
        isLoading = true
        // Keep the series catalog visible while episode metadata is fetched.
        io.execute {
            try {
                val obj = fetchJsonObject(apiUrl("get_series_info", "series_id", series.seriesId))
                val info = obj.getAsJsonObject("info")
                val enrichedSeries = series.copy(
                    plot = info?.stringValue("plot")?.ifBlank { series.plot } ?: series.plot,
                    releaseDate = info?.stringValue("releaseDate")?.ifBlank { series.releaseDate } ?: series.releaseDate,
                    year = info?.stringValue("year")?.ifBlank { series.year } ?: series.year,
                    director = info?.stringValue("director")?.ifBlank { series.director } ?: series.director,
                    cast = info?.stringValue("cast")?.ifBlank { series.cast } ?: series.cast
                )
                val episodesRoot = obj.getAsJsonObject("episodes")
                    ?: throw IllegalStateException("O servidor não retornou episódios para esta série.")
                val episodes = mutableListOf<MediaEntry>()
                for ((season, array) in episodesRoot.entrySet().sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }) {
                    if (!array.isJsonArray) continue
                    for (episode in array.asJsonArray) {
                        if (!episode.isJsonObject) continue
                        val ep = episode.asJsonObject
                        val id = ep.stringValue("id")
                        val title = ep.stringValue("title").ifBlank { "Temporada $season • Episódio ${episodes.size + 1}" }
                        val ext = ep.stringValue("container_extension").ifBlank { "mp4" }
                        val epInfo = ep.getAsJsonObject("info")
                        val epPoster = firstNonBlank(ep, "movie_image", "cover_big", "cover", "stream_icon")
                            .ifBlank { firstNonBlank(epInfo ?: JsonObject(), "movie_image", "cover_big", "cover") }
                            .ifBlank { enrichedSeries.posterUrl }
                        val epPlot = firstNonBlank(ep, "plot", "description").ifBlank { firstNonBlank(epInfo ?: JsonObject(), "plot", "description") }.ifBlank { enrichedSeries.plot }
                        val epDate = firstNonBlank(ep, "releaseDate", "release_date").ifBlank { firstNonBlank(epInfo ?: JsonObject(), "releaseDate", "release_date") }.ifBlank { enrichedSeries.releaseDate }
                        if (id.isNotBlank()) episodes.add(MediaEntry(
                            title, streamUrl("series", id, ext), "Temporada $season", "Episódios", id, ext,
                            series.seriesId, epDate, epPlot, enrichedSeries.year,
                            enrichedSeries.director, enrichedSeries.cast, epPoster
                        ))
                    }
                }
                runOnUiThread {
                    isLoading = false
                    if (episodes.isEmpty()) {
                        showCatalog()
                        toast("Nenhum episódio foi retornado para esta série.")
                    } else {
                        activeSeriesForEpisodes = enrichedSeries
                        episodeEntries.clear()
                        episodeEntries.addAll(episodes)
                        selectedSeason = null
                        activeSection = "Episódios"
                        selectedFolder = null
                        showCatalog()
                        showSection("Episódios")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { isLoading = false; showCatalog(); toast(safeError(e)) }
            }
        }
    }

    private fun JsonObject.stringValue(key: String): String {
        val value = get(key) ?: return ""
        return if (value.isJsonNull) "" else runCatching { value.asString }.getOrDefault("")
    }

    private fun play(item: MediaEntry) {
        if (item.url.isBlank()) { toast("Este item não possui um endereço de reprodução disponível."); return }
        // Persist the previous item's position before currentPlaying changes or the player stops.
        currentPlaying?.let { previous -> player?.let { oldPlayer ->
            if (previous.section == "Filmes" || previous.section == "Episódios") {
                savePlaybackProgress(previous, oldPlayer.currentPosition, oldPlayer.duration)
            }
        } }
        currentPlaying = item
        currentPlaybackQueue = (if (item.section == "Episódios") episodeEntries else entries.filter { it.section == item.section }).filter { it.url.isNotBlank() }
        currentPlaybackIndex = currentPlaybackQueue.indexOfFirst { it.url == item.url }
        try {
            val currentPlayer = player ?: run { initializePlayer(); player ?: throw IllegalStateException("Player indisponível") }
            currentPlayer.stop(); currentPlayer.clearMediaItems()
            currentPlayer.setMediaItem(MediaItem.Builder().setUri(Uri.parse(item.url)).build())
            val savedPosition = getPlaybackPosition(item)
            saveRecentEntry(item)
            currentPlayer.prepare()
            if (savedPosition > 5000L) currentPlayer.seekTo(savedPosition)
            currentPlayer.playWhenReady = true
            savePlaybackProgress(item, savedPosition, getPlaybackDuration(item))
            showPlaybackScreen()
        } catch (e: Exception) { toast("Não foi possível abrir este conteúdo: ${e.message.orEmpty().take(100)}") }
    }

    private fun showPlaybackScreen() {
        fullScreenMode = true
        playbackControlsVisible = true
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); setPadding(0, 0, 0, 0) }
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            useController = false
            setBackgroundColor(Color.BLACK)
            player = this@MainActivity.player
        }
        playerView.setOnClickListener { togglePlaybackControls() }
        frame.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xCC050911.toInt()); cornerRadius = dp(14).toFloat()
            }
        }
        playbackOverlay = overlay
        val item = currentPlaying
        overlay.addView(TextView(this).apply {
            text = item?.name.orEmpty(); textSize = 20f; setTextColor(white); typeface = Typeface.DEFAULT_BOLD
        }, matchWrap())
        val meta = listOfNotNull(
            item?.releaseDate?.takeIf { it.isNotBlank() }?.let { "Data: $it" },
            item?.year?.takeIf { it.isNotBlank() }?.let { "Ano: $it" },
            item?.director?.takeIf { it.isNotBlank() }?.let { "Direção: $it" },
            item?.cast?.takeIf { it.isNotBlank() }?.let { "Elenco: $it" },
            item?.currentProgram?.takeIf { it.isNotBlank() }?.let { "No ar: $it ${item.currentProgramTime}" }
        ).joinToString("  •  ")
        if (meta.isNotBlank()) overlay.addView(TextView(this).apply {
            text = meta; textSize = 11f; setTextColor(muted); setPadding(0, dp(4), 0, dp(2)); maxLines = 2
        }, matchWrap())
        if (!item?.plot.isNullOrBlank()) overlay.addView(TextView(this).apply {
            text = item?.plot; textSize = 12f; setTextColor(white); maxLines = 3; setPadding(0, dp(5), 0, dp(4))
        }, matchWrap())

        val seekRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        playbackSeekBar = SeekBar(this).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(cyan)
            thumbTintList = android.content.res.ColorStateList.valueOf(white)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) playbackTimeLabel?.text = "${formatTime((player?.duration?.coerceAtLeast(0L) ?: 0L) * progress / 1000)} / ${player?.duration?.takeIf { it > 0 }?.let { formatTime(it) } ?: "--:--"}"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) { playbackControlsVisible = true }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val duration = player?.duration?.coerceAtLeast(0L) ?: 0L
                    if (duration > 0) player?.seekTo(duration * (seekBar?.progress ?: 0) / 1000)
                }
            })
        }
        seekRow.addView(playbackSeekBar, LinearLayout.LayoutParams(0, dp(36), 1f))
        playbackTimeLabel = TextView(this).apply { text = "00:00 / --:--"; textSize = 11f; setTextColor(white); setPadding(dp(8), 0, 0, 0) }
        seekRow.addView(playbackTimeLabel, LinearLayout.LayoutParams(-2, -2))
        overlay.addView(seekRow, matchWrap())

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        controls.addView(makeSmallButton("↶ 15s") { player?.let { it.seekTo((it.currentPosition - 15000).coerceAtLeast(0L)) } }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        controls.addView(makeSmallButton("Ⅱ / ▶") { player?.let { if (it.isPlaying) it.pause() else it.play() } }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        controls.addView(makeSmallButton("15s ↷") { player?.let { exo -> exo.seekTo((exo.currentPosition + 15000).coerceAtMost(exo.duration.coerceAtLeast(0L))) } }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) })
        controls.addView(makeSmallButton(if (item?.section == "Canais ao vivo") "PRÓX. CANAL  ›" else "PRÓXIMO  ›") { playNext() }, LinearLayout.LayoutParams(0, dp(44), 1.2f))
        overlay.addView(controls, matchWrap())
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bottom.addView(TextView(this).apply {
            text = "Toque no vídeo para mostrar/ocultar controles"; textSize = 10f; setTextColor(muted)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        bottom.addView(makeSmallButton("VOLTAR À LISTA") {
            fullScreenMode = false; player?.pause(); playbackHandler.removeCallbacks(playbackTicker); showCatalog()
        })
        overlay.addView(bottom, LinearLayout.LayoutParams(-1, dp(38)))
        frame.addView(overlay, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            leftMargin = dp(12); rightMargin = dp(12); bottomMargin = dp(12)
        })
        root.addView(frame, LinearLayout.LayoutParams(-1, -1))
        setContentView(root)
        playbackHandler.removeCallbacks(playbackTicker)
        playbackHandler.post(playbackTicker)
    }

    private fun togglePlaybackControls() {
        playbackControlsVisible = !playbackControlsVisible
        playbackOverlay?.visibility = if (playbackControlsVisible) View.VISIBLE else View.GONE
        if (playbackControlsVisible) playbackHandler.removeCallbacks(playbackTicker).also { playbackHandler.post(playbackTicker) }
    }

    private fun playNext() {
        if (currentPlaybackQueue.isEmpty()) { toast("Não há outro conteúdo nesta lista."); return }
        val next = (currentPlaybackIndex + 1).coerceAtMost(currentPlaybackQueue.lastIndex)
        if (next == currentPlaybackIndex) { toast("Você chegou ao último conteúdo desta lista."); return }
        play(currentPlaybackQueue[next])
    }

    private fun formatTime(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0L)
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
            else String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun saveAccount(server: String, user: String, pass: String) {
        val alias = "visionplay_saved_account_key"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        var key = store.getKey(alias, null) as? SecretKey
        if (key == null) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
            key = generator.generateKey()
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(pass.toByteArray(Charsets.UTF_8))
        getSharedPreferences("visionplay_account", MODE_PRIVATE).edit()
            .putString("server", server)
            .putString("username", user)
            .putString("password_enc", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("password_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    private fun loadSavedAccount(): Triple<String, String, String>? {
        return try {
            val prefs = getSharedPreferences("visionplay_account", MODE_PRIVATE)
            val server = prefs.getString("server", null) ?: return null
            val user = prefs.getString("username", null) ?: return null
            val encryptedText = prefs.getString("password_enc", null) ?: return null
            val ivText = prefs.getString("password_iv", null) ?: return null
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = store.getKey("visionplay_saved_account_key", null) as? SecretKey ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)))
            val pass = String(cipher.doFinal(Base64.decode(encryptedText, Base64.NO_WRAP)), Charsets.UTF_8)
            if (pass.isBlank()) null else Triple(server, user, pass)
        } catch (_: Exception) { null }
    }

    private fun loadM3uFile(uri: Uri) {
        if (isLoading) return
        isLoading = true
        showLoading("Importando lista M3U...")
        io.execute {
            try {
                val found = mutableListOf<MediaEntry>()
                var name = "Conteúdo"
                var group = "Outros"
                contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                    lines.forEach { raw ->
                        val value = raw.trim().removePrefix("\uFEFF")
                        if (value.startsWith("#EXTINF", true)) {
                            name = value.substringAfter(",", "Conteúdo").trim().ifBlank { "Conteúdo" }
                            group = Regex("""group-title=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                                .find(value)?.groupValues?.getOrNull(1) ?: "Outros"
                        } else if (value.startsWith("http://", true) || value.startsWith("https://", true)) {
                            found.add(MediaEntry(name, value, group, "Canais ao vivo"))
                            name = "Conteúdo"; group = "Outros"
                        }
                    }
                } ?: throw IllegalStateException("Não foi possível abrir o arquivo.")
                if (found.isEmpty()) throw IllegalStateException("Nenhum endereço de mídia foi encontrado.")
                runOnUiThread {
                    isLoading = false
                    entries.clear(); entries.addAll(found)
                    activeSection = "INICIO"
                    showHome()
                    toast("Lista importada: ${found.size} itens")
                }
            } catch (e: Exception) {
                runOnUiThread { isLoading = false; showLogin(safeError(e)) }
            }
        }
    }

    private fun safeError(error: Throwable): String = when (error) {
        is java.net.SocketTimeoutException -> "O servidor demorou para responder. Tente novamente."
        is java.net.UnknownHostException -> "Servidor não encontrado. Verifique a URL e a conexão."
        is java.io.IOException -> "Falha de rede ao consultar o servidor. Verifique a conexão."
        else -> error.message?.take(220) ?: "Não foi possível carregar o conteúdo."
    }

    private fun stopPlayback() { player?.stop() }

    private fun brandHeader() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val logo = TextView(this@MainActivity).apply {
            text = "▶"; textSize = 25f; setTextColor(white); gravity = Gravity.CENTER
            setBackgroundColor(blue); typeface = Typeface.DEFAULT_BOLD
        }
        addView(logo, LinearLayout.LayoutParams(dp(54), dp(54)).apply { marginEnd = dp(12) })
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "VISION PLAY TV"; textSize = 25f; setTextColor(blue)
                typeface = Typeface.DEFAULT_BOLD; letterSpacing = .02f
            }, matchWrap())
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text; textSize = 12f; setTextColor(cyan); typeface = Typeface.DEFAULT_BOLD
        setPadding(0, 0, 0, dp(10))
    }

    private fun input(hintText: String, type: Int) = EditText(this).apply {
        hint = hintText; setHintTextColor(muted); setTextColor(white); textSize = 14f
        inputType = type; setSingleLine(true); setPadding(dp(14), dp(12), dp(14), dp(12))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(panel); cornerRadius = dp(12).toFloat(); setStroke(dp(1), Color.rgb(36, 52, 75))
        }
    }

    private fun fieldParams() = LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(10) }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 13f; setTextColor(white)
        backgroundTintList = android.content.res.ColorStateList.valueOf(blue)
        setOnClickListener { action() }
    }

    private fun makeSmallButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 11f; setTextColor(white)
        backgroundTintList = android.content.res.ColorStateList.valueOf(panel2)
        setOnClickListener { action() }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        when {
            fullScreenMode -> { fullScreenMode = false; player?.pause(); playbackHandler.removeCallbacks(playbackTicker); showCatalog() }
            detailScreenVisible -> { detailScreenVisible = false; showCatalog() }
            activeSection == "Episódios" && selectedSeason != null -> { selectedSeason = null; showSection("Episódios") }
            activeSection == "Episódios" -> { activeSection = "Séries"; selectedFolder = null; selectedSeason = null; showCatalog() }
            activeSection != "INICIO" && entries.isNotEmpty() -> { selectedFolder = null; searchQuery = ""; showHome() }
            else -> super.onBackPressed()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onStop() {
        currentPlaying?.let { item -> player?.let { savePlaybackProgress(item, it.currentPosition, it.duration) } }
        super.onStop()
        player?.pause()
    }
    override fun onDestroy() {
        currentPlaying?.let { item -> player?.let { savePlaybackProgress(item, it.currentPosition, it.duration) } }
        playbackHandler.removeCallbacks(playbackTicker)
        playerViewSafeDetach()
        io.shutdownNow()
        imageIo.shutdownNow()
        player?.release()
        player = null
        super.onDestroy()
    }
    private fun playerViewSafeDetach() { if (::playerView.isInitialized) playerView.player = null }

    companion object {
        private const val MAX_ITEMS_PER_SECTION = Int.MAX_VALUE
    }
}
