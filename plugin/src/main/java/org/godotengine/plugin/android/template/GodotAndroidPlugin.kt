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
    private var isYtDlpUpdated = false

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun getPluginName() = BuildConfig.GODOT_PLUGIN_NAME

    override fun getPluginSignals(): MutableSet<SignalInfo> {
        return mutableSetOf(
            SignalInfo("download_completed", String::class.java),
            SignalInfo("download_error", String::class.java),
            SignalInfo("download_progress", Float::class.java)
        )
    }

    private fun ensureYtDlpUpdated() {
        if (!isYtDlpUpdated) {
            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._NIGHTLY)
            isYtDlpUpdated = true
        }
    }

    @UsedByGodot
    fun initLibrary(): Boolean {
        val act = activity
        if (act == null) {
            Log.e(pluginName, "Activity is null, cannot initialize libraries")
            return false
        }

        return try {
            YoutubeDL.getInstance().init(act)
            FFmpeg.getInstance().init(act)
            Log.v(pluginName, "yt-dlp and FFmpeg initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(pluginName, "Failed to init: ${e.message}")
            false
        }
    }

    @UsedByGodot
    fun startDownload(url: String, fileName: String, destinationDir: String) {

        val request = YoutubeDLRequest(url)
        val saveDir = File(destinationDir)
        if (!saveDir.exists()) saveDir.mkdirs()

        request.addOption("-o", "${saveDir.absolutePath}/$fileName.%(ext)s")
        request.addOption("--extract-audio")
        request.addOption("--audio-format", "mp3")
        
        // 🔥 critical fixes
        request.addOption("--extractor-args", "youtube:player_client=android")
        request.addOption("--add-header", "User-Agent: Mozilla/5.0")
        request.addOption("-f", "bestaudio/best")
        request.addOption("--force-ipv4")

        Thread {
            try {
                if (!alreadyUpdated) {
                    YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
                    alreadyUpdated = true
                }
                YoutubeDL.getInstance().execute(request) { progress, _, _ ->

                    Log.d("YTDLP", "RAW progress = [$progress]")

                    val progressFloat = Regex("""(\d+(\.\d+)?)%""")
                        .find(progress?.toString() ?: "")
                        ?.groupValues?.get(1)
                        ?.toFloatOrNull()

                    if (progressFloat != null && progressFloat.isFinite()) {

                        Log.d("YTDLP", "Parsed progressFloat = $progressFloat")

                        mainHandler.post {
                            emitSignal(
                                "download_progress",
                                java.lang.Float.valueOf(progressFloat)
                            )
                        }

                    } else {
                        Log.w(pluginName, "Skipped invalid progress: $progress")
                    }
                }

                val finalPath = "${saveDir.absolutePath}/$fileName.mp3"

                mainHandler.post {
                    emitSignal("download_completed", finalPath)
                }

            } catch (e: Exception) {
                Log.e(pluginName, "Download Error: ${e.message}")

                mainHandler.post {
                    emitSignal("download_error", e.message ?: "Unknown error")
                }
            }
        }.start()
    }

    @UsedByGodot
    fun getStreamInfo(url: String) {
    
        Thread {
            try {
                // ✅ Ensure updated
                YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._NIGHTLY)
    
                val request = YoutubeDLRequest(url)
    
                // 🔥 Required for YouTube
                request.addOption("--extractor-args", "youtube:player_client=android")
                request.addOption("--add-header", "User-Agent: Mozilla/5.0")
                request.addOption("--force-ipv4")
                
                val info = YoutubeDL.getInstance().getInfo(request)
    
                val title = info.title ?: "unknown"
                val duration = info.duration ?: 0
    
                mainHandler.post {
                    emitSignal("stream_info", title, duration.toInt())
                }
    
            } catch (e: Exception) {
                Log.e(pluginName, "StreamInfo Error: ${e.message}")
    
                mainHandler.post {
                    emitSignal("stream_info_error", e.message ?: "Unknown error")
                }
            }
        }.start()
    }

    @UsedByGodot
    fun getDirectUrl(url: String) {
    
        Thread {
            try {
                YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
    
                val request = YoutubeDLRequest(url)
    
                // 🔥 Critical options
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
