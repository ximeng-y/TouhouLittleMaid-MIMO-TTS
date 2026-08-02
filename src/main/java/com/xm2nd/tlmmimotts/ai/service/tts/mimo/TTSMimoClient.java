package com.xm2nd.tlmmimotts.ai.service.tts.mimo;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.TTSCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.service.ErrorCode;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xm2nd.tlmmimotts.TlmMimoTts;
import com.xm2nd.tlmmimotts.server.MimoCloneSampleRepository;
import com.xm2nd.tlmmimotts.util.ChatTextAccessor;
import com.xm2nd.tlmmimotts.util.MimoTextUtil;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * MiMo 语音合成客户端（服务端发起）。
 * <p>
 * 协议：Chat Completions 端点 + {@code api-key} 请求头 + 非流式 WAV 输出；
 * 预置音色 → {@code mimo-v2.5-tts} 直接携带 voiceId；克隆音色 → {@code mimo-v2.5-tts-voiceclone}，
 * 仅在服务端从固定目录读取样本并 Base64 编码为 data URI 写入 {@code audio.voice}。
 * 任何失败都走 TLM 失败回调，诊断信息不含 API Key，绝不静默替换为其他音色。
 */
public class TTSMimoClient implements TTSClient {
    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(30);

    /** 预置音色使用的模型 */
    public static final String PRESET_MODEL = "mimo-v2.5-tts";
    /** 语音克隆使用的模型 */
    public static final String CLONE_MODEL = "mimo-v2.5-tts-voiceclone";

    private final HttpClient httpClient;
    private final TTSMimoSite site;
    private final MimoCloneSampleRepository repository;

    public TTSMimoClient(HttpClient httpClient, TTSMimoSite site) {
        this(httpClient, site, MimoCloneSampleRepository.defaultInstance());
    }

    TTSMimoClient(HttpClient httpClient, TTSMimoSite site, MimoCloneSampleRepository repository) {
        this.httpClient = httpClient;
        this.site = site;
        this.repository = repository;
    }

    @Override
    public void play(String message, TTSConfig config, TTSCallback callback) {
        // 中文语音意图兜底：LLM 可能不遵守 TLM 的"朗读文本与聊天文本同语言"要求，
        // 把朗读文本翻译成了英文；此时改用中文聊天文本朗读
        if (callback instanceof ChatTextAccessor accessor) {
            message = resolveTtsText(message, accessor.getChatText(), config.language());
        }
        HttpRequest request;
        try {
            request = buildRequest(message, config);
        } catch (Exception e) {
            // 合成前的本地校验失败（样本缺失/类型不合法/大小超限/未知音色 ID），明确失败
            callback.onFailure(null, e, ErrorCode.REQUEST_RECEIVED_ERROR);
            return;
        }
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .whenComplete((response, throwable) -> handleResponse(callback, response, throwable, request));
    }

    /**
     * 中文语音意图下的朗读文本兜底：
     * 语言设置以 zh 开头（简体中文等）且聊天文本为中文、朗读文本无汉字时，
     * 说明 LLM 把朗读文本翻译成了其他语言，改用中文聊天文本朗读。
     * 其余情况原样返回（尊重用户选择的语言与 LLM 输出）。
     */
    public static String resolveTtsText(String ttsText, String chatText, String language) {
        if (language != null && language.startsWith("zh")
                && MimoTextUtil.containsHan(chatText) && !MimoTextUtil.containsHan(ttsText)) {
            return chatText;
        }
        return ttsText;
    }

    /**
     * 构造 MiMo Chat Completions 请求。
     * <p>
     * 预置音色 → {@link #PRESET_MODEL} + voiceId；
     * 克隆音色 → {@link #CLONE_MODEL} + 固定目录样本 data URI（仅读取固定目录），
     * 且若该音色配置了描述（mimo-clone/descriptions/ 同名 txt），
     * 描述会作为可选的 user 消息（风格指令/音色描述）随请求传入。
     *
     * @throws MimoCloneSampleRepository.MimoSampleException 本地校验失败（不含 API Key 的诊断）
     * @throws IOException                                   样本读取失败
     */
    HttpRequest buildRequest(String message, TTSConfig config)
            throws MimoCloneSampleRepository.MimoSampleException, IOException {
        MimoVoiceRef ref = resolveVoice(config.model());
        JsonObject body = buildBody(ref.model(), message, ref.voiceValue(), ref.description());

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
                .header("api-key", this.site.secretKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .timeout(MAX_TIMEOUT)
                .uri(URI.create(this.site.url()));
        this.site.headers().forEach(builder::header);
        return builder.build();
    }

    /**
     * 按选中音色分流：预置音色直接使用 voiceId；克隆音色仅在服务端读取固定目录样本。
     */
    private MimoVoiceRef resolveVoice(String voiceId)
            throws MimoCloneSampleRepository.MimoSampleException, IOException {
        if (MimoVoiceIds.isPreset(voiceId)) {
            String presetId = MimoVoiceIds.presetId(voiceId);
            if (presetId.isEmpty()) {
                throw new MimoCloneSampleRepository.MimoSampleException("预置音色 ID 为空");
            }
            return new MimoVoiceRef(PRESET_MODEL, presetId, null);
        }
        if (MimoVoiceIds.isClone(voiceId)) {
            byte[] sample = this.repository.readSample(voiceId);
            String mime = MimoCloneSampleRepository.isMp3(MimoVoiceIds.cloneFileName(voiceId))
                    ? "audio/mpeg" : "audio/wav";
            String dataUri = "data:%s;base64,%s".formatted(mime, Base64.getEncoder().encodeToString(sample));
            return new MimoVoiceRef(CLONE_MODEL, dataUri, this.readDescriptionQuietly(voiceId));
        }
        throw new MimoCloneSampleRepository.MimoSampleException("未知的音色 ID: " + voiceId);
    }

    /**
     * 读取克隆音色描述（可选增强）：描述文件缺失视为无描述；
     * 读取失败仅记录日志，不阻断合成。
     */
    private String readDescriptionQuietly(String voiceId) {
        try {
            return this.repository.readDescription(voiceId);
        } catch (Exception e) {
            TlmMimoTts.LOGGER.warn("MiMo 克隆音色描述读取失败，忽略描述继续合成: {}", voiceId, e);
            return null;
        }
    }

    private static JsonObject buildBody(String model, String text, String voice, String description) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);

        JsonArray messages = new JsonArray();
        // 克隆音色可携带音色描述（风格指令）作为可选的 user 消息
        if (description != null && !description.isEmpty()) {
            JsonObject user = new JsonObject();
            user.addProperty("role", "user");
            user.addProperty("content", description);
            messages.add(user);
        }
        JsonObject assistant = new JsonObject();
        assistant.addProperty("role", "assistant");
        assistant.addProperty("content", text);
        messages.add(assistant);
        body.add("messages", messages);

        JsonObject audio = new JsonObject();
        audio.addProperty("format", "wav");
        audio.addProperty("voice", voice);
        body.add("audio", audio);

        body.addProperty("stream", false);
        return body;
    }

    @Override
    public void handleResponse(TTSCallback callback, HttpResponse<byte[]> response,
                               @Nullable Throwable throwable, HttpRequest request) {
        // 优先检查女仆是否存在
        EntityMaid maid = callback.getMaid();
        if (this.shouldStopChat(maid)) {
            return;
        }
        if (throwable != null) {
            callback.onFailure(request, throwable, ErrorCode.REQUEST_SENDING_ERROR);
            return;
        }
        try {
            byte[] wav = processResponse(response.statusCode(), response.body(), this.site.secretKey());
            callback.onSuccess(wav);
        } catch (MimoResponseException e) {
            callback.onFailure(request, e, ErrorCode.REQUEST_RECEIVED_ERROR);
        }
    }

    /**
     * 处理 MiMo HTTP 响应：
     * 2xx → 解析 {@code choices[0].message.audio.data} 并返回解码后的 WAV 字节；
     * 其余状态码 → 明确失败。诊断信息经过 API Key 脱敏。
     */
    public static byte[] processResponse(int statusCode, byte[] body, String secretKey)
            throws MimoResponseException {
        if (statusCode < 200 || statusCode >= 300) {
            String responseText = safeUtf8(body);
            throw new MimoResponseException(
                    "HTTP Error Code: %d, Response %s".formatted(statusCode, redact(responseText, secretKey)));
        }
        return parseWavResponse(body);
    }

    /**
     * 解析非流式响应中的 {@code choices[0].message.audio.data}（Base64 编码的 WAV）。
     * 响应字段缺失或 Base64 解码失败均抛出明确异常。
     */
    public static byte[] parseWavResponse(byte[] body) throws MimoResponseException {
        JsonObject root;
        try {
            root = JsonParser.parseString(safeUtf8(body)).getAsJsonObject();
        } catch (Exception e) {
            throw new MimoResponseException("MiMo 响应不是合法 JSON", e);
        }
        JsonElement choices = root.get("choices");
        if (choices == null || !choices.isJsonArray() || choices.getAsJsonArray().isEmpty()) {
            throw new MimoResponseException("MiMo 响应缺少 choices 数组");
        }
        JsonElement message = choices.getAsJsonArray().get(0).getAsJsonObject().get("message");
        if (message == null || !message.isJsonObject()) {
            throw new MimoResponseException("MiMo 响应缺少 choices[0].message");
        }
        JsonElement audio = message.getAsJsonObject().get("audio");
        if (audio == null || !audio.isJsonObject()) {
            throw new MimoResponseException("MiMo 响应缺少 choices[0].message.audio");
        }
        JsonElement data = audio.getAsJsonObject().get("data");
        if (data == null || !data.isJsonPrimitive() || !data.getAsJsonPrimitive().isString()) {
            throw new MimoResponseException("MiMo 响应缺少 choices[0].message.audio.data");
        }
        String base64 = data.getAsString();
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new MimoResponseException("MiMo 音频数据不是合法 Base64", e);
        }
    }

    private static String safeUtf8(byte[] body) {
        if (body == null) {
            return "";
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    /** 诊断信息中隐藏 API Key */
    private static String redact(String text, String secretKey) {
        if (secretKey != null && !secretKey.isEmpty()) {
            return text.replace(secretKey, "***");
        }
        return text;
    }

    private record MimoVoiceRef(String model, String voiceValue, String description) {
    }

    /** 响应解析失败；诊断信息不含 API Key */
    public static class MimoResponseException extends Exception {
        public MimoResponseException(String message) {
            super(message);
        }

        public MimoResponseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
