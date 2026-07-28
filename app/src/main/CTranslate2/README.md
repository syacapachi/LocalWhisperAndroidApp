# Android組み込み版CTranslate2

`upstream/` は `C:\Users\emthf\Programming\CTranslate2` の CTranslate2 4.8.1
（コミット `0d8bcd36`）から、C++本体・公開ヘッダー・CMake・Android CPUビルドに
必要な `spdlog`、`cpu_features`、`ruy` をコピーしたものです。ライセンスは
`upstream/LICENSE` にあります。

## Whisperモデル

CTranslate2はWhisper.cpp用の `ggml-*.bin` を読み込めません。Transformers版Whisperを
CTranslate2形式へ変換し、次のディレクトリへ置いてからAPKをビルドしてください。

- openai/whisper-base int8: `app/src/main/assets/ctranslate2/openai-whisper-base-int8/`
- openai/whisper-small int8: `app/src/main/assets/ctranslate2/openai-whisper-small-int8/`
- openai/whisper-medium int8: `app/src/main/assets/ctranslate2/openai-whisper-medium-int8/`
- kotoba-tech/kotoba-whisper-v2.2 int8: `app/src/main/assets/ctranslate2/kotoba-whisper-v2.2-int8/`

`ct2-transformers-converter` のインストールは以下の方法で行います。

```powershell
pip install ctranslate2 transformers
```

各ディレクトリには少なくとも `model.bin`、`config.json`、`vocabulary.json` が必要です。
変換ツールをインストール済みのPowerShellでは、プロジェクトルートから次のように生成できます。

```powershell
python -m ctranslate2.converters.transformers `
  --model C:\Users\emthf\Programming\hf\models--openai--whisper-base\snapshots\e37978b90ca9030d5170a5c07aadb050351a65bb `
  --output_dir app\src\main\assets\ctranslate2\openai-whisper-base-int8 `
  --quantization int8 --copy_files tokenizer.json preprocessor_config.json --low_cpu_mem_usage

python -m ctranslate2.converters.transformers `
  --model C:\Users\emthf\Programming\hf\models--openai--whisper-small\snapshots\973afd24965f72e36ca33b3055d56a652f456b4d `
  --output_dir app\src\main\assets\ctranslate2\openai-whisper-small-int8 `
  --quantization int8 --copy_files tokenizer.json preprocessor_config.json --low_cpu_mem_usage

python -m ctranslate2.converters.transformers `
  --model openai/whisper-medium `
  --output_dir app\src\main\assets\ctranslate2\openai-whisper-medium-int8 `
  --quantization int8 --copy_files tokenizer.json preprocessor_config.json --low_cpu_mem_usage

python -m ctranslate2.converters.transformers `
  --model C:\Users\emthf\Programming\hf\models--kotoba-tech--kotoba-whisper-v2.2\snapshots\9d33482a0eb9b57f1ad80708e8ac5538246d8355 `
  --output_dir app\src\main\assets\ctranslate2\kotoba-whisper-v2.2-int8 `
  --quantization int8 --copy_files tokenizer.json preprocessor_config.json --low_cpu_mem_usage
```

モデルはサイズが大きいため、このリポジトリの `.gitignore` ではassets内のモデルを追跡しません。
設定画面で選んだモデルがない場合、アプリは`model.bin not found`を表示して推論を開始しません。

JNIは16kHz・モノラル・`-1.0～1.0` のfloat PCMを受け取り、Whisper互換の
80-band log-Mel特徴量へ変換してCPU `int8` 推論を行います。リアルタイム推論だけが
CTranslate2の時間窓を使います。音声ファイルと録音全体の再推論はPCM全体を分割せず、
`WhisperCPPTranscriptionWorker`からWhisper.cppの`full` APIへ1回だけ渡します。

VADはCTranslate2の`return_no_speech_prob`と生成スコアを使い、無音確率が設定閾値以上かつ
生成スコアが低い窓を空結果として除外します。翻訳はWhisperが対応する英語向けのみで、入力言語を
自動検出して`<|translate|>`タスクを実行します。プロンプトは`tokenizer.json`のbyte-level BPEで
最大224 tokenへ変換し、リアルタイム推論ではユーザー入力に直前結果の末尾100文字を追加します。

エンジン固有処理は`CTranslate2TranscriptionWorker`と`WhisperCPPTranscriptionWorker`へ分離し、
既存の`WhisperTranscriptionWorker`と`WhisperFileTranscriptionWorker`は音声入力、スレッド終了、
イベント通知を担当します。medium選択時のWhisper.cpp一括推論は、未同梱のmedium ggmlの代わりに
`ggml-small_q8_0.bin`を使います。
