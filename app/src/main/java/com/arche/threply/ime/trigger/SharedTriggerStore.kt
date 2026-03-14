package com.arche.threply.ime.trigger

import android.content.Context
import com.arche.threply.data.PrefsManager
import com.arche.threply.ime.model.ImeAiMode
import com.arche.threply.ime.model.ImeTriggerPayload
import com.google.gson.Gson

object SharedTriggerStore {
    private val gson = Gson()

    fun pushTrigger(
        context: Context,
        draft: String,
        source: String,
        mode: ImeAiMode
    ): ImeTriggerPayload {
        val version = PrefsManager.getImeSuggestionVersion(context) + 1L
        val payload = ImeTriggerPayload(
            draft = draft,
            source = source,
            mode = mode,
            createdAt = System.currentTimeMillis(),
            version = version
        )
        PrefsManager.setImePendingTriggerPayload(context, gson.toJson(payload))
        PrefsManager.setImeSuggestionVersion(context, version)
        PrefsManager.setImeLastInputContext(context, draft)
        return payload
    }

    fun peekPending(context: Context): ImeTriggerPayload? {
        val raw = PrefsManager.getImePendingTriggerPayload(context)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching { gson.fromJson(raw, ImeTriggerPayload::class.java) }.getOrNull()
    }

    fun consumePending(context: Context): ImeTriggerPayload? {
        val payload = peekPending(context)
        PrefsManager.setImePendingTriggerPayload(context, null)
        return payload
    }
}
