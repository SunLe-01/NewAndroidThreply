package com.arche.threply.ime.rime

import android.content.Context
import android.util.Log
import com.arche.threply.data.PrefsManager
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal object RimeResourceManager {
    private const val TAG = "RimeResourceManager"
    
    data class Directories(
        val sharedDataDir: String,
        val userDataDir: String,
        val deployed: Boolean
    )

    private const val ASSET_RIME_ROOT = "rime/shared"
    private const val RESOURCE_VERSION = 2
    private val prepareExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "RimeResourcePrep").apply { isDaemon = true }
    }
    private val prepareInFlight = AtomicBoolean(false)

    fun prepare(context: Context): Directories? {
        return runCatching {
            val appContext = context.applicationContext
            val root = File(appContext.filesDir, "rime")
            val sharedDir = File(root, "shared")
            val userDir = File(root, "user")

            ensureDirectories(sharedDir, userDir)

            val needsDeploy = needsDeploy(appContext, sharedDir)
            if (needsDeploy) {
                sharedDir.deleteRecursively()
                sharedDir.mkdirs()
                
                try {
                    copyAssetDirectory(appContext, ASSET_RIME_ROOT, sharedDir)
                    PrefsManager.setImeRimeResourceVersion(appContext, RESOURCE_VERSION)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy Rime assets", e)
                    sharedDir.deleteRecursively()
                    sharedDir.mkdirs()
                    return@runCatching null
                }
            }

            Directories(
                sharedDataDir = sharedDir.absolutePath,
                userDataDir = userDir.absolutePath,
                deployed = needsDeploy
            )
        }.getOrElse { throwable ->
            Log.e(TAG, "Failed to prepare Rime directories", throwable)
            null
        }
    }

    fun getPreparedDirectories(context: Context): Directories? {
        return runCatching {
            val appContext = context.applicationContext
            val root = File(appContext.filesDir, "rime")
            val sharedDir = File(root, "shared")
            val userDir = File(root, "user")

            ensureDirectories(sharedDir, userDir)

            if (needsDeploy(appContext, sharedDir)) {
                return@runCatching null
            }

            Directories(
                sharedDataDir = sharedDir.absolutePath,
                userDataDir = userDir.absolutePath,
                deployed = false
            )
        }.getOrElse { throwable ->
            Log.e(TAG, "Failed to get prepared directories", throwable)
            null
        }
    }

    fun warmUpAsync(context: Context) {
        val appContext = context.applicationContext
        
        runCatching {
            if (getPreparedDirectories(appContext) != null) return
            if (!prepareInFlight.compareAndSet(false, true)) return

            prepareExecutor.execute {
                try {
                    prepare(appContext)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to prepare Rime in background", e)
                } finally {
                    prepareInFlight.set(false)
                }
            }
        }.onFailure {
            Log.e(TAG, "Failed to start Rime warm up", it)
        }
    }

    private fun ensureDirectories(sharedDir: File, userDir: File) {
        if (!sharedDir.exists()) {
            sharedDir.mkdirs()
        }
        if (!userDir.exists()) {
            userDir.mkdirs()
        }
    }

    private fun needsDeploy(context: Context, sharedDir: File): Boolean {
        val previousVersion = PrefsManager.getImeRimeResourceVersion(context)
        return previousVersion < RESOURCE_VERSION || sharedDir.listFiles().isNullOrEmpty()
    }

    private fun copyAssetDirectory(context: Context, assetPath: String, targetDir: File) {
        val listed = try {
            context.assets.list(assetPath)
        } catch (e: Exception) {
            Log.w(TAG, "Asset path not found: $assetPath, skipping", e)
            return
        }
        
        if (listed == null || listed.isEmpty()) {
            // This might be a file, try to copy it
            try {
                copyAssetFile(context, assetPath, targetDir)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to copy asset file: $assetPath", e)
            }
            return
        }

        val children = listed.toList()
        children.forEach { child ->
            try {
                val childAssetPath = "$assetPath/$child"
                val nestedChildren = context.assets.list(childAssetPath).orEmpty()
                if (nestedChildren.isEmpty()) {
                    copyAssetFile(context, childAssetPath, targetDir)
                } else {
                    val nestedTargetDir = File(targetDir, child)
                    if (!nestedTargetDir.exists()) nestedTargetDir.mkdirs()
                    copyAssetDirectory(context, childAssetPath, nestedTargetDir)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to process asset child: $child", e)
            }
        }
    }

    private fun copyAssetFile(context: Context, assetPath: String, targetDir: File) {
        val output = File(targetDir, assetPath.substringAfterLast('/'))
        context.assets.open(assetPath).use { input ->
            output.outputStream().use { out ->
                input.copyTo(out)
            }
        }
    }
}
