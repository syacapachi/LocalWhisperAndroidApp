# 必要なCMakeの最低バージョン
cmake_minimum_required(VERSION 3.22)

# プロジェクト名を定義
# VERSION    : プロジェクトバージョン
# LANGUAGES  : 使用する言語
project(whisper_android VERSION 1.0.0 LANGUAGES C CXX)

# 使用するC++規格を指定
set(CMAKE_CXX_STANDARD 17)
# C++17未満へのフォールバックを禁止
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Android では JNI から読み込む libwhisper-lib.so へ静的にまとめる
# Androidでは最終的にJNI用の
# libwhisper-lib.so を生成したいので、
# Whisper本体とggmlは静的ライブラリとしてビルドする
#
# ON なら .so
# OFFなら .a
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Android NDKでは通常OpenMPを利用しないため無効化
# libomp依存を避ける目的
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

# 共有ライブラリのABIバージョン
#
# Linux向けでは
# libwhisper.so.1
# のような名前生成に利用される
#
# Androidではほぼ影響なし
set(SOVERSION 1)

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

# Whisperライブラリへコンパイル時マクロを追加
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
