package com.visionplay.app

import android.graphics.Color
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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
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
import android.text.Editable
import android.text.TextWatcher

private data class MediaEntry(
    val name: String,
    val url: String,
    val group: String,
    val section: String,
    val itemId: String = "",
    val extension: String = "",
    val seriesId: String = ""
)

class MainActivity : AppCompatActivity() {
    private val bg = Color.rgb(5, 9, 17)
    private val panel = Color.rgb(15, 23, 38)
    private val panel2 = Color.rgb(20, 31, 49)
    private val blue = Color.rgb(28, 137, 255)
    private val cyan = Color.rgb(64, 190, 255)
    private val white = Color.WHITE
    private val muted = Color.rgb(154, 169, 190)
    private val io = Executors.newSingleThreadExecutor()

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
    private var activeSection = "Canais ao vivo"
    private var selectedFolder: String? = null
    private var visibleLimit = 100
    private var fullScreenMode = false
    @Volatile private var isLoading = false

    // Credentials are kept only in memory; this prototype does not save them to disk.
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
            setPadding(0, dp(6), 0, dp(24))
        }, matchWrap())
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
            text = "Por segurança, os dados de acesso não são salvos nesta versão. Prefira HTTPS quando seu servidor oferecer suporte."
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
                    activeSection = if (entries.any { it.section == "Canais ao vivo" }) "Canais ao vivo"
                        else if (entries.any { it.section == "Filmes" }) "Filmes" else "Séries"
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
                    var extension = when (type) { "live" -> "ts"; "movie" -> "mp4"; else -> "mp4" }
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "stream_id", "series_id" -> id = reader.nextStringSafe()
                            "name", "title" -> name = reader.nextStringSafe()
                            "category_id" -> categoryId = reader.nextStringSafe()
                            "container_extension" -> extension = reader.nextStringSafe().ifBlank { extension }
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
                        result.add(MediaEntry(name, url, category, section, id, extension, if (type == "series") id else ""))
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
        if (::playerView.isInitialized) playerView.player = null
        root = baseScreen()
        if (fullScreenMode) {
            root.setPadding(0, 0, 0, 0)
            val frame = FrameLayout(this)
            playerView = PlayerView(this).apply {
                useController = true
                setBackgroundColor(Color.BLACK)
                player = this@MainActivity.player
            }
            frame.addView(playerView, FrameLayout.LayoutParams(-1, -1))
            val back = makeSmallButton("✕  VOLTAR") {
                fullScreenMode = false
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                showHome()
            }
            frame.addView(back, FrameLayout.LayoutParams(-2, dp(44), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(14); marginEnd = dp(14)
            })
            root.addView(frame, LinearLayout.LayoutParams(-1, -1))
            setContentView(root)
            return
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "VISION PLAY"; textSize = 23f; setTextColor(blue)
            typeface = Typeface.DEFAULT_BOLD; letterSpacing = .04f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(makeSmallButton("⛶ TELA CHEIA") {
            fullScreenMode = true
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            showHome()
        })
        header.addView(makeSmallButton("CONTA") { stopPlayback(); selectedFolder = null; showLogin() })
        root.addView(header, matchWrap())
        root.addView(TextView(this).apply {
            text = "FILMES, SÉRIES E TV AO VIVO"; setTextColor(muted); textSize = 11f
            letterSpacing = .09f; setPadding(0, dp(4), 0, dp(12))
        }, matchWrap())

        playerView = PlayerView(this).apply {
            useController = true
            setBackgroundColor(Color.BLACK)
            player = this@MainActivity.player
        }
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        root.addView(playerView, LinearLayout.LayoutParams(-1, if (landscape) dp(150) else dp(205)).apply { bottomMargin = dp(10) })

        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Canais ao vivo", "Filmes", "Séries").forEach { label ->
            tabs.addView(makeSmallButton(label) {
                activeSection = label; selectedFolder = null; visibleLimit = 100; showSection(label)
            }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(5) })
        }
        root.addView(tabs, matchWrap())
        searchInput = input("Buscar canais, filmes ou séries...", InputType.TYPE_CLASS_TEXT).apply {
            setText(searchQuery)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s?.toString().orEmpty()
                    selectedFolder = null
                    visibleLimit = 100
                    showSection(activeSection)
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        root.addView(searchInput, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8); bottomMargin = dp(6) })
        contentBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        scroll.addView(contentBox)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        showSection(activeSection)
    }

    private var searchQuery: String = ""

    private fun searchInputTextReset() { searchQuery = "" }

    private fun showSection(section: String) {
        activeSection = section
        if (!::contentBox.isInitialized || fullScreenMode) return
        contentBox.removeAllViews()
        val all = entries.filter { it.section == section }
        val query = searchQuery.trim()
        val filtered = if (query.isBlank()) all else all.filter {
            it.name.contains(query, true) || it.group.contains(query, true) || folderFor(it).contains(query, true)
        }
        contentBox.addView(TextView(this).apply {
            text = when (section) {
                "Canais ao vivo" -> "TV AO VIVO  •  ${all.size} canais"
                "Filmes" -> "FILMES  •  ${all.size} títulos"
                "Séries" -> "SÉRIES  •  ${all.size} títulos"
                else -> "$section  •  ${all.size} itens"
            }
            setTextColor(white); textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(2), dp(12), 0, dp(10))
        }, matchWrap())

        if (filtered.isEmpty()) {
            contentBox.addView(TextView(this).apply {
                text = if (all.isEmpty()) "O servidor não disponibilizou itens nesta seção. Confira se seu plano inclui este catálogo e se a API Xtream está habilitada." else "Nenhum resultado encontrado. Tente outro termo."
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
                val folder = folderEntry.key
                val items = folderEntry.value
                contentBox.addView(makeFolderRow(folder, items.size) {
                    selectedFolder = folder
                    visibleLimit = 100
                    showSection(section)
                }, LinearLayout.LayoutParams(-1, dp(66)).apply { bottomMargin = dp(7) })
            }
            return
        }

        if (section == "Episódios") {
            contentBox.addView(makeSmallButton("‹  VOLTAR PARA SÉRIES") {
                activeSection = "Séries"; selectedFolder = null; showSection("Séries")
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(8) })
        }
        if (query.isBlank() && selectedFolder != null) {
            contentBox.addView(makeSmallButton("‹  TODAS AS PASTAS") { selectedFolder = null; showSection(section) },
                LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin = dp(8) })
        }
        val visible = if (query.isBlank() && selectedFolder != null) filtered.filter { folderFor(it) == selectedFolder } else filtered
        val page = visible.take(visibleLimit)
        page.forEach { item ->
            contentBox.addView(makeMediaRow(item), LinearLayout.LayoutParams(-1, dp(62)).apply { bottomMargin = dp(7) })
        }
        if (visible.size > page.size) {
            contentBox.addView(makeFolderRow("Carregar mais conteúdos", visible.size - page.size) {
                visibleLimit += 100
                showSection(section)
            }, LinearLayout.LayoutParams(-1, dp(60)).apply { topMargin = dp(4); bottomMargin = dp(10) })
        }
        if (page.isEmpty() && visible.isEmpty()) {
            contentBox.addView(TextView(this).apply {
                text = "Nenhum conteúdo nesta pasta."
                setTextColor(muted); textSize = 14f; setPadding(dp(14), dp(18), dp(14), dp(18)); setBackgroundColor(panel)
            }, matchWrap())
        }
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

    private fun makeFolderRow(name: String, count: Int, action: () -> Unit): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(8), dp(12), dp(8)); setBackgroundColor(panel)
        val symbol = TextView(this@MainActivity).apply {
            text = when {
                name.contains("Globo", true) || name.contains("SBT", true) || name.contains("Record", true) || name.contains("Band", true) -> "▣"
                name.contains("Netflix", true) || name.contains("HBO", true) || name.contains("Prime", true) || name.contains("GloboPlay", true) -> "▶"
                else -> "▰"
            }
            textSize = 22f; setTextColor(cyan); gravity = Gravity.CENTER
            setBackgroundColor(panel2)
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
        addView(TextView(this@MainActivity).apply {
            text = when (item.section) { "Canais ao vivo" -> "●"; "Filmes" -> "▶"; else -> "▤" }
            setTextColor(cyan); textSize = 16f; gravity = Gravity.CENTER
            setBackgroundColor(panel2)
        }, LinearLayout.LayoutParams(dp(38), dp(42)).apply { marginEnd = dp(10) })
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = item.name; setTextColor(white); textSize = 13f; maxLines = 2; typeface = Typeface.DEFAULT_BOLD
            }, matchWrap())
            addView(TextView(this@MainActivity).apply {
                text = if (item.section == "Séries") "Série • toque para ver episódios" else item.group
                setTextColor(muted); textSize = 10f; maxLines = 1; setPadding(0, dp(2), 0, 0)
            }, matchWrap())
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(makeSmallButton(if (item.section == "Séries") "EPISÓDIOS" else "ASSISTIR") {
            if (item.section == "Séries") loadSeriesEpisodes(item) else play(item)
        })
        setOnClickListener { if (item.section == "Séries") loadSeriesEpisodes(item) else play(item) }
    }

    private fun loadSeriesEpisodes(series: MediaEntry) {
        if (isLoading) return
        isLoading = true
        showLoading("Carregando episódios de ${series.name}...")
        io.execute {
            try {
                val obj = fetchJsonObject(apiUrl("get_series_info", "series_id", series.seriesId))
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
                        if (id.isNotBlank()) episodes.add(MediaEntry(title, streamUrl("series", id, ext), "Temporada $season", "Episódios", id, ext))
                    }
                }
                runOnUiThread {
                    isLoading = false
                    if (episodes.isEmpty()) {
                        showHome()
                        toast("Nenhum episódio foi retornado para esta série.")
                    } else {
                        entries.removeAll { it.section == "Episódios" }
                        entries.addAll(episodes)
                        activeSection = "Episódios"
                        showHome()
                        showSection("Episódios")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { isLoading = false; showHome(); toast(safeError(e)) }
            }
        }
    }

    private fun JsonObject.stringValue(key: String): String {
        val value = get(key) ?: return ""
        return if (value.isJsonNull) "" else runCatching { value.asString }.getOrDefault("")
    }

    private fun play(item: MediaEntry) {
        if (item.url.isBlank()) {
            toast("Este item não possui um endereço de reprodução disponível.")
            return
        }
        try {
            val currentPlayer = player ?: run {
                initializePlayer()
                player ?: throw IllegalStateException("Player indisponível")
            }
            currentPlayer.stop()
            currentPlayer.clearMediaItems()
            currentPlayer.setMediaItem(MediaItem.Builder().setUri(Uri.parse(item.url)).build())
            currentPlayer.prepare()
            currentPlayer.playWhenReady = true
            toast("Abrindo: ${item.name}")
        } catch (e: Exception) {
            toast("Não foi possível abrir este conteúdo: ${e.message.orEmpty().take(100)}")
        }
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
                        if (found.size >= MAX_ITEMS_PER_SECTION) return@forEach
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
                    activeSection = "Canais ao vivo"
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
            addView(TextView(this@MainActivity).apply {
                text = "CANAIS AO VIVO  •  FILMES  •  SÉRIES"; textSize = 10f
                setTextColor(muted); letterSpacing = .06f
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!fullScreenMode && !isFinishing && entries.isNotEmpty()) showHome()
    }

    override fun onStop() { super.onStop(); player?.pause() }
    override fun onDestroy() {
        playerViewSafeDetach()
        io.shutdownNow()
        player?.release()
        player = null
        super.onDestroy()
    }
    private fun playerViewSafeDetach() { if (::playerView.isInitialized) playerView.player = null }

    companion object {
        private const val MAX_ITEMS_PER_SECTION = Int.MAX_VALUE
    }
}
