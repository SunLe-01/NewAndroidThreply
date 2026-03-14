cmake_minimum_required(VERSION 3.22.1)

project(rime_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Enable 16 KB page size support for Android 15+
if(ANDROID_PLATFORM_LEVEL GREATER_EQUAL 35)
    set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
    set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
endif()

add_library(
    rime_jni
    SHARED
    rime_jni.cpp
)

# Phase A scaffold: try to link real librime if prebuilt library is provided.
set(RIME_LIB_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})
set(RIME_API_INCLUDE_DIR ${CMAKE_SOURCE_DIR}/../../../../../threply/Packages/RimeKit/Sources/C)
if(EXISTS ${RIME_LIB_DIR}/libRime.so AND EXISTS ${RIME_API_INCLUDE_DIR}/rime_api.h)
    add_library(Rime SHARED IMPORTED)
    set_target_properties(Rime PROPERTIES IMPORTED_LOCATION ${RIME_LIB_DIR}/libRime.so)
    target_link_libraries(rime_jni Rime)
    target_include_directories(rime_jni PRIVATE ${RIME_API_INCLUDE_DIR})
    target_compile_definitions(rime_jni PRIVATE HAS_LIBRIME=1)
else()
    target_compile_definitions(rime_jni PRIVATE HAS_LIBRIME=0)
endif()

find_library(log-lib log)

target_link_libraries(
    rime_jni
    ${log-lib}
)
