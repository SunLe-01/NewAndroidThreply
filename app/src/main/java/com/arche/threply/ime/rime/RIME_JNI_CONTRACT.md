# Rime JNI Candidate Contract (Phase C-2)

This document defines the JNI contract expected by Android IME Kotlin bridge.

## Kotlin caller
- File: `app/src/main/java/com/arche/threply/ime/rime/RimeNativeBridge.kt`
- Native methods expected:
  - `nativeInitialize(schema: String): Boolean`
  - `nativeOnStartInput()`
  - `nativeOnFinishInput()`
  - `nativeRelease()`
  - `nativeQueryCandidates(schema: String, input: String, limit: Int): Array<String>`

## Candidate query semantics
1. `schema`
   - Value from app prefs, default `luna_pinyin`.
   - Native side should switch schema if needed before querying.

2. `input`
   - Raw pinyin or plain text.
   - Native side should trim and return empty when invalid.

3. `limit`
   - Range expected by Kotlin side: `1..20`.
   - Native side may return fewer results.

4. Return
   - UTF-8 candidate strings in ranking order.
   - Empty array when no result or engine unavailable.

## Error handling
- Kotlin side wraps every JNI call with `runCatching`.
- Native side should avoid throwing; return `false`/empty array on failure.

## Compatibility goal
- This contract allows immediate fallback to `RimeFallbackLexicon` when native is not present,
  while enabling drop-in upgrade once `rime_jni` and actual librime binding are added.
