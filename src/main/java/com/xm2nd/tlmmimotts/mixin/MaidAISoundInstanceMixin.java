package com.xm2nd.tlmmimotts.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.sound.data.MaidAISoundInstance;
import com.xm2nd.tlmmimotts.TlmMimoTts;
import com.xm2nd.tlmmimotts.client.sound.WavAudioStream;
import net.minecraft.Util;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * 客户端专用 Mixin：拦截 TLM {@link MaidAISoundInstance#getStream}。
 * <p>
 * 识别到 RIFF/WAVE 时返回新的 WAV 音频流，其余格式完全交由 TLM 原逻辑处理。
 * <p>
 * 对 TLM（Mojang 映射 mod）的方法注入一律 remap = false。
 */
@Mixin(value = MaidAISoundInstance.class, remap = false)
public abstract class MaidAISoundInstanceMixin {
    @Shadow(remap = false)
    @Final
    private byte[] data;

    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true, remap = false)
    private void tlm_mimo_tts$handleWave(SoundBufferLibrary library, Sound sound, boolean looping,
                                         CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        if (WavAudioStream.isWave(this.data)) {
            cir.setReturnValue(CompletableFuture.supplyAsync(() -> {
                try {
                    return new WavAudioStream(this.data);
                } catch (Exception e) {
                    TlmMimoTts.LOGGER.error("MiMo WAV 音频解码失败", e);
                    return null;
                }
            }, Util.backgroundExecutor()));
        }
    }
}
