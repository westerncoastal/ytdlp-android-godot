package org.godotengine.plugin.android.template

import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg // Fixed import path
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot
import java.io.File

class GodotAndroidPlugin(godot: Godot): GodotPlugin(godot) {

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
        return try {
            // Use activity!! to provide the Context needed
            YoutubeDL.getInstance().init(activity!!)
            FFmpeg.getInstance().init(activity!!)
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
                // Fixed: Added the 3rd parameter 'line' to the callback
                YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                    emitSignal("download_progress", progress.toFloat())
                }
                
                val finalPath = "${saveDir.absolutePath}/$fileName.mp3"
                emitSignal("download_completed", finalPath)
                
            } catch (e: Exception) {
                Log.e(pluginName, "Download Error: ${e.message}")
                emitSignal("download_error", e.message ?: "Unknown error")
            }
        }.start()
    }
}
