package org.godotengine.plugin.android.template

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot
import java.io.File

class GodotAndroidPlugin(godot: Godot) : GodotPlugin(godot) {

    @Volatile
    private var isYtDlpUpdated = false

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun getPluginName() = BuildConfig.GODOT_PLUGIN_NAME

    override fun getPluginSignals(): MutableSet<SignalInfo> {
        return mutableSetOf(
            SignalInfo("download_progress", Float::class.java),
            SignalInfo("download_completed", String::class.java),

            SignalInfo("audio_ready", String::class.java),
            SignalInfo("download_error", String::class.java),

            SignalInfo("stream_info", String::class.java, Int::class.java),
            SignalInfo("stream_info_error", String::class.java),

            SignalInfo("stream_url", String::class.java),
            SignalInfo("stream_url_error", String::class.java)
        )
    }

    @Synchronized
    private fun ensureYtDlpUpdated() {
        if (!isYtDlpUpdated) {
            YoutubeDL.getInstance()
                .updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
            isYtDlpUpdated = true
        }
    }

    @UsedByGodot
    fun initLibrary(): Boolean {
        val act = activity ?: return false
        return try {
            YoutubeDL.getInstance().init(act)
            FFmpeg.getInstance().init(act)
            true
        } catch (e: Exception) {
            Log.e(pluginName, "Init failed: ${e.message}")
            false
        }
    }

    // =========================================================
    // 🎵 DOWNLOAD VIDEO + EXTRACT WAV (FIXED SAFE VERSION)
    // =========================================================
    @UsedByGodot
    fun startDownload(url: String, fileName: String, destinationDir: String) {

        val saveDir = File(destinationDir)
        if (!saveDir.exists()) saveDir.mkdirs()

        val videoPath = "${saveDir.absolutePath}/$fileName.mp4"
        val wavPath = "${saveDir.absolutePath}/$fileName.wav"

        Thread {
            try {
                ensureYtDlpUpdated()

                // ======================
                // 1. DOWNLOAD MP4
                // ======================
                val request = YoutubeDLRequest(url)
                request.addOption("-o", videoPath)
                request.addOption("-f", "bv*[height<=1080]+ba/b")
                request.addOption("--merge-output-format", "mp4")
                request.addOption("--force-ipv4")
                request.addOption("--add-header", "User-Agent: Mozilla/5.0")

                YoutubeDL.getInstance().execute(request) { progress, _, _ ->
                    val match = Regex("""(\d+(\.\d+)?)%""")
                        .find(progress?.toString() ?: "")
                        ?.groupValues?.get(1)
                        ?.toFloatOrNull()

                    if (match != null) {
                        mainHandler.post {
                            emitSignal("download_progress", match)
                        }
                    }
                }

                val videoFile = File(videoPath)
                if (!videoFile.exists()) {
                    throw Exception("MP4 download failed")
                }

                mainHandler.post {
                    emitSignal("download_completed", videoPath)
                }

                // ======================
                // 2. EXTRACT WAV
                // ======================
                val cmd = arrayOf(
                    "-y",
                    "-i", videoFile.absolutePath,
                    "-vn",
                    "-acodec", "pcm_s16le",
                    "-ar", "44100",
                    "-ac", "2",
                    wavPath
                )

                // ======================
                // 2. EXTRACT WAV (FIXED)
                // ======================
                val cmd = "-y -i ${videoFile.absolutePath} -vn -acodec pcm_s16le -ar 44100 -ac 2 $wavPath"
                
                FFmpeg.getInstance().execute(cmd) { result ->
                    Log.d(pluginName, "FFmpeg: $result")
                
                    val wavFile = File(wavPath)
                
                    if (wavFile.exists()) {
                        mainHandler.post {
                            emitSignal("audio_ready", wavPath)
                        }
                    } else {
                        mainHandler.post {
                            emitSignal("download_error", "WAV conversion failed")
                        }
                    }
                }

                val wavFile = File(wavPath)

                if (wavFile.exists()) {
                    mainHandler.post {
                        emitSignal("audio_ready", wavPath)
                    }
                } else {
                    mainHandler.post {
                        emitSignal("download_error", "WAV conversion failed")
                    }
                }

            } catch (e: Exception) {
                Log.e(pluginName, "Download Error: ${e.message}")

                mainHandler.post {
                    emitSignal("download_error", e.message ?: "unknown")
                }
            }
        }.start()
    }

    // =========================================================
    // 📦 STREAM INFO
    // =========================================================
    @UsedByGodot
    fun getStreamInfo(url: String) {
        Thread {
            try {
                ensureYtDlpUpdated()

                val request = YoutubeDLRequest(url)
                val info = YoutubeDL.getInstance().getInfo(request)

                mainHandler.post {
                    emitSignal(
                        "stream_info",
                        info.title ?: "unknown",
                        (info.duration ?: 0).toInt()
                    )
                }

            } catch (e: Exception) {
                mainHandler.post {
                    emitSignal("stream_info_error", e.message ?: "error")
                }
            }
        }.start()
    }

    // =========================================================
    // 🔗 DIRECT STREAM URL
    // =========================================================
    @UsedByGodot
    fun getDirectUrl(url: String) {
        Thread {
            try {
                ensureYtDlpUpdated()

                val request = YoutubeDLRequest(url)
                request.addOption("-f", "best")

                val info = YoutubeDL.getInstance().getInfo(request)

                mainHandler.post {
                    emitSignal("stream_url", info.url ?: "")
                }

            } catch (e: Exception) {
                mainHandler.post {
                    emitSignal("stream_url_error", e.message ?: "error")
                }
            }
        }.start()
    }
}
