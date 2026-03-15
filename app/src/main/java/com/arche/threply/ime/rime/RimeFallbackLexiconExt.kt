package com.arche.threply.ime.rime

/**
 * RimeFallbackLexiconExt — legacy shim.
 * Content has been split into RimeFallbackLexiconExt1 (time/number/verb/adjective)
 * and RimeFallbackLexiconExt2 (location/transport/tech/food/weather/family/health).
 * This object merges both for any callers that reference it directly.
 */
internal object RimeFallbackLexiconExt {
    val entries: Map<String, List<String>> =
        RimeFallbackLexiconExt1.entries + RimeFallbackLexiconExt2.entries
}
