#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <cctype>
#include <mutex>
#include <string>
#include <vector>

#ifndef HAS_LIBRIME
#define HAS_LIBRIME 0
#endif

#if HAS_LIBRIME
#include <rime_api.h>
#endif

#define LOG_TAG "rime_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {

std::mutex gMutex;
std::string gSharedDataDir;
std::string gUserDataDir;
std::string gCurrentSchema = "luna_pinyin";
bool gInitialized = false;

#if HAS_LIBRIME
bool gRimeRuntimeInitialized = false;
RimeSessionId gSessionId = 0;
RimeApi* gRimeApi = nullptr;

bool ensureApiLoadedLocked() {
    if (gRimeApi != nullptr) {
        return true;
    }
    gRimeApi = rime_get_api();
    if (gRimeApi == nullptr) {
        LOGW("rime_get_api returned null");
        return false;
    }
    return true;
}

void destroySessionLocked() {
    if (!ensureApiLoadedLocked() || gRimeApi->find_session == nullptr || gRimeApi->destroy_session == nullptr) {
        gSessionId = 0;
        return;
    }
    if (gSessionId != 0 && gRimeApi->find_session(gSessionId)) {
        gRimeApi->destroy_session(gSessionId);
    }
    gSessionId = 0;
}

bool ensureSessionLocked() {
    if (!ensureApiLoadedLocked() || gRimeApi->find_session == nullptr || gRimeApi->create_session == nullptr) {
        return false;
    }
    if (gSessionId != 0 && gRimeApi->find_session(gSessionId)) {
        return true;
    }
    gSessionId = gRimeApi->create_session();
    return gSessionId != 0 && gRimeApi->find_session(gSessionId);
}

bool selectSchemaLocked(const std::string& schema) {
    if (schema.empty()) {
        return false;
    }
    if (!ensureSessionLocked()) {
        return false;
    }
    if (gRimeApi->select_schema == nullptr) {
        return false;
    }
    return gRimeApi->select_schema(gSessionId, schema.c_str()) == True;
}

bool initializeRimeLocked() {
    if (gRimeRuntimeInitialized) {
        return ensureSessionLocked();
    }
    if (gSharedDataDir.empty() || gUserDataDir.empty()) {
        return false;
    }
    if (!ensureApiLoadedLocked()) {
        return false;
    }
    if (gRimeApi->setup == nullptr ||
        gRimeApi->initialize == nullptr ||
        gRimeApi->start_maintenance == nullptr ||
        gRimeApi->join_maintenance_thread == nullptr) {
        LOGW("RimeApi is missing required lifecycle functions");
        return false;
    }

    RIME_STRUCT(RimeTraits, traits);
    traits.shared_data_dir = gSharedDataDir.c_str();
    traits.user_data_dir = gUserDataDir.c_str();
    traits.app_name = "rime.threply.android";

    gRimeApi->setup(&traits);
    gRimeApi->initialize(&traits);
    gRimeRuntimeInitialized = true;

    // Ensure deploy/build artifacts are generated if needed.
    gRimeApi->start_maintenance(False);
    gRimeApi->join_maintenance_thread();

    if (!ensureSessionLocked()) {
        return false;
    }
    if (!gCurrentSchema.empty()) {
        selectSchemaLocked(gCurrentSchema);
    }
    return true;
}

void releaseRimeLocked() {
    destroySessionLocked();
    if (gRimeRuntimeInitialized && ensureApiLoadedLocked() && gRimeApi->finalize != nullptr) {
        gRimeApi->finalize();
    }
    gRimeRuntimeInitialized = false;
}

bool feedPinyinLocked(const std::string& rawInput) {
    if (!ensureSessionLocked()) {
        return false;
    }
    if (gRimeApi->clear_composition == nullptr || gRimeApi->process_key == nullptr) {
        return false;
    }
    gRimeApi->clear_composition(gSessionId);

    bool handled = false;
    for (char ch : rawInput) {
        unsigned char uch = static_cast<unsigned char>(ch);
        if (std::isspace(uch)) {
            continue;
        }
        char normalized = static_cast<char>(std::tolower(uch));
        if ((normalized >= 'a' && normalized <= 'z') ||
            (normalized >= '0' && normalized <= '9') ||
            normalized == '\'') {
            handled = gRimeApi->process_key(gSessionId, static_cast<int>(normalized), 0) == True || handled;
        }
    }
    return handled;
}

std::vector<std::string> collectCandidatesLocked(int limit, int page) {
    std::vector<std::string> results;
    if (!ensureSessionLocked()) {
        return results;
    }

    int safeLimit = std::clamp(limit, 1, 20);
    int safePage = std::max(page, 0);

    RIME_STRUCT(RimeContext, context);
    if (gRimeApi->get_context == nullptr ||
        gRimeApi->free_context == nullptr ||
        gRimeApi->process_key == nullptr) {
        return results;
    }
    if (!gRimeApi->get_context(gSessionId, &context)) {
        return results;
    }

    while (context.menu.page_no < safePage && !context.menu.is_last_page) {
        gRimeApi->free_context(&context);
        if (gRimeApi->process_key(gSessionId, 0xff56, 0) != True) {  // XK_Next/PageDown
            return results;
        }
        RIME_STRUCT(RimeContext, nextContext);
        if (!gRimeApi->get_context(gSessionId, &nextContext)) {
            return results;
        }
        context = nextContext;
    }

    if (context.menu.page_no == safePage && context.menu.num_candidates > 0 && context.menu.candidates != nullptr) {
        int count = std::min(context.menu.num_candidates, safeLimit);
        for (int i = 0; i < count; ++i) {
            const char* text = context.menu.candidates[i].text;
            if (text != nullptr && text[0] != '\0') {
                results.emplace_back(text);
            }
        }
    }

    gRimeApi->free_context(&context);
    return results;
}
#endif

jobjectArray toJavaStringArray(JNIEnv* env, const std::vector<std::string>& values) {
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(values.size()), stringClass, nullptr);
    for (size_t i = 0; i < values.size(); ++i) {
        env->SetObjectArrayElement(result, static_cast<jsize>(i), env->NewStringUTF(values[i].c_str()));
    }
    return result;
}

}  // namespace

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_arche_threply_ime_rime_RimeNativeBridge_nativeInitialize(
        JNIEnv* env,
        jobject /*thiz*/,
        jstring schema,
        jstring sharedDataDir,
        jstring userDataDir) {
    const char* schemaChars = env->GetStringUTFChars(schema, nullptr);
    const char* sharedDirChars = env->GetStringUTFChars(sharedDataDir, nullptr);
    const char* userDirChars = env->GetStringUTFChars(userDataDir, nullptr);

    std::string schemaName = schemaChars != nullptr ? schemaChars : "luna_pinyin";
    std::string sharedDir = sharedDirChars != nullptr ? sharedDirChars : "";
    std::string userDir = userDirChars != nullptr ? userDirChars : "";

    if (schemaChars != nullptr) env->ReleaseStringUTFChars(schema, schemaChars);
    if (sharedDirChars != nullptr) env->ReleaseStringUTFChars(sharedDataDir, sharedDirChars);
    if (userDirChars != nullptr) env->ReleaseStringUTFChars(userDataDir, userDirChars);

    std::lock_guard<std::mutex> guard(gMutex);
    gCurrentSchema = schemaName;
    gSharedDataDir = sharedDir;
    gUserDataDir = userDir;
    gInitialized = !gSharedDataDir.empty() && !gUserDataDir.empty();

#if HAS_LIBRIME
    if (gInitialized) {
        if (!initializeRimeLocked()) {
            gInitialized = false;
            LOGW("nativeInitialize failed to initialize librime runtime");
            return JNI_FALSE;
        }
        if (!gCurrentSchema.empty()) {
            selectSchemaLocked(gCurrentSchema);
        }
    }
#endif

    LOGI("nativeInitialize schema=%s shared=%s user=%s HAS_LIBRIME=%d initialized=%d",
         gCurrentSchema.c_str(),
         gSharedDataDir.c_str(),
         gUserDataDir.c_str(),
         HAS_LIBRIME,
         gInitialized ? 1 : 0);
#if !HAS_LIBRIME
    LOGW("nativeInitialize: compiled WITHOUT librime (HAS_LIBRIME=0). "
         "Native Rime is disabled — all queries will return empty. "
         "To enable: place libRime.so in jniLibs/<ABI>/ and rebuild.");
#endif
    return gInitialized ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arche_threply_ime_rime_RimeNativeBridge_nativeOnStartInput(
        JNIEnv* /*env*/,
        jobject /*thiz*/) {
    std::lock_guard<std::mutex> guard(gMutex);
#if HAS_LIBRIME
    if (gInitialized) {
        initializeRimeLocked();
        if (!gCurrentSchema.empty()) {
            selectSchemaLocked(gCurrentSchema);
        }
    }
#endif
    LOGI("nativeOnStartInput schema=%s initialized=%d", gCurrentSchema.c_str(), gInitialized ? 1 : 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arche_threply_ime_rime_RimeNativeBridge_nativeOnFinishInput(
        JNIEnv* /*env*/,
        jobject /*thiz*/) {
    std::lock_guard<std::mutex> guard(gMutex);
#if HAS_LIBRIME
    if (ensureApiLoadedLocked() &&
        gSessionId != 0 &&
        gRimeApi->find_session != nullptr &&
        gRimeApi->clear_composition != nullptr &&
        gRimeApi->find_session(gSessionId)) {
        gRimeApi->clear_composition(gSessionId);
    }
#endif
    LOGI("nativeOnFinishInput schema=%s initialized=%d", gCurrentSchema.c_str(), gInitialized ? 1 : 0);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_arche_threply_ime_rime_RimeNativeBridge_nativeRelease(
        JNIEnv* /*env*/,
        jobject /*thiz*/) {
    std::lock_guard<std::mutex> guard(gMutex);
#if HAS_LIBRIME
    releaseRimeLocked();
#endif
    gInitialized = false;
    gSharedDataDir.clear();
    gUserDataDir.clear();
    LOGI("nativeRelease");
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_arche_threply_ime_rime_RimeNativeBridge_nativeQueryCandidates(
        JNIEnv* env,
        jobject /*thiz*/,
        jstring schema,
        jstring input,
        jint limit,
        jint page) {
    const char* schemaChars = env->GetStringUTFChars(schema, nullptr);
    const char* inputChars = env->GetStringUTFChars(input, nullptr);

    std::string schemaName = schemaChars != nullptr ? schemaChars : "luna_pinyin";
    std::string raw = inputChars != nullptr ? inputChars : "";

    if (schemaChars != nullptr) env->ReleaseStringUTFChars(schema, schemaChars);
    if (inputChars != nullptr) env->ReleaseStringUTFChars(input, inputChars);

    int safeLimit = std::clamp(static_cast<int>(limit), 1, 20);
    int safePage = std::max(static_cast<int>(page), 0);

    std::lock_guard<std::mutex> guard(gMutex);
    if (!gInitialized || raw.empty()) {
        return toJavaStringArray(env, {});
    }

#if HAS_LIBRIME
    if (!initializeRimeLocked()) {
        return toJavaStringArray(env, {});
    }

    if (!schemaName.empty() && schemaName != gCurrentSchema) {
        gCurrentSchema = schemaName;
        selectSchemaLocked(gCurrentSchema);
    } else if (!gCurrentSchema.empty()) {
        selectSchemaLocked(gCurrentSchema);
    }

    if (!feedPinyinLocked(raw)) {
        return toJavaStringArray(env, {});
    }

    const std::vector<std::string> candidates = collectCandidatesLocked(safeLimit, safePage);
    return toJavaStringArray(env, candidates);
#else
    LOGW("nativeQueryCandidates: HAS_LIBRIME=0, returning empty (stub mode)");
    return toJavaStringArray(env, {});
#endif
}
