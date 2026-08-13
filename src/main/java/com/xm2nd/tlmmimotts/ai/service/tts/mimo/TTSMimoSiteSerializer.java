package com.xm2nd.tlmmimotts.ai.service.tts.mimo;

import com.github.tartaricacid.touhoulittlemaid.ai.service.SerializableSite;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.xm2nd.tlmmimotts.TlmMimoTts;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.github.tartaricacid.touhoulittlemaid.ai.service.Site.*;

/**
 * MiMo 站点序列化器。
 * <p>
 * 持久化字段仅包含：ID、图标、URL、启用状态、API Key、附加请求头和音色元数据；
 * 不保存音频字节、Base64、绝对路径或客户端本地路径。
 */
public class TTSMimoSiteSerializer implements SerializableSite<TTSMimoSite> {
    public static final Codec<TTSMimoSite> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf(ID).forGetter(TTSMimoSite::id),
            ResourceLocation.CODEC.fieldOf(ICON).forGetter(TTSMimoSite::icon),
            Codec.STRING.fieldOf(URL).forGetter(TTSMimoSite::url),
            Codec.BOOL.fieldOf(ENABLED).forGetter(TTSMimoSite::enabled),
            Codec.STRING.fieldOf(SECRET_KEY).forGetter(TTSMimoSite::secretKey),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf(HEADERS).forGetter(TTSMimoSite::headers),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf(MODELS).forGetter(TTSMimoSite::models)
    ).apply(instance, TTSMimoSite::new));

    @Override
    public TTSMimoSite defaultSite() {
        return new TTSMimoSite(TTSMimoSite.API_TYPE,
                new ResourceLocation(TlmMimoTts.MOD_ID, "textures/gui/ai_chat/mimo.png"),
                "https://api.xiaomimimo.com/v1/chat/completions", false, "",
                Map.of(), defaultPresetVoices());
    }

    @Override
    public Codec<TTSMimoSite> codec() {
        return CODEC;
    }

    /**
     * 内置 MiMo 预置音色（显示名 = 音色名称并标注语言）。
     * <p>
     * 不使用 {@code mimo_default}：其输出语言随部署集群而异（非中国集群默认 Mia 英文），
     * 与女仆 AI 设置的语言无关，会造成"设置为中文却生成英语"；
     * 故仅保留语言明确的音色，并按中文优先排列（女仆侧默认选中列表第一个音色）。
     */
    public static Map<String, String> defaultPresetVoices() {
        Map<String, String> voices = new LinkedHashMap<>();
        voices.put("preset:冰糖", "冰糖（中文女）");
        voices.put("preset:茉莉", "茉莉（中文女）");
        voices.put("preset:苏打", "苏打（中文男）");
        voices.put("preset:白桦", "白桦（中文男）");
        voices.put("preset:Mia", "Mia（英文女）");
        voices.put("preset:Chloe", "Chloe（英文女）");
        voices.put("preset:Milo", "Milo（英文男）");
        voices.put("preset:Dean", "Dean（英文男）");
        return voices;
    }
}
