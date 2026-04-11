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
            SignalInfo("download_completed", String::class.java),
            SignalInfo("download_error", String::class.java),
            SignalInfo("download_progress", Float::class.java),

            // ✅ NEW signals
            SignalInfo("stream_info", String::class.java, Int::class.java),
            SignalInfo("stream_info_error", String::class.java),
            SignalInfo("stream_url", String::class.java),
            SignalInfo("stream_url_error", String::class.java)
        )
    }

    // ✅ ONE-TIME UPDATE (thread-safe)
    @Synchronized
    private fun ensureYtDlpUpdated() {
        if (!isYtDlpUpdated) {
            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
            isYtDlpUpdated = true
            Log.d(pluginName, "yt-dlp updated once")
        }
    }

    @UsedByGodot
    fun initLibrary(): Boolean {
        val act = activity ?: return false

        return try {
            YoutubeDL.getInstance().init(act)
            FFmpeg.getInstance().init(act)

            Log.v(pluginName, "yt-dlp + FFmpeg initialized")

            // ✅ OPTIONAL: warm-up update in background
            Thread {
                try {
                    ensureYtDlpUpdated()
                } catch (e: Exception) {
                    Log.e(pluginName, "Initial update failed: ${e.message}")
                }
            }.start()

            true
        } catch (e: Exception) {
            Log.e(pluginName, "Init failed: ${e.message}")
            false
        }
    }

    // =========================
    // 🎵 DOWNLOAD AUDIO
    // =========================
    @UsedByGodot
    fun startDownload(url: String, fileName: String, destinationDir: String) {
    
        val saveDir = File(destinationDir)
        if (!saveDir.exists()) saveDir.mkdirs()
    
        val videoPath = "${saveDir.absolutePath}/$fileName.mp4"
        val wavPath = "${saveDir.absolutePath}/$fileName.wav"
    
        val request = YoutubeDLRequest(url)
    
        // =========================
        // 🎥 VIDEO DOWNLOAD (MP4)
        // =========================
        request.addOption("-o", videoPath)
        request.addOption("-f", "bv*[vcodec^=avc1][height<=1080]+ba/b[height<=1080]")
        request.addOption("--merge-output-format", "mp4")
    
        // Stability
        request.addOption("--extractor-args", "youtube:player_client=android")
        request.addOption("--add-header", "User-Agent: Mozilla/5.0")
        request.addOption("--force-ipv4")
    
        Thread {
            try {
                ensureYtDlpUpdated()
    
                YoutubeDL.getInstance().execute(request) { progress, _, _ ->
    
                    val progressFloat = Regex("""(\d+(\.\d+)?)%""")
                        .find(progress?.toString() ?: "")
                        ?.groupValues?.get(1)
                        ?.toFloatOrNull()
    
                    if (progressFloat != null && progressFloat.isFinite()) {
                        mainHandler.post {
                            emitSignal("download_progress", progressFloat)
                        }
                    }
                }
    
                val videoFile = File(videoPath)
    
                if (!videoFile.exists()) {
                    throw Exception("Video download failed (MP4 missing)")
                }
    
                // =========================
                // 🎧 WAV CONVERSION (FFmpeg)
                // =========================
                val cmd = arrayOf(
                    "-i", videoFile.absolutePath,
                    "-ar", "44100",
                    "-ac", "2",
                    "-y",
                    wavPath
                )
    
                FFmpeg.getInstance().execute(cmd) { result ->
    
                    val wavFile = File(wavPath)
    
                    if (!wavFile.exists()) {
                        mainHandler.post {
                            emitSignal("download_error", "WAV conversion failed")
                        }
                        return@execute
                    }
    
                    mainHandler.post {
                        // 🎯 SEND BOTH OUTPUTS TO GODOT
                        emitSignal("download_completed", videoFile.absolutePath)
                        emitSignal("audio_ready", wavFile.absolutePath)
                    }
                }
    
            } catch (e: Exception) {
                Log.e(pluginName, "Download Error: ${e.message}")
    
                mainHandler.post {
                    emitSignal("download_error", e.message ?: "Unknown error")
                }
            }
        }.start()
    }

    // =========================
    // 📦 STREAM INFO
    // =========================
    @UsedByGodot
    fun getStreamInfo(url: String) {

        Thread {
            try {
                ensureYtDlpUpdated()

                val request = YoutubeDLRequest(url)
                request.addOption("--extractor-args", "youtube:player_client=android")
                request.addOption("--add-header", "User-Agent: Mozilla/5.0")
                request.addOption("--force-ipv4")

                val info = YoutubeDL.getInstance().getInfo(request)

                val title = info.title ?: "unknown"
                val duration = (info.duration ?: 0).toInt()

                mainHandler.post {
                    emitSignal("stream_info", title, duration)
                }

            } catch (e: Exception) {
                Log.e(pluginName, "StreamInfo Error: ${e.message}")

                mainHandler.post {
                    emitSignal("stream_info_error", e.message ?: "Unknown error")
                }
            }
        }.start()
    }

    // =========================
    // 🔗 DIRECT STREAM URL
    // =========================
    @UsedByGodot
    fun getDirectUrl(url: String) {

        Thread {
            try {
                ensureYtDlpUpdated()

                val request = YoutubeDLRequest(url)
                request.addOption("-f", "best")
                request.addOption("--extractor-args", "youtube:player_client=android")
                request.addOption("--add-header", "User-Agent: Mozilla/5.0")

                val info = YoutubeDL.getInstance().getInfo(request)
                val directUrl = info.url ?: ""

                mainHandler.post {
                    emitSignal("stream_url", directUrl)
                }

            } catch (e: Exception) {
                Log.e(pluginName, "DirectURL Error: ${e.message}")

                mainHandler.post {
                    emitSignal("stream_url_error", e.message ?: "Unknown error")
                }
            }
        }.start()
    }
}
