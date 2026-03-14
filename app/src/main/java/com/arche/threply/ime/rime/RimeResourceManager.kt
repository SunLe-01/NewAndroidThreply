package com.arche.threply.ime.rime

import android.content.Context
import android.util.Log
import com.arche.threply.data.PrefsManager
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
    private val cachedDirs = AtomicReference<Directories?>(null)

    fun prepare(context: Context): Directories? {
        return runCatching {
            val appContext = context.applicationContext
            val root = File(appContext.filesDir, "rime")
            val sharedDir = File(root, "shared")
            val userDir = File(root, "user")

            ensureDirectories(sharedDir, userDir)

            val needsDeploy = needsDeploy(appContext, sharedDir)
            if (needsDeploy) {
                Log.i(TAG, "Deploying Rime resources (version $RESOURCE_VERSION)...")
                sharedDir.deleteRecursively()
                sharedDir.mkdirs()

                val hasAssets = hasRimeAssets(appContext)
                if (!hasAssets) {
                    Log.w(TAG, "No Rime assets found at '$ASSET_RIME_ROOT'. " +
                            "Ensure Rime resources are bundled in the APK assets.")
                    return@runCatching null
                }

                try {
                    val count = copyAssetDirectory(appContext, ASSET_RIME_ROOT, sharedDir)
                    Log.i(TAG, "Copied $count Rime asset files to ${sharedDir.absolutePath}")
                    PrefsManager.setImeRimeResourceVersion(appContext, RESOURCE_VERSION)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy Rime assets", e)
                    sharedDir.deleteRecursively()
                    sharedDir.mkdirs()
                    return@runCatching null
                }
            }

            val fileCount = sharedDir.listFiles()?.size ?: 0
            if (fileCount == 0) {
                Log.w(TAG, "sharedDir is empty after deploy, Rime will not work natively")
                return@runCatching null
            }

            val dirs = Directories(
                sharedDataDir = sharedDir.absolutePath,
                userDataDir = userDir.absolutePath,
                deployed = needsDeploy
            )
            cachedDirs.set(dirs)
            Log.i(TAG, "Rime directories ready: shared=$fileCount files, " +
                    "user=${userDir.absolutePath}")
            dirs
        }.getOrElse { throwable ->
            Log.e(TAG, "Failed to prepare Rime directories", throwable)
            null
        }
    }

    fun getPreparedDirectories(context: Context): Directories? {
        // Return cached result if available
        cachedDirs.get()?.let { return it }

        return runCatching {
            val appContext = context.applicationContext
            val root = File(appContext.filesDir, "rime")
            val sharedDir = File(root, "shared")
            val userDir = File(root, "user")

            ensureDirectories(sharedDir, userDir)

            if (needsDeploy(appContext, sharedDir)) {
                return@runCatching null
            }

            val dirs = Directories(
                sharedDataDir = sharedDir.absolutePath,
                userDataDir = userDir.absolutePath,
                deployed = false
            )
            cachedDirs.set(dirs)
            dirs
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

            Log.i(TAG, "Starting async Rime resource preparation")
            prepareExecutor.execute {
                try {
                    val result = prepare(appContext)
                    if (result != null) {
                        Log.i(TAG, "Async Rime resource preparation completed")
                    } else {
                        Log.w(TAG, "Async Rime resource preparation returned null")
                    }
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
        if (!sharedDir.exists()) sharedDir.mkdirs()
        if (!userDir.exists()) userDir.mkdirs()
    }

    private fun needsDeploy(context: Context, sharedDir: File): Boolean {
        val previousVersion = PrefsManager.getImeRimeResourceVersion(context)
        val empty = sharedDir.listFiles().isNullOrEmpty()
        if (empty) {
            Log.d(TAG, "needsDeploy: sharedDir is empty")
        }
        return previousVersion < RESOURCE_VERSION || empty
    }

    private fun hasRimeAssets(context: Context): Boolean {
        return try {
            val listed = context.assets.list(ASSET_RIME_ROOT)
            !listed.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Recursively copies an asset directory to a target directory.
     * Returns the number of files copied.
     */
    private fun copyAssetDirectory(context: Context, assetPath: String, targetDir: File): Int {
        val listed = try {
            context.assets.list(assetPath)
        } catch (e: Exception) {
            Log.w(TAG, "Asset path not found: $assetPath, skipping", e)
            return 0
        }

        if (listed == null || listed.isEmpty()) {
            // Leaf node — try to copy as a file
            return try {
                copyAssetFile(context, assetPath, targetDir)
                1
            } catch (e: Exception) {
                Log.w(TAG, "Failed to copy asset file: $assetPath", e)
                0
            }
        }

        var count = 0
        listed.forEach { child ->
            try {
                val childAssetPath = "$assetPath/$child"
                val nestedChildren = context.assets.list(childAssetPath).orEmpty()
                if (nestedChildren.isEmpty()) {
                    copyAssetFile(context, childAssetPath, targetDir)
                    count++
                } else {
                    val nestedTargetDir = File(targetDir, child)
                    if (!nestedTargetDir.exists()) nestedTargetDir.mkdirs()
                    count += copyAssetDirectory(context, childAssetPath, nestedTargetDir)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to process asset child: $child", e)
            }
        }
        return count
    }

    private fun copyAssetFile(context: Context, assetPath: String, targetDir: File) {
        val output = File(targetDir, assetPath.substringAfterLast('/'))
        context.assets.open(assetPath).use { input ->
            output.outputStream().use { out ->
                input.copyTo(out, bufferSize = 8192)
            }
        }
    }
}
