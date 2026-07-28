# 必要なCMakeの最低バージョン
cmake_minimum_required(VERSION 3.22)

# プロジェクト情報
# VERSION    : プロジェクトバージョン
# LANGUAGES  : 使用する言語
project(
        whisper_android
        VERSION 1.0.0
        LANGUAGES C CXX
)

# C++17
set(CMAKE_CXX_STANDARD 17)
# C++17より古いG++を禁止
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Android では JNI から読み込む libwhisper-lib.so へ静的にまとめる
# Androidでは最終的にJNI用の
# libwhisper-lib.so を生成したいので、
# Whisper本体とggmlは静的ライブラリとしてビルドする
#
# ON なら .so
# OFFなら .a
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# AI 推論向けライブラリを有効(arm64-v8aのみ,実機推奨)
if(ANDROID AND CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE)
endif()

# Android NDKでは通常OpenMP(処理のマルチスレッド化)を利用すると高速化できる
set(GGML_OPENMP ON CACHE BOOL "" FORCE)

# 共有ライブラリのABIバージョン
#
# Linux向けでは
# libwhisper.so.1
# のような名前生成に利用される
#
# Androidではほぼ影響なし
set(SOVERSION 1)

# ------------------------
# Release最適化
# ------------------------

# Release時のみ-O3(積極的な最適化)を有効
set(CMAKE_C_FLAGS_RELEASE
        "${CMAKE_C_FLAGS_RELEASE} -O3")

set(CMAKE_CXX_FLAGS_RELEASE
        "${CMAKE_CXX_FLAGS_RELEASE} -O3")

# ------------------------
# LTO (Link Time Optimization)
# ------------------------
# 最適化を統合的に行う(ビルド時間増加の代わりに、高速化)
include(CheckIPOSupported)

check_ipo_supported(
        RESULT IPO_SUPPORTED
        OUTPUT IPO_ERROR
)

# Whisper.cpp公式の CMake を利用してライブラリを作成
# ggmlサブプロジェクトを追加
#
# ggml/CMakeLists.txt を読み込み、
# ggmlライブラリを生成する
add_subdirectory(ggml)

# Whisper本体を追加
#
# src/CMakeLists.txt を読み込み、
# whisperライブラリを生成する
add_subdirectory(src)

# CTranslate2 は Android で利用できない MKL/CUDA を無効化し、Ruy を使う CPU 専用の
# 静的ライブラリとして JNI 共有ライブラリへ組み込む。
set(WITH_MKL OFF CACHE BOOL "" FORCE)
set(WITH_DNNL OFF CACHE BOOL "" FORCE)
set(WITH_ACCELERATE OFF CACHE BOOL "" FORCE)
set(WITH_OPENBLAS OFF CACHE BOOL "" FORCE)
set(WITH_RUY ON CACHE BOOL "" FORCE)
set(WITH_CUDA OFF CACHE BOOL "" FORCE)
set(WITH_CUDNN OFF CACHE BOOL "" FORCE)
set(WITH_HIP OFF CACHE BOOL "" FORCE)
set(OPENMP_RUNTIME NONE CACHE STRING "" FORCE)
set(ENABLE_CPU_DISPATCH OFF CACHE BOOL "" FORCE)
set(BUILD_CLI OFF CACHE BOOL "" FORCE)
set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(RUY_MINIMAL_BUILD ON CACHE BOOL "" FORCE)
set(CPUINFO_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(CPUINFO_BUILD_PKG_CONFIG OFF CACHE BOOL "" FORCE)
add_subdirectory(
        ../CTranslate2/upstream
        ${CMAKE_CURRENT_BINARY_DIR}/ctranslate2
)
# JNI共有ライブラリへ静的リンクするため、上流本体もPICでコンパイルする。
set_target_properties(ctranslate2 PROPERTIES POSITION_INDEPENDENT_CODE ON)

# LTO有効化
if (IPO_SUPPORTED)

    set_property(
            TARGET whisper
            PROPERTY INTERPROCEDURAL_OPTIMIZATION_RELEASE TRUE
    )

    if(TARGET ggml)
        set_property(
                TARGET ggml
                PROPERTY INTERPROCEDURAL_OPTIMIZATION_RELEASE TRUE
        )
    endif()

endif()

# Whisperライブラリへコンパイル時マクロを追加(バージョン情報)
#
# ソースコード内では
#
# #ifdef WHISPER_VERSION
#
# のように参照可能
target_compile_definitions(
        whisper
        PRIVATE
        WHISPER_VERSION="unknown"
)

# JNI用共有ライブラリを作成
#
# Androidから
#
# System.loadLibrary("whisper-lib")
#
# で読み込まれる
add_library(
        # 出力されるライブラリ名
        whisper-lib
        # SHARED = 共有ライブラリ(.so)
        # STATIC = 静的ライブラリ(.a)
        SHARED
        # コンパイル対象のソースファイル
        native-lib.cpp
        ../NativeAudio/pcm16-float-converter.cpp
)

# CTranslate2 のモデル管理、log-Mel生成、Whisper生成処理をJavaへ公開するJNI。
add_library(
        ctranslate2-jni
        SHARED
        ../NativeAudio/pcm16-float-converter.cpp
        ../NativeText/utf-converter.cpp
        ../CTranslate2/ctranslate2-jni.cpp
        ../CTranslate2/whisper-feature-extractor.cpp
        ../CTranslate2/whisper-prompt-tokenizer.cpp
        ../CTranslate2/whisper-token-decoder.cpp
        ../CTranslate2/whisper-vad-filter.cpp
)

target_include_directories(
        ctranslate2-jni
        PRIVATE
        ../CTranslate2
        ../CTranslate2/upstream/include
        ../CTranslate2/upstream/src
        ../CTranslate2/upstream/third_party
        include
)

# JNI側も-O3
target_compile_options(
        whisper-lib
        PRIVATE
        $<$<CONFIG:Release>:-O3>
)

# JNI側もLTO
if (IPO_SUPPORTED)

    set_property(
            TARGET whisper-lib
            PROPERTY INTERPROCEDURAL_OPTIMIZATION_RELEASE TRUE
    )

endif()

# Android NDK標準のログライブラリを検索
#
# __android_log_print()
#
# が利用可能になる
# Android NDK標準のlogライブラリを検索
find_library(log-lib log)

# ライブラリ同士をリンク
target_link_libraries(
        # JNIライブラリ
        whisper-lib
        # Whisper本体
        whisper
        # logライブラリ
        ${log-lib}
)

target_link_libraries(
        ctranslate2-jni
        ctranslate2
        whisper
        ${log-lib}
)
