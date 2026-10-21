package com.visionplay.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class Channel(val name: String, val url: String, val group: String = "Canais")

class MainActivity : AppCompatActivity() {
    private val bg = Color.rgb(7, 11, 18)
    private val panel = Color.rgb(16, 24, 38)
    private val blue = Color.rgb(22, 139, 255)
    private val white = Color.WHITE
    private val muted = Color.rgb(160, 174, 192)

    private lateinit var root: LinearLayout
    private lateinit var urlInput: EditText
    private lateinit var status: TextView
    private lateinit var channelsBox: LinearLayout
    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private val channels = mutableListOf<Channel>()

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) loadFile(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildInterface()
        initializePlayer()
    }

    private fun buildInterface() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16))
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0)
        }

        val logo = TextView(this).apply {
            text = "VISIONPLAY"
            textSize = 27f
            setTextColor(blue)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        }
        content.addView(logo, matchWrap())

        val subtitle = TextView(this).apply {
            text = "Sua central de entretenimento"
            textSize = 14f
            setTextColor(muted)
            setPadding(0, dp(4), 0, dp(16))
        }
        content.addView(subtitle, matchWrap())

        playerView = PlayerView(this).apply {
            useController = true
            setBackgroundColor(Color.BLACK)
        }
        content.addView(playerView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(220)
        ).apply { bottomMargin = dp(16) })

        urlInput = EditText(this).apply {
            hint = "URL da lista M3U/M3U8"
            setHintTextColor(muted)
            setTextColor(white)
            textSize = 14f
            setSingleLine(true)
            setPadding(dp(12))
            setBackgroundColor(panel)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        content.addView(urlInput, matchWrap().apply { bottomMargin = dp(8) })

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(makeButton("Carregar URL") { loadFromUrl(urlInput.text.toString().trim()) },
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
        buttons.addView(makeButton("Abrir arquivo") {
            pickFile.launch(arrayOf("*/*", "text/*", "application/octet-stream"))
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
        content.addView(buttons, matchWrap())

        status = TextView(this).apply {
            text = "Importe uma lista M3U para começar."
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(12), 0, dp(12))
        }
        content.addView(status, matchWrap())

        val section = TextView(this).apply {
            text = "CANAIS E CONTEÚDOS"
            textSize = 13f
            setTextColor(blue)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(8))
        }
        content.addView(section, matchWrap())

        channelsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(channelsBox, matchWrap())

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    status.text = "Não foi possível reproduzir este conteúdo. Verifique o link e o formato."
                }
            })
        }
    }

    private fun loadFromUrl(raw: String) {
        if (raw.isBlank() || !(raw.startsWith("https://", true) || raw.startsWith("http://", true))) {
            toast("Informe uma URL http ou https válida.")
            return
        }
        status.text = "Carregando lista..."
        thread {
            try {
                val connection = (URL(raw).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 20000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "VisionPlay/1.0 Android")
                }
                val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                connection.disconnect()
                val parsed = parseM3u(text)
                runOnUiThread { showChannels(parsed, "Lista carregada pela URL") }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Falha ao carregar. Confira a URL e a conexão."
                    toast("Não foi possível abrir a lista.")
                }
            }
        }
    }

    private fun loadFile(uri: Uri) {
        status.text = "Lendo arquivo..."
        thread {
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw IllegalStateException("Arquivo vazio")
                val parsed = parseM3u(text)
                runOnUiThread { showChannels(parsed, "Lista importada do arquivo") }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Não foi possível ler esse arquivo."
                    toast("Confira se o arquivo contém uma lista M3U.")
                }
            }
        }
    }

    private fun parseM3u(text: String): List<Channel> {
        val result = mutableListOf<Channel>()
        var name = "Canal"
        var group = "Canais"
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.startsWith("#EXTINF", true)) {
                name = line.substringAfter(",", "Canal").trim().ifBlank { "Canal" }
                group = Regex("""group-title=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    .find(line)?.groupValues?.getOrNull(1) ?: "Canais"
            } else if (line.isNotBlank() && !line.startsWith("#") &&
                (line.startsWith("https://", true) || line.startsWith("http://", true))) {
                result.add(Channel(name, line, group))
                name = "Canal"
                group = "Canais"
            }
        }
        return result
    }

    private fun showChannels(items: List<Channel>, message: String) {
        channels.clear()
        channels.addAll(items)
        channelsBox.removeAllViews()
        status.text = "$message • ${items.size} item(ns)"
        if (items.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Nenhum endereço de mídia foi encontrado nesta lista."
                setTextColor(muted)
                textSize = 14f
            }
            channelsBox.addView(empty, matchWrap())
            return
        }
        var lastGroup = ""
        items.forEachIndexed { index, channel ->
            if (channel.group != lastGroup) {
                lastGroup = channel.group
                val groupTitle = TextView(this).apply {
                    text = channel.group.uppercase()
                    setTextColor(blue)
                    textSize = 12f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setPadding(0, dp(12), 0, dp(6))
                }
                channelsBox.addView(groupTitle, matchWrap())
            }
            val button = Button(this).apply {
                text = channel.name
                isAllCaps = false
                textSize = 14f
                setTextColor(white)
                setBackgroundTintList(android.content.res.ColorStateList.valueOf(panel))
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(dp(12))
                setOnClickListener { play(channel) }
            }
            channelsBox.addView(button, matchWrap().apply { bottomMargin = dp(6) })
        }
    }

    private fun play(channel: Channel) {
        try {
            val item = MediaItem.fromUri(channel.url)
            player?.setMediaItem(item)
            player?.prepare()
            player?.playWhenReady = true
            status.text = "Reproduzindo: ${channel.name}"
        } catch (e: Exception) {
            status.text = "Não foi possível iniciar a reprodução."
        }
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(white)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(blue))
        setOnClickListener { action() }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
