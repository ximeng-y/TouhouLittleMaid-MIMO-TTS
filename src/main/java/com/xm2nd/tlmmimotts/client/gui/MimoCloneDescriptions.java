package com.xm2nd.tlmmimotts.client.gui;

import com.xm2nd.tlmmimotts.client.gui.layout.TTSMimoFormLayout;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端克隆音色描述缓存与活动编辑页桥接。
 * <p>
 * 描述文本只存于服务端 {@code mimo-clone/descriptions/} 的同名 txt；
 * 客户端仅持有供编辑页展示的临时副本。
 */
public final class MimoCloneDescriptions {
    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();
    /** 最近一次打开的 MiMo 站点编辑页布局（用于同步包即时填入输入框） */
    @Nullable
    private static volatile TTSMimoFormLayout activeLayout;

    private MimoCloneDescriptions() {
    }

    /** 打开编辑页时注册活动布局 */
    public static void setActiveLayout(@Nullable TTSMimoFormLayout layout) {
        activeLayout = layout;
    }

    /** 获取站点描述缓存（无则空表） */
    public static Map<String, String> getCached(String siteId) {
        return CACHE.getOrDefault(siteId, Map.of());
    }

    /** 同步包处理：更新缓存，并把描述即时填入当前打开的编辑页输入框 */
    public static void applyToActiveLayout(String siteId, Map<String, String> descriptions) {
        CACHE.put(siteId, new LinkedHashMap<>(descriptions));
        TTSMimoFormLayout layout = activeLayout;
        if (layout != null && siteId.equals(layout.getSourceSiteId())) {
            layout.applyDescriptions(descriptions);
        }
    }
}
