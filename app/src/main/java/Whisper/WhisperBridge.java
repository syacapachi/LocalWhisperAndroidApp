package Whisper;

public class WhisperBridge {

    public static final int SAMPLING_GREEDY = 0;
    public static final int SAMPLING_BEAM_SEARCH = 1;

    public static final int AHEADS_NONE = 0;
    public static final int AHEADS_N_TOP_MOST = 1;
    public static final int AHEADS_CUSTOM = 2;
    public static final int AHEADS_TINY_EN = 3;
    public static final int AHEADS_TINY = 4;
    public static final int AHEADS_BASE_EN = 5;
    public static final int AHEADS_BASE = 6;
    public static final int AHEADS_SMALL_EN = 7;
    public static final int AHEADS_SMALL = 8;
    public static final int AHEADS_MEDIUM_EN = 9;
    public static final int AHEADS_MEDIUM = 10;
    public static final int AHEADS_LARGE_V1 = 11;
    public static final int AHEADS_LARGE_V2 = 12;
    public static final int AHEADS_LARGE_V3 = 13;
    public static final int AHEADS_LARGE_V3_TURBO = 14;

    static {
        // native を解決するためにのライブラリを読み込む
        System.loadLibrary("whisper-lib");
    }

    public static String transcribe(String modelPath, float[] pcmData) {
        long context = initFromFile(modelPath, defaultContextParams());
        if (context == 0) {
            return "model load failed";
        }

        try {
            FullParams params = defaultFullParams(SAMPLING_GREEDY);
            params.printProgress = false;
            params.printSpecial = false;
            params.printRealtime = false;
            params.printTimestamps = false;

            int result = full(context, params, pcmData);
            return result == 0 ? getText(context) : "";
        } finally {
            freeContext(context);
        }
    }

    public static native String version();
    public static native String systemInfo();

    public static native ContextParams defaultContextParams();
    public static native FullParams defaultFullParams(int strategy);
    public static native VadParams defaultVadParams();
    public static native VadContextParams defaultVadContextParams();

    public static native long initFromFile(String modelPath, ContextParams params);
    public static native long initFromFileNoState(String modelPath, ContextParams params);
    public static native long initFromBuffer(byte[] modelBuffer, ContextParams params);
    public static native long initFromBufferNoState(byte[] modelBuffer, ContextParams params);
    public static native long initState(long context);
    public static native void freeContext(long context);
    public static native void freeState(long state);

    public static native int full(long context, FullParams params, float[] pcmData);
    public static native int fullWithState(long context, long state, FullParams params, float[] pcmData);
    public static native int fullParallel(long context, FullParams params, float[] pcmData, int processors);

    public static native int pcmToMel(long context, float[] samples, int threads);
    public static native int pcmToMelWithState(long context, long state, float[] samples, int threads);
    public static native int setMel(long context, float[] melData, int melLength, int melBands);
    public static native int setMelWithState(long context, long state, float[] melData, int melLength, int melBands);
    public static native int encode(long context, int offset, int threads);
    public static native int encodeWithState(long context, long state, int offset, int threads);
    public static native int decode(long context, int[] tokens, int pastTokens, int threads);
    public static native int decodeWithState(long context, long state, int[] tokens, int pastTokens, int threads);

    public static native int[] tokenize(long context, String text, int maxTokens);
    public static native int tokenCount(long context, String text);
    public static native String tokenToString(long context, int token);
    public static native int tokenEot(long context);
    public static native int tokenSot(long context);
    public static native int tokenSolm(long context);
    public static native int tokenPrev(long context);
    public static native int tokenNosp(long context);
    public static native int tokenNot(long context);
    public static native int tokenBeg(long context);
    public static native int tokenLang(long context, int languageId);
    public static native int tokenTranslate(long context);
    public static native int tokenTranscribe(long context);

    public static native int langMaxId();
    public static native int langId(String language);
    public static native String langStr(int languageId);
    public static native String langStrFull(int languageId);
    public static native LangDetection langAutoDetect(long context, int offsetMs, int threads);
    public static native LangDetection langAutoDetectWithState(long context, long state, int offsetMs, int threads);

    public static native int nLen(long context);
    public static native int nLenFromState(long state);
    public static native int nVocab(long context);
    public static native int nTextCtx(long context);
    public static native int nAudioCtx(long context);
    public static native boolean isMultilingual(long context);
    public static native int modelNVocab(long context);
    public static native int modelNAudioCtx(long context);
    public static native int modelNAudioState(long context);
    public static native int modelNAudioHead(long context);
    public static native int modelNAudioLayer(long context);
    public static native int modelNTextCtx(long context);
    public static native int modelNTextState(long context);
    public static native int modelNTextHead(long context);
    public static native int modelNTextLayer(long context);
    public static native int modelNMels(long context);
    public static native int modelFType(long context);
    public static native int modelType(long context);
    public static native String modelTypeReadable(long context);
    public static native float[] logits(long context);
    public static native float[] logitsFromState(long state, int vocabularySize);

    public static native int fullNSegments(long context);
    public static native int fullNSegmentsFromState(long state);
    public static native int fullLangId(long context);
    public static native int fullLangIdFromState(long state);
    public static native long fullSegmentT0(long context, int segment);
    public static native long fullSegmentT0FromState(long state, int segment);
    public static native long fullSegmentT1(long context, int segment);
    public static native long fullSegmentT1FromState(long state, int segment);
    public static native boolean fullSegmentSpeakerTurnNext(long context, int segment);
    public static native boolean fullSegmentSpeakerTurnNextFromState(long state, int segment);
    public static native String fullSegmentText(long context, int segment);
    public static native String fullSegmentTextFromState(long state, int segment);
    public static native int fullNTokens(long context, int segment);
    public static native int fullNTokensFromState(long state, int segment);
    public static native String fullTokenText(long context, int segment, int token);
    public static native String fullTokenTextFromState(long context, long state, int segment, int token);
    public static native int fullTokenId(long context, int segment, int token);
    public static native int fullTokenIdFromState(long state, int segment, int token);
    public static native TokenData fullTokenData(long context, int segment, int token);
    public static native TokenData fullTokenDataFromState(long state, int segment, int token);
    public static native float fullTokenProbability(long context, int segment, int token);
    public static native float fullTokenProbabilityFromState(long state, int segment, int token);
    public static native float fullSegmentNoSpeechProbability(long context, int segment);
    public static native float fullSegmentNoSpeechProbabilityFromState(long state, int segment);

    public static native Timings timings(long context);
    public static native void printTimings(long context);
    public static native void resetTimings(long context);

    public static native long vadInitFromFile(String modelPath, VadContextParams params);
    public static native boolean vadDetectSpeech(long vadContext, float[] samples);
    public static native boolean vadDetectSpeechNoReset(long vadContext, float[] samples);
    public static native void vadResetState(long vadContext);
    public static native float[] vadProbabilities(long vadContext);
    public static native long vadSegmentsFromProbabilities(long vadContext, VadParams params);
    public static native long vadSegmentsFromSamples(long vadContext, VadParams params, float[] samples);
    public static native int vadSegmentCount(long vadSegments);
    public static native float vadSegmentT0(long vadSegments, int segment);
    public static native float vadSegmentT1(long vadSegments, int segment);
    public static native void vadFreeSegments(long vadSegments);
    public static native void vadFree(long vadContext);

    public static native int benchMemcpy(int threads);
    public static native String benchMemcpyString(int threads);
    public static native int benchGgmlMulMat(int threads);
    public static native String benchGgmlMulMatString(int threads);

    public static Segment[] getSegments(long context) {
        int count = fullNSegments(context);
        Segment[] segments = new Segment[count];

        for (int i = 0; i < count; i++) {
            segments[i] = collectSegment(context, i);
        }

        return segments;
    }

    public static Segment[] getSegmentsFromState(long state) {
        int count = fullNSegmentsFromState(state);
        Segment[] segments = new Segment[count];

        for (int i = 0; i < count; i++) {
            segments[i] = collectSegmentFromState(state, i);
        }

        return segments;
    }

    public static String getText(long context) {
        StringBuilder builder = new StringBuilder();
        int count = fullNSegments(context);

        for (int i = 0; i < count; i++) {
            builder.append(fullSegmentText(context, i));
        }

        return builder.toString();
    }

    public static ModelInfo getModelInfo(long context) {
        ModelInfo info = new ModelInfo();
        info.nLen = nLen(context);
        info.nVocab = nVocab(context);
        info.nTextCtx = nTextCtx(context);
        info.nAudioCtx = nAudioCtx(context);
        info.multilingual = isMultilingual(context);
        info.modelNVocab = modelNVocab(context);
        info.modelNAudioCtx = modelNAudioCtx(context);
        info.modelNAudioState = modelNAudioState(context);
        info.modelNAudioHead = modelNAudioHead(context);
        info.modelNAudioLayer = modelNAudioLayer(context);
        info.modelNTextCtx = modelNTextCtx(context);
        info.modelNTextState = modelNTextState(context);
        info.modelNTextHead = modelNTextHead(context);
        info.modelNTextLayer = modelNTextLayer(context);
        info.modelNMels = modelNMels(context);
        info.modelFType = modelFType(context);
        info.modelType = modelType(context);
        info.modelTypeReadable = modelTypeReadable(context);
        return info;
    }

    private static Segment collectSegment(long context, int segmentIndex) {
        Segment segment = new Segment();
        segment.index = segmentIndex;
        segment.t0 = fullSegmentT0(context, segmentIndex);
        segment.t1 = fullSegmentT1(context, segmentIndex);
        segment.text = fullSegmentText(context, segmentIndex);
        segment.speakerTurnNext = fullSegmentSpeakerTurnNext(context, segmentIndex);
        segment.noSpeechProbability = fullSegmentNoSpeechProbability(context, segmentIndex);

        int tokenCount = fullNTokens(context, segmentIndex);
        segment.tokens = new TokenData[tokenCount];

        for (int i = 0; i < tokenCount; i++) {
            segment.tokens[i] = fullTokenData(context, segmentIndex, i);
            segment.tokens[i].text = fullTokenText(context, segmentIndex, i);
        }

        return segment;
    }

    private static Segment collectSegmentFromState(long state, int segmentIndex) {
        Segment segment = new Segment();
        segment.index = segmentIndex;
        segment.t0 = fullSegmentT0FromState(state, segmentIndex);
        segment.t1 = fullSegmentT1FromState(state, segmentIndex);
        segment.text = fullSegmentTextFromState(state, segmentIndex);
        segment.speakerTurnNext = fullSegmentSpeakerTurnNextFromState(state, segmentIndex);
        segment.noSpeechProbability = fullSegmentNoSpeechProbabilityFromState(state, segmentIndex);

        int tokenCount = fullNTokensFromState(state, segmentIndex);
        segment.tokens = new TokenData[tokenCount];

        for (int i = 0; i < tokenCount; i++) {
            segment.tokens[i] = fullTokenDataFromState(state, segmentIndex, i);
        }

        return segment;
    }

    public static class ContextParams {
        public boolean useGpu;
        public boolean flashAttn;
        public int gpuDevice;
        public boolean dtwTokenTimestamps;
        public int dtwAheadsPreset;
        public int dtwNTop;
        public long dtwMemSize;
    }

    public static class FullParams {
        public int strategy = SAMPLING_GREEDY;
        public int nThreads;
        public int nMaxTextCtx;
        public int offsetMs;
        public int durationMs;
        public boolean translate;
        public boolean noContext;
        public boolean noTimestamps;
        public boolean singleSegment;
        public boolean printSpecial;
        public boolean printProgress;
        public boolean printRealtime;
        public boolean printTimestamps;
        public boolean tokenTimestamps;
        public float tholdPt;
        public float tholdPtsum;
        public int maxLen;
        public boolean splitOnWord;
        public int maxTokens;
        public boolean debugMode;
        public int audioCtx;
        public boolean tdrzEnable;
        public String suppressRegex;
        public String initialPrompt;
        public boolean carryInitialPrompt;
        public int[] promptTokens;
        public String language;
        public boolean detectLanguage;
        public boolean suppressBlank;
        public boolean suppressNst;
        public float temperature;
        public float maxInitialTs;
        public float lengthPenalty;
        public float temperatureInc;
        public float entropyThold;
        public float logprobThold;
        public float noSpeechThold;
        public int greedyBestOf;
        public int beamSize;
        public float beamPatience;
        public boolean vad;
        public String vadModelPath;
        public VadParams vadParams;
    }

    public static class VadParams {
        public float threshold;
        public int minSpeechDurationMs;
        public int minSilenceDurationMs;
        public float maxSpeechDurationS;
        public int speechPadMs;
        public float samplesOverlap;
    }

    public static class VadContextParams {
        public int nThreads;
        public boolean useGpu;
        public int gpuDevice;
    }

    public static class TokenData {
        public int id;
        public int tid;
        public float p;
        public float plog;
        public float pt;
        public float ptsum;
        public long t0;
        public long t1;
        public long tDtw;
        public float vlen;
        public String text;
    }

    public static class Segment {
        public int index;
        public long t0;
        public long t1;
        public String text;
        public boolean speakerTurnNext;
        public float noSpeechProbability;
        public TokenData[] tokens;
    }

    public static class Timings {
        public float sampleMs;
        public float encodeMs;
        public float decodeMs;
        public float batchdMs;
        public float promptMs;
    }

    public static class LangDetection {
        public int languageId;
        public float[] probabilities;
    }

    public static class ModelInfo {
        public int nLen;
        public int nVocab;
        public int nTextCtx;
        public int nAudioCtx;
        public boolean multilingual;
        public int modelNVocab;
        public int modelNAudioCtx;
        public int modelNAudioState;
        public int modelNAudioHead;
        public int modelNAudioLayer;
        public int modelNTextCtx;
        public int modelNTextState;
        public int modelNTextHead;
        public int modelNTextLayer;
        public int modelNMels;
        public int modelFType;
        public int modelType;
        public String modelTypeReadable;
    }
}
