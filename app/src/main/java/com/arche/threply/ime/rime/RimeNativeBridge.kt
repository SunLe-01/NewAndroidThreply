package com.arche.threply.ime.rime

import android.os.Build
import android.util.Log
import java.io.File

/**
 * JNI bridge for native Rime engine.
 *
 * Loads librime_jni.so with diagnostics and graceful fallback.
 * When native library is unavailable, all query methods return empty results
 * so the caller can fall back to RimeFallbackLexicon.
 */
internal class RimeNativeBridge {
    companion object {
        private const val TAG = "RimeNativeBridge"
        private const val LIB_NAME = "rime_jni"

        @Volatile
        private var loadAttempted = false

        @Volatile
        private var loadSucceeded = false

        @Synchronized
        fun ensureLoaded() {
            if (loadAttempted) return
            loadAttempted = true
            loadSucceeded = try {
                System.loadLibrary(LIB_NAME)
                Log.i(TAG, "Successfully loaded lib${LIB_NAME}.so " +
                        "(ABI=${Build.SUPPORTED_ABIS.firstOrNull()}, " +
                        "SDK=${Build.VERSION.SDK_INT})")
                true
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Failed to load lib${LIB_NAME}.so: ${e.message}")
                logNativeLibDiagnostics()
                false
            } catch (e: Exception) {
                Log.w(TAG, "Unexpected error loading lib${LIB_NAME}.so", e)
                false
            }
        }

        private fun logNativeLibDiagnostics() {
            try {
                val appInfo = "/proc/self/maps"
                val abi = Build.SUPPORTED_ABIS.joinToString()
                Log.w(TAG, "Device ABIs: $abi, SDK: ${Build.VERSION.SDK_INT}")
                // Check if the .so exists in the app's native lib directory
                val nativeDir = System.getProperty("java.library.path") ?: ""
                Log.w(TAG, "Library path: $nativeDir")
                nativeDir.split(":").forEach { dir ->
                    val soFile = File(dir, "lib${LIB_NAME}.so")
                    if (soFile.exists()) {
                        Log.w(TAG, "Found ${soFile.absolutePath} (${soFile.length()} bytes)")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to log diagnostics", e)
            }
        }
    }

    private var nativeReady = false
    private var currentSchema = "luna_pinyin"
    private var sharedDataDir = ""
    private var userDataDir = ""

    init {
        ensureLoaded()
    }

    fun isNativeAvailable(): Boolean = loadSucceeded

    fun initialize(schema: String, sharedDataDir: String, userDataDir: String): Boolean {
        if (!loadSucceeded) {
            Log.w(TAG, "Cannot initialize: native library not loaded")
            return false
        }
        if (sharedDataDir.isBlank() || userDataDir.isBlank()) {
            Log.w(TAG, "Cannot initialize: invalid directories " +
                    "(shared=${sharedDataDir.isNotBlank()}, user=${userDataDir.isNotBlank()})")
            return false
        }
        // Validate directories exist
        if (!File(sharedDataDir).isDirectory) {
            Log.w(TAG, "sharedDataDir does not exist: $sharedDataDir")
            return false
        }
        if (!File(userDataDir).isDirectory) {
            Log.w(TAG, "userDataDir does not exist: $userDataDir")
            return false
        }

        currentSchema = schema
        this.sharedDataDir = sharedDataDir
        this.userDataDir = userDataDir

        return try {
            val result = nativeInitialize(schema, sharedDataDir, userDataDir)
            nativeReady = result
            Log.i(TAG, "Native initialize: schema=$schema, result=$result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize native Rime", e)
            nativeReady = false
            false
        }
    }

    fun onStartInput() {
        if (!nativeReady) return
        try {
            nativeOnStartInput()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartInput", e)
        }
    }

    fun onFinishInput() {
        if (!nativeReady) return
        try {
            nativeOnFinishInput()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onFinishInput", e)
        }
    }

    fun release() {
        if (!nativeReady) return
        try {
            nativeRelease()
        } catch (e: Exception) {
            Log.e(TAG, "Error in release", e)
        } finally {
            nativeReady = false
        }
    }

    fun queryCandidates(input: String, limit: Int, page: Int = 0): List<String> {
        if (!nativeReady) return emptyList()
        val safeLimit = limit.coerceIn(1, 20)
        val safePage = page.coerceAtLeast(0)
        return try {
            nativeQueryCandidates(currentSchema, input, safeLimit, safePage)
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying candidates for '$input'", e)
            emptyList()
        }
    }

    private external fun nativeInitialize(schema: String, sharedDataDir: String, userDataDir: String): Boolean
    private external fun nativeOnStartInput()
    private external fun nativeOnFinishInput()
    private external fun nativeRelease()
    private external fun nativeQueryCandidates(schema: String, input: String, limit: Int, page: Int): Array<String>
}
