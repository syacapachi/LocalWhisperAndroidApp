# Android組み込み版CTranslate2

`upstream/` は `C:\Users\emthf\Programming\CTranslate2` の CTranslate2 4.8.1
（コミット `0d8bcd36`）から、C++本体・公開ヘッダー・CMake・Android CPUビルドに
必要な `spdlog`、`cpu_features`、`ruy` をコピーしたものです。ライセンスは
`upstream/LICENSE` にあります。

## Whisperモデル

CTranslate2はWhisper.cpp用の `ggml-*.bin` を読み込めません。Transformers版Whisperを
CTranslate2形式へ変換し、次のディレクトリへ置いてからAPKをビルドしてください。

- base: `app/src/main/assets/ctranslate2/base/`
- small: `app/src/main/assets/ctranslate2/small/`

各ディレクトリには少なくとも `model.bin`、`config.json`、`vocabulary.json` が必要です。
変換ツールをインストール済みのPowerShellでは、プロジェクトルートから次のように生成できます。

```powershell
ct2-transformers-converter --model openai/whisper-base --output_dir app/src/main/assets/ctranslate2/base --quantization int8
ct2-transformers-converter --model openai/whisper-small --output_dir app/src/main/assets/ctranslate2/small --quantization int8
```

モデルはサイズが大きいため、このリポジトリの `.gitignore` ではassets内のモデルを追跡しません。
設定画面で「CTranslate2を使う」を有効にした際に対応モデルがない場合、アプリは
`model.bin not found` を表示して推論を開始しません。

JNIは16kHz・モノラル・`-1.0～1.0` のfloat PCMを受け取り、Whisper互換の
80-band log-Mel特徴量へ変換してCPU `int8` 推論を行います。1回のnative推論は最大30秒で、
ファイル文字起こしはJava側で30秒ごとに分割します。
