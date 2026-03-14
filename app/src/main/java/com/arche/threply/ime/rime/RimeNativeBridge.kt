package com.arche.threply.ime.rime

import android.util.Log

/**
 * First-step native bridge for Rime.
 *
 * Current project does not bundle native Rime yet, so this bridge is designed to:
 * 1) detect native availability safely
 * 2) expose lifecycle/query APIs with graceful fallback
 *
 * Once native libs are added, only this class needs to be swapped to real JNI calls.
 */
internal class RimeNativeBridge {
    companion object {
        private const val TAG = "RimeNativeBridge"
    }
    
    private var nativeLoaded = false
    private var currentSchema = "luna_pinyin"
    private var sharedDataDir = ""
    private var userDataDir = ""

    init {
        nativeLoaded = try {
            System.loadLibrary("rime_jni")
            Log.i(TAG, "Successfully loaded librime_jni.so")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Failed to load librime_jni.so: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error loading librime_jni.so", e)
            false
        }
    }

    fun isNativeAvailable(): Boolean = nativeLoaded

    fun initialize(schema: String, sharedDataDir: String, userDataDir: String): Boolean {
        if (!nativeLoaded) {
            Log.w(TAG, "Cannot initialize: native library not loaded")
            return false
        }
        if (sharedDataDir.isBlank() || userDataDir.isBlank()) {
            Log.w(TAG, "Cannot initialize: invalid directories")
            return false
        }

        currentSchema = schema
        this.sharedDataDir = sharedDataDir
        this.userDataDir = userDataDir

        return try {
            val result = nativeInitialize(
                schema = schema,
                sharedDataDir = sharedDataDir,
                userDataDir = userDataDir
            )
            Log.i(TAG, "Native initialize result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize native Rime", e)
            false
        }
    }

    fun onStartInput() {
        if (!nativeLoaded) return
        try {
            nativeOnStartInput()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartInput", e)
        }
    }

    fun onFinishInput() {
        if (!nativeLoaded) return
        try {
            nativeOnFinishInput()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onFinishInput", e)
        }
    }

    fun release() {
        if (!nativeLoaded) return
        try {
            nativeRelease()
        } catch (e: Exception) {
            Log.e(TAG, "Error in release", e)
        }
    }

    fun queryCandidates(input: String, limit: Int, page: Int = 0): List<String> {
        if (!nativeLoaded) {
            return emptyList()
        }
        if (sharedDataDir.isBlank() || userDataDir.isBlank()) {
            return emptyList()
        }
        val safeLimit = limit.coerceIn(1, 20)
        val safePage = page.coerceAtLeast(0)
        return try {
            nativeQueryCandidates(currentSchema, input, safeLimit, safePage)
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying candidates", e)
            emptyList()
        }
    }

    private external fun nativeInitialize(schema: String, sharedDataDir: String, userDataDir: String): Boolean
    private external fun nativeOnStartInput()
    private external fun nativeOnFinishInput()
    private external fun nativeRelease()
    private external fun nativeQueryCandidates(schema: String, input: String, limit: Int, page: Int): Array<String>
}
