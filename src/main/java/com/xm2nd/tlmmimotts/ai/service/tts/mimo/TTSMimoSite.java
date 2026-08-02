package com.xm2nd.tlmmimotts.ai.service.tts.mimo;

import com.github.tartaricacid.touhoulittlemaid.ai.service.SupportModelSelect;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSSite;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.ai.layout.TTSSiteFormLayout;
import com.xm2nd.tlmmimotts.client.gui.layout.TTSMimoFormLayout;
import com.xm2nd.tlmmimotts.server.MimoCloneSampleRepository;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MiMo TTS 站点。预置音色与克隆音色统一显示在同一个音色列表中：
 * <ul>
 *   <li>{@code preset:<voiceId>}：内置预置音色；</li>
 *   <li>{@code clone:<filename>}：固定目录中的参考音频，仅保存文件名元数据，样本永不出现在配置或客户端。</li>
 * </ul>
 */
public final class TTSMimoSite implements TTSSite, SupportModelSelect {
    public static final String API_TYPE = "mimo";

    private final String id;
    private final ResourceLocation icon;
    private final Map<String, String> headers;
    private final Map<String, String> models;

    private String url;
    private boolean enabled;
    private String secretKey;

    public TTSMimoSite(String id, ResourceLocation icon, String url, boolean enabled, String secretKey,
                       Map<String, String> headers, Map<String, String> models) {
        this.id = id;
        this.icon = icon;
        this.url = url;
        this.enabled = enabled;
        this.secretKey = secretKey;
        this.headers = Map.copyOf(headers);
        // 音色列表需要支持服务端刷新时增删克隆条目，使用可变副本
        this.models = new LinkedHashMap<>(models);
    }

    @Override
    public String getApiType() {
        return API_TYPE;
    }

    @Override
    public TTSClient client() {
        return new TTSMimoClient(TTS_HTTP_CLIENT, this);
    }

    @Override
    public TTSSiteFormLayout formLayout() {
        return new TTSMimoFormLayout(this);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public ResourceLocation icon() {
        return icon;
    }

    @Override
    public String url() {
        return url;
    }

    public String secretKey() {
        return secretKey;
    }

    @Override
    public Map<String, String> headers() {
        return headers;
    }

    @Override
    public Map<String, String> models() {
        return models;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * 重建克隆音色：保留预置音色，删除旧克隆条目，写入固定目录扫描结果（仅文件名元数据）。
     *
     * @return 当前克隆音色数量
     */
    public int refreshCloneVoices(MimoCloneSampleRepository repository) throws IOException {
        List<MimoCloneSampleRepository.CloneVoice> voices = repository.refresh();
        this.models.entrySet().removeIf(entry -> entry.getKey().startsWith(MimoCloneSampleRepository.CLONE_PREFIX));
        for (MimoCloneSampleRepository.CloneVoice voice : voices) {
            this.models.put(voice.voiceId(), voice.displayName());
        }
        return voices.size();
    }
}
