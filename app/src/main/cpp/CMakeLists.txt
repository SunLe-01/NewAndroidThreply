cmake_minimum_required(VERSION 3.22.1)

project(rime_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Always enable 16 KB page size alignment for broad device compatibility (Android 15+).
# This is harmless on 4 KB devices and required on 16 KB page-size devices.
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

add_library(
    rime_jni
    SHARED
    rime_jni.cpp
)

# Try to link real librime if prebuilt library is provided.
# Place libRime.so in app/src/main/jniLibs/<ABI>/ and rime_api.h in app/src/main/cpp/include/.
set(RIME_LIB_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})
set(RIME_API_INCLUDE_DIR ${CMAKE_SOURCE_DIR}/include)

# Also check the legacy iOS-relative path as fallback
set(RIME_API_INCLUDE_DIR_LEGACY ${CMAKE_SOURCE_DIR}/../../../../../threply/Packages/RimeKit/Sources/C)

# ── Diagnostics ──
message(STATUS "──────────────────────────────────────────────")
message(STATUS "Rime native integration check")
message(STATUS "  ABI:            ${ANDROID_ABI}")
message(STATUS "  libRime.so dir: ${RIME_LIB_DIR}")
message(STATUS "  rime_api.h dir: ${RIME_API_INCLUDE_DIR}")

set(RIME_HAS_SO FALSE)
set(RIME_HAS_HEADER FALSE)

if(EXISTS ${RIME_LIB_DIR}/libRime.so)
    set(RIME_HAS_SO TRUE)
    file(SIZE ${RIME_LIB_DIR}/libRime.so RIME_SO_SIZE)
    message(STATUS "  libRime.so:     FOUND (${RIME_SO_SIZE} bytes)")
else()
    message(STATUS "  libRime.so:     NOT FOUND")
    message(STATUS "  → To enable native Rime, place a prebuilt libRime.so for ${ANDROID_ABI}")
    message(STATUS "    at: app/src/main/jniLibs/${ANDROID_ABI}/libRime.so")
endif()

# Find rime_api.h in either location
if(EXISTS ${RIME_API_INCLUDE_DIR}/rime_api.h)
    set(RIME_INCLUDE ${RIME_API_INCLUDE_DIR})
    set(RIME_HAS_HEADER TRUE)
    message(STATUS "  rime_api.h:     FOUND at ${RIME_INCLUDE}")
elseif(EXISTS ${RIME_API_INCLUDE_DIR_LEGACY}/rime_api.h)
    set(RIME_INCLUDE ${RIME_API_INCLUDE_DIR_LEGACY})
    set(RIME_HAS_HEADER TRUE)
    message(STATUS "  rime_api.h:     FOUND at ${RIME_INCLUDE} (legacy path)")
else()
    message(STATUS "  rime_api.h:     NOT FOUND")
endif()

if(RIME_HAS_SO AND RIME_HAS_HEADER)
    message(STATUS "  Result:         HAS_LIBRIME=1 (full native Rime)")
    add_library(Rime SHARED IMPORTED)
    set_target_properties(Rime PROPERTIES IMPORTED_LOCATION ${RIME_LIB_DIR}/libRime.so)
    target_link_libraries(rime_jni Rime)
    target_include_directories(rime_jni PRIVATE ${RIME_INCLUDE})
    target_compile_definitions(rime_jni PRIVATE HAS_LIBRIME=1)
elseif(RIME_HAS_SO AND NOT RIME_HAS_HEADER)
    message(WARNING "libRime.so found but rime_api.h missing — cannot compile native Rime")
    message(STATUS "  Result:         HAS_LIBRIME=0 (stub-only)")
    target_compile_definitions(rime_jni PRIVATE HAS_LIBRIME=0)
else()
    message(STATUS "  Result:         HAS_LIBRIME=0 (stub-only, fallback lexicon)")
    target_compile_definitions(rime_jni PRIVATE HAS_LIBRIME=0)
endif()
message(STATUS "──────────────────────────────────────────────")

find_library(log-lib log)

target_link_libraries(
    rime_jni
    ${log-lib}
)
