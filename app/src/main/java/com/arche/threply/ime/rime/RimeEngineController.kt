package com.arche.threply.ime.rime

import android.util.Log

class RimeEngineController {
    companion object {
        private const val TAG = "RimeEngineController"
    }
    
    private var initialized = false
    private var currentSchema = "luna_pinyin"
    private val nativeBridge = RimeNativeBridge()
    private var pinyinPage = 0
    private var nativeEnabled = false

    fun initialize(
        schema: String,
        nativeEnabled: Boolean,
        sharedDataDir: String? = null,
        userDataDir: String? = null
    ): Boolean {
        return try {
            currentSchema = schema
            pinyinPage = 0

            this.nativeEnabled = if (nativeEnabled && nativeBridge.isNativeAvailable()) {
                val sharedDir = sharedDataDir?.takeIf { it.isNotBlank() }
                val userDir = userDataDir?.takeIf { it.isNotBlank() }
                if (sharedDir != null && userDir != null) {
                    nativeBridge.initialize(schema, sharedDir, userDir)
                } else {
                    Log.w(TAG, "Native Rime requested but directories not provided")
                    false
                }
            } else {
                if (nativeEnabled) {
                    Log.w(TAG, "Native Rime requested but library not available")
                }
                false
            }

            initialized = true
            Log.i(
                TAG,
                "Initialized: schema=$schema, nativeRequested=$nativeEnabled, nativeReady=${nativeBridge.isNativeReady()}"
            )
            initialized
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize", e)
            initialized = false
            false
        }
    }

    fun onStartInput() {
        if (!initialized) return
        try {
            if (nativeEnabled) {
                nativeBridge.onStartInput()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartInput", e)
        }
    }

    fun onFinishInput() {
        if (!initialized) return
        try {
            if (nativeEnabled) {
                nativeBridge.onFinishInput()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onFinishInput", e)
        }
    }

    fun release() {
        try {
            if (initialized && nativeEnabled) {
                nativeBridge.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in release", e)
        } finally {
            initialized = false
        }
    }

    fun suggest(input: String): List<String> {
        if (!initialized) return emptyList()
        
        return try {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return emptyList()

            if (nativeEnabled && nativeBridge.isNativeAvailable()) {
                val nativeCandidates = nativeBridge.queryCandidates(trimmed, limit = 6, page = 0)
                if (nativeCandidates.isNotEmpty()) return nativeCandidates
            }

            val base = when {
                trimmed.length >= 6 -> trimmed.takeLast(6)
                else -> trimmed
            }

            listOf(
                "$base。",
                "$base，好的",
                "$base，谢谢"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in suggest", e)
            emptyList()
        }
    }

    fun suggestFromPinyin(pinyinRaw: String): List<String> {
        if (!initialized) return emptyList()
        
        return try {
            val normalized = pinyinRaw.trim().lowercase()
            if (normalized.isEmpty()) return emptyList()

            if (nativeEnabled && nativeBridge.isNativeAvailable()) {
                val nativeCandidates = nativeBridge.queryCandidates(normalized, limit = 8, page = pinyinPage)
                    .asSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .filterNot { looksLikePlaceholderPinyinCandidate(normalized, it) }
                    .distinct()
                    .toList()
                if (nativeCandidates.isNotEmpty()) {
                    return nativeCandidates
                }
            } else {
                Log.v(TAG, "suggestFromPinyin: native unavailable, using fallback lexicon")
            }

            RimeFallbackLexicon.lookup(normalized, limit = 8, page = pinyinPage)
        } catch (e: Exception) {
            Log.e(TAG, "Error in suggestFromPinyin", e)
            emptyList()
        }
    }

    private fun looksLikePlaceholderPinyinCandidate(rawPinyin: String, candidate: String): Boolean {
        val normalizedCandidate = candidate.trim().lowercase()
        return normalizedCandidate == rawPinyin || normalizedCandidate.startsWith(rawPinyin)
    }

    fun nextPinyinPage() {
        pinyinPage += 1
    }

    fun previousPinyinPage() {
        pinyinPage = (pinyinPage - 1).coerceAtLeast(0)
    }

    fun resetPinyinPage() {
        pinyinPage = 0
    }

    fun currentPinyinPage(): Int = pinyinPage

    fun isNativeEngineActive(): Boolean =
        nativeEnabled && nativeBridge.isNativeAvailable() && nativeBridge.isNativeReady()

    fun currentSchema(): String = currentSchema

    /** Full diagnostic status for developer UI. */
    data class DiagnosticStatus(
        val initialized: Boolean,
        val nativeEnabled: Boolean,
        val nativeActive: Boolean,
        val currentSchema: String,
        val libraryLoaded: Boolean,
        val nativeInitializing: Boolean,
        val nativeReady: Boolean,
        val sharedDataDir: String,
        val userDataDir: String
    )

    fun getDiagnosticStatus(): DiagnosticStatus {
        val bridge = nativeBridge.getDiagnosticStatus()
        return DiagnosticStatus(
            initialized = initialized,
            nativeEnabled = nativeEnabled,
            nativeActive = isNativeEngineActive(),
            currentSchema = currentSchema,
            libraryLoaded = bridge.libraryLoaded,
            nativeInitializing = bridge.nativeInitializing,
            nativeReady = bridge.nativeReady,
            sharedDataDir = bridge.sharedDataDir,
            userDataDir = bridge.userDataDir
        )
    }
}
