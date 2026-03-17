package com.arche.threply.ime.rime

import android.os.Build
import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

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

    private val nativeInitExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "RimeNativeInit").apply { isDaemon = true }
    }
    private val initGeneration = AtomicInteger(0)

    @Volatile
    private var nativeInitializing = false

    @Volatile
    private var nativeReady = false
    private var currentSchema = "luna_pinyin"
    private var sharedDataDir = ""
    private var userDataDir = ""

    init {
        ensureLoaded()
    }

    fun isNativeAvailable(): Boolean = loadSucceeded

    /** Diagnostic status for developer UI / logging. */
    data class DiagnosticStatus(
        val libraryLoaded: Boolean,
        val nativeInitializing: Boolean,
        val nativeReady: Boolean,
        val currentSchema: String,
        val sharedDataDir: String,
        val userDataDir: String
    )

    fun getDiagnosticStatus(): DiagnosticStatus = DiagnosticStatus(
        libraryLoaded = loadSucceeded,
        nativeInitializing = nativeInitializing,
        nativeReady = nativeReady,
        currentSchema = currentSchema,
        sharedDataDir = sharedDataDir,
        userDataDir = userDataDir
    )

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

        val sameDirs = this.sharedDataDir == sharedDataDir && this.userDataDir == userDataDir
        val sameSchema = currentSchema == schema
        if (sameDirs && nativeReady) {
            currentSchema = schema
            if (!sameSchema) {
                Log.i(TAG, "Native already ready, reusing runtime for schema=$schema")
            }
            return true
        }
        if (sameDirs && sameSchema && nativeInitializing) {
            Log.i(TAG, "Native initialize already in flight for schema=$schema")
            return true
        }

        currentSchema = schema
        this.sharedDataDir = sharedDataDir
        this.userDataDir = userDataDir
        val generation = initGeneration.incrementAndGet()
        nativeInitializing = true
        nativeReady = false
        Log.i(TAG, "Scheduling native initialize: schema=$schema, generation=$generation")
        nativeInitExecutor.execute {
            val result = try {
                nativeInitialize(schema, sharedDataDir, userDataDir)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize native Rime", e)
                false
            }

            if (initGeneration.get() != generation) {
                Log.i(TAG, "Discarding stale native initialize result: generation=$generation, result=$result")
                return@execute
            }

            nativeReady = result
            nativeInitializing = false
            Log.i(TAG, "Native initialize finished: schema=$schema, generation=$generation, result=$result")
        }
        return true
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
        initGeneration.incrementAndGet()
        nativeInitializing = false
        if (!nativeReady) return
        try {
            nativeRelease()
        } catch (e: Exception) {
            Log.e(TAG, "Error in release", e)
        } finally {
            nativeReady = false
        }
    }

    fun isNativeReady(): Boolean = nativeReady

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
