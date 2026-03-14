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

if(EXISTS ${RIME_LIB_DIR}/libRime.so)
    # Find rime_api.h in either location
    if(EXISTS ${RIME_API_INCLUDE_DIR}/rime_api.h)
        set(RIME_INCLUDE ${RIME_API_INCLUDE_DIR})
    elseif(EXISTS ${RIME_API_INCLUDE_DIR_LEGACY}/rime_api.h)
        set(RIME_INCLUDE ${RIME_API_INCLUDE_DIR_LEGACY})
    else()
        message(WARNING "libRime.so found but rime_api.h missing. "
                "Place rime_api.h in app/src/main/cpp/include/")
    endif()

    if(DEFINED RIME_INCLUDE)
        message(STATUS "Linking real libRime: ${RIME_LIB_DIR}/libRime.so")
        message(STATUS "Rime API header: ${RIME_INCLUDE}/rime_api.h")
        add_library(Rime SHARED IMPORTED)
        set_target_properties(Rime PROPERTIES IMPORTED_LOCATION ${RIME_LIB_DIR}/libRime.so)
        target_link_libraries(rime_jni Rime)
        target_include_directories(rime_jni PRIVATE ${RIME_INCLUDE})
        target_compile_definitions(rime_jni PRIVATE HAS_LIBRIME=1)
    else()
        message(STATUS "libRime.so found but no header — building stub-only rime_jni")
        target_compile_definitions(rime_jni PRIVATE HAS_LIBRIME=0)
    endif()
else()
    message(STATUS "No prebuilt libRime.so at ${RIME_LIB_DIR} — building stub-only rime_jni")
    target_compile_definitions(rime_jni PRIVATE HAS_LIBRIME=0)
endif()

find_library(log-lib log)

target_link_libraries(
    rime_jni
    ${log-lib}
)
