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

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun getPluginName() = BuildConfig.GODOT_PLUGIN_NAME

    override fun getPluginSignals(): MutableSet<SignalInfo> {
        return mutableSetOf(
            SignalInfo("download_completed", String::class.java),
            SignalInfo("download_error", String::class.java),
            SignalInfo("download_progress", Float::class.java)
        )
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

        Thread {
            try {

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
}
