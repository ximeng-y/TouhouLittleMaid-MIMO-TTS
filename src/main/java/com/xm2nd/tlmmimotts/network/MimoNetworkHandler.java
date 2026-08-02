package com.xm2nd.tlmmimotts.network;

import com.xm2nd.tlmmimotts.TlmMimoTts;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络包注册：仅「刷新克隆音色」一个客户端 → 服务端包。
 */
public final class MimoNetworkHandler {
    private MimoNetworkHandler() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(RefreshMimoClonesPacket.TYPE, RefreshMimoClonesPacket.STREAM_CODEC, RefreshMimoClonesPacket::handle);
    }
}
