package com.xm2nd.tlmmimotts;

import com.xm2nd.tlmmimotts.network.MimoNetworkHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * TLM MiMo TTS：为 Touhou Little Maid 女仆 AI 聊天接入小米 MiMo 语音合成
 * （预置音色与语音克隆）的附属 Mod。客户端与服务端均需安装。
 */
@Mod(TlmMimoTts.MOD_ID)
public class TlmMimoTts {
    public static final String MOD_ID = "tlm_mimo_tts";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public TlmMimoTts(IEventBus modEventBus) {
        modEventBus.addListener(MimoNetworkHandler::registerPayloads);
    }
}
