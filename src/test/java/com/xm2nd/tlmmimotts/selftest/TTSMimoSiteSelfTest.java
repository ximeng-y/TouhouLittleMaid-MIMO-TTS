package com.xm2nd.tlmmimotts.selftest;

import com.xm2nd.tlmmimotts.ai.service.tts.mimo.TTSMimoSite;
import com.xm2nd.tlmmimotts.ai.service.tts.mimo.TTSMimoSiteSerializer;
import com.xm2nd.tlmmimotts.server.MimoCloneSampleRepository;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.xm2nd.tlmmimotts.selftest.SelftestUtil.check;
import static com.xm2nd.tlmmimotts.selftest.SelftestUtil.checkEquals;

/**
 * 站点级克隆音色刷新自检：保留预置音色、清除旧克隆条目、写入固定目录扫描结果
 * （仅文件名元数据，不含音频字节/Base64/路径）。
 */
public final class TTSMimoSiteSelfTest {
    private TTSMimoSiteSelfTest() {
    }

    public static int run() {
        int failures = 0;
        try {
            testRefreshCloneVoices();
            testNoAudioBytesPersisted();
            System.out.println("[通过] TTSMimoSiteSelfTest");
        } catch (AssertionError | Exception e) {
            failures++;
            System.err.println("[失败] TTSMimoSiteSelfTest: " + e);
            e.printStackTrace();
        }
        return failures;
    }

    /** 刷新：预置音色保留、旧克隆条目清除、新克隆条目按扫描结果写入 */
    private static void testRefreshCloneVoices() throws Exception {
        Path cloneDir = Files.createTempDirectory("mimo-site-repo");
        MimoCloneSampleRepository repo = new MimoCloneSampleRepository(cloneDir);

        // 预置音色 + 一个已失效的旧克隆条目
        Map<String, String> models = new LinkedHashMap<>();
        models.putAll(TTSMimoSiteSerializer.defaultPresetVoices());
        models.put("clone:stale.mp3", "stale.mp3");

        TTSMimoSite site = new TTSMimoSite("mimo",
                ResourceLocation.fromNamespaceAndPath("tlm_mimo_tts", "textures/gui/ai_chat/mimo.png"),
                "https://api.xiaomimimo.com/v1/chat/completions", true, "sk-x",
                Map.of(), models);

        int presetCount = TTSMimoSiteSerializer.defaultPresetVoices().size();
        checkEquals(presetCount + 1, site.models().size(), "前置条件：预置 + 1 个旧克隆");

        // 目录里有两个真实样本
        Files.writeString(cloneDir.resolve("voice_a.wav"), "a");
        Files.writeString(cloneDir.resolve("voice_b.mp3"), "b");

        int count = site.refreshCloneVoices(repo);
        checkEquals(2, count, "刷新应返回 2 个克隆音色");
        checkEquals(presetCount + 2, site.models().size(), "刷新后应为 预置 + 2 个克隆");

        check(site.models().containsKey("preset:冰糖"), "预置音色应保留");
        checkEquals("冰糖（中文女）", site.models().get("preset:冰糖"), "预置音色显示名应保留");
        check(!site.models().containsKey("clone:stale.mp3"), "失效的旧克隆条目应被清除");
        checkEquals("voice_a.wav", site.models().get("clone:voice_a.wav"), "克隆条目显示名应为完整文件名");
        checkEquals("voice_b.mp3", site.models().get("clone:voice_b.mp3"), "克隆条目显示名应为完整文件名");
    }

    /** 站点模型中只允许存在音色元数据，不得出现音频字节/Base64/路径 */
    private static void testNoAudioBytesPersisted() throws Exception {
        Path cloneDir = Files.createTempDirectory("mimo-site-repo2");
        Files.writeString(cloneDir.resolve("v.wav"), "SAMPLE-DATA");
        MimoCloneSampleRepository repo = new MimoCloneSampleRepository(cloneDir);

        TTSMimoSite site = new TTSMimoSite("mimo",
                ResourceLocation.fromNamespaceAndPath("tlm_mimo_tts", "textures/gui/ai_chat/mimo.png"),
                "https://api.xiaomimimo.com/v1/chat/completions", true, "sk-x",
                Map.of(), TTSMimoSiteSerializer.defaultPresetVoices());
        site.refreshCloneVoices(repo);

        for (String id : site.models().keySet()) {
            check(id.startsWith("preset:") || id.startsWith("clone:"), "音色 ID 必须带 preset:/clone: 前缀: " + id);
            check(!id.contains("data:") && !id.contains("base64"), "音色 ID 不得包含音频数据: " + id);
            check(!id.contains("/") && !id.contains("\\"), "音色 ID 不得包含路径: " + id);
        }
    }

    public static void main(String[] args) {
        System.exit(run());
    }
}
