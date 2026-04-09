package org.godotengine.plugin.android.template // Ensure this matches your package name!

import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.ffmpeg.FFmpeg
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot
import java.io.File

class GodotAndroidPlugin(godot: Godot): GodotPlugin(godot) {

    override fun getPluginName() = BuildConfig.GODOT_PLUGIN_NAME

    // Register signals so Godot can "listen" to the download status
    override fun getPluginSignals(): MutableSet<SignalInfo> {
        return mutableSetOf(
            SignalInfo("download_completed", String::class.java), // Returns the file path
            SignalInfo("download_error", String::class.java),     // Returns error message
            SignalInfo("download_progress", Float::class.java)    // Returns 0.0 to 100.0
        )
    }

    /**
     * Initializes the yt-dlp and FFmpeg binaries on the Android device.
     * Call this once in Godot before downloading.
     */
    @UsedByGodot
    fun initLibrary(): Boolean {
        return try {
            YoutubeDL.getInstance().init(activity)
            FFmpeg.getInstance().init(activity)
            Log.v(pluginName, "yt-dlp and FFmpeg initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(pluginName, "Failed to init: ${e.message}")
            false
        }
    }

    /**
     * Starts a download on a background thread.
     */
    @UsedByGodot
    fun startDownload(url: String, fileName: String, destinationDir: String) {
        val request = YoutubeDLRequest(url)
        
        // Ensure the directory exists
        val saveDir = File(destinationDir)
        if (!saveDir.exists()) saveDir.mkdirs()

        // Set output path (e.g., /path/to/dir/filename.mp3)
        request.addOption("-o", "${saveDir.absolutePath}/$fileName.%(ext)s")
        request.addOption("--extract-audio")
        request.addOption("--audio-format", "mp3")

        // Run in a background thread so we don't freeze the game
        Thread {
            try {
                YoutubeDL.getInstance().execute(request) { progress, etaInSeconds ->
                    emitSignal(godot, pluginName, "download_progress", progress)
                }
                
                // Assuming it saved as .mp3 due to our options
                val finalPath = "${saveDir.absolutePath}/$fileName.mp3"
                emitSignal(godot, pluginName, "download_completed", finalPath)
                
            } catch (e: Exception) {
                Log.e(pluginName, "Download Error: ${e.message}")
                emitSignal(godot, pluginName, "download_error", e.message ?: "Unknown error")
            }
        }.start()
    }
}
