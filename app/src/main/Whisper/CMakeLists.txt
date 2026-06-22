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
# libomp依存を避ける目的
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