# 必要なCMakeの最低バージョン
cmake_minimum_required(VERSION 3.22)

# プロジェクト名を定義
project(whisper_android VERSION 1.0.0 LANGUAGES C CXX)

# 使用するC++規格を指定
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Android では JNI から読み込む libwhisper-lib.so へ静的にまとめる
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(SOVERSION 1)

add_subdirectory(ggml)
add_subdirectory(src)

target_compile_definitions(whisper PRIVATE
        WHISPER_VERSION="unknown"
)

# ネイティブライブラリを作成
add_library(
        # 出力されるライブラリ名
        whisper-lib

        # SHARED = 共有ライブラリ(.so)
        SHARED

        # コンパイル対象のソースファイル
        native-lib.cpp
)

# Android NDK標準のlogライブラリを検索
find_library(log-lib log)

# ライブラリ同士をリンク
target_link_libraries(
        whisper-lib
        whisper
        ${log-lib}
)
