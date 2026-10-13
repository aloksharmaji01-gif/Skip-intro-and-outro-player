
package com.skipintrooutro.player

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class MainActivity : AppCompatActivity() {

    private lateinit var vlc: LibVLC
    private lateinit var player: MediaPlayer
    private lateinit var video: VLCVideoLayout
    private lateinit var status: TextView
    private lateinit var controls: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private val episodes = mutableListOf<Uri>()
    private var episodeIndex = 0
    private var introStart = 0L
    private var introEnd = 130000L
    private var outroStart = 1380000L
    private var sleepAt = 0L
    private var folderUri: Uri? = null

    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                folderUri = uri
                Toast.makeText(this, "Folder selected. Use Open video to start.", Toast.LENGTH_LONG).show()
            }
        }

    private val videoPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                if (!episodes.contains(uri)) episodes.add(uri)
                episodeIndex = episodes.lastIndex
                playEpisode()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vlc = LibVLC(this, arrayListOf("--no-video-title-show"))
        player = MediaPlayer(vlc)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
        }

        video = VLCVideoLayout(this)
        root.addView(video, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        status = TextView(this).apply {
            text = "Choose a video to begin"
            textSize = 15f
            gravity = Gravity.CENTER
        }
        root.addView(status)

        controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(controls)

        addRow(
            button("Choose folder") { folderPicker.launch(null) },
            button("Open video") {
                videoPicker.launch(arrayOf("video/*"))
            }
        )
        addRow(
            button("⏪ Intro") { seekTo(introEnd) },
            button("⏩ Outro") { seekTo(outroStart) }
        )
        addRow(
            button("Settings") { showSettings() },
            button("Sleep timer") { showSleepTimer() }
        )
        addRow(
            button("Previous") {
                if (episodeIndex > 0) {
                    episodeIndex--
                    playEpisode()
                }
            },
            button("Next") {
                if (episodeIndex < episodes.lastIndex) {
                    episodeIndex++
                    playEpisode()
                }
            }
        )

        setContentView(root)
        player.attachViews(video, null, false, false)

        player.setEventListener { event ->
            if (event.type == MediaPlayer.Event.EndReached) {
                runOnUiThread {
                    if (episodeIndex < episodes.lastIndex) {
                        episodeIndex++
                        playEpisode()
                    } else {
                        status.text = "Episode finished"
                    }
                }
            }
        }

        handler.post(tick)
    }

    private fun button(label: String, action: () -> Unit) =
        Button(this).apply {
            text = label
            setOnClickListener { action() }
        }

    private fun addRow(a: Button, b: Button) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.addView(a, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(row)
    }

    private fun playEpisode() {
        if (episodes.isEmpty()) return
        val media = Media(vlc, episodes[episodeIndex])
        player.media = media
        media.release()
        player.play()
        status.text = "Episode ${episodeIndex + 1} of ${episodes.size}"
    }

    private fun seekTo(ms: Long) {
        if (player.isPlaying) player.time = ms
        else Toast.makeText(this, "Open a video first", Toast.LENGTH_SHORT).show()
    }

    private fun showSettings() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 8)
        }
        val intro = EditText(this).apply {
            hint = "Intro end in seconds (default 130)"
            setText((introEnd / 1000).toString())
            inputType = 2
        }
        val outro = EditText(this).apply {
            hint = "Outro start in seconds (default 1380)"
            setText((outroStart / 1000).toString())
            inputType = 2
        }
        layout.addView(intro)
        layout.addView(outro)
        AlertDialog.Builder(this)
            .setTitle("Skip settings")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                introEnd = (intro.text.toString().toLongOrNull() ?: 130L) * 1000
                outroStart = (outro.text.toString().toLongOrNull() ?: 1380L) * 1000
                Toast.makeText(this, "Settings saved for this session", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSleepTimer() {
        val input = EditText(this).apply {
            hint = "Minutes until pause"
            inputType = 2
        }
        AlertDialog.Builder(this)
            .setTitle("Sleep timer")
            .setView(input)
            .setPositiveButton("Start") { _, _ ->
                val minutes = input.text.toString().toLongOrNull() ?: 0L
                sleepAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60000 else 0L
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (::player.isInitialized && player.isPlaying) {
                val time = player.time
                if (time >= introStart && time < introEnd) {
                    player.time = introEnd
                }
                if (time >= outroStart && player.length > outroStart) {
                    if (episodeIndex < episodes.lastIndex) {
                        episodeIndex++
                        playEpisode()
                    } else {
                        player.pause()
                    }
                }
                if (sleepAt > 0 && System.currentTimeMillis() >= sleepAt) {
                    player.pause()
                    sleepAt = 0L
                    status.text = "Sleep timer paused playback"
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        player.detachViews()
        player.release()
        vlc.release()
        super.onDestroy()
    }
}
