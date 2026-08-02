package com.xm2nd.tlmmimotts.compat;

import com.github.tartaricacid.touhoulittlemaid.ai.service.SerializerRegister;
import com.github.tartaricacid.touhoulittlemaid.ai.service.ServiceType;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.xm2nd.tlmmimotts.ai.service.tts.mimo.TTSMimoSite;
import com.xm2nd.tlmmimotts.ai.service.tts.mimo.TTSMimoSiteSerializer;

/**
 * TLM 扩展开箱点：注册 api_type = "mimo" 的 TTS 站点序列化器。
 * <p>
 * TLM 通过扫描 {@link LittleMaidExtension} 注解自动发现本类并实例化，
 * 实例化时机早于 SerializerRegister.init()，因此注册必然生效。
 */
@LittleMaidExtension
public class LittleMaidCompat implements ILittleMaid {
    public LittleMaidCompat() {
    }

    @Override
    public void registerAIChatSerializer(SerializerRegister register) {
        register.register(ServiceType.TTS, TTSMimoSite.API_TYPE, new TTSMimoSiteSerializer());
    }
}
