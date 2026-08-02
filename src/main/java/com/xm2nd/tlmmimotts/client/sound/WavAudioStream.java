package com.xm2nd.tlmmimotts.client.sound;

import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * WAV 音频流（客户端）：用 Java 标准 {@link AudioSystem} 将 MiMo 返回的 WAV
 * 解码为 16-bit PCM，保持与 TLM MP3/Opus 音频流相同的接口。
 */
public class WavAudioStream implements AudioStream {
    private final AudioInputStream stream;
    private final int frameSize;
    private final byte[] frame;

    public WavAudioStream(byte[] data) throws UnsupportedAudioFileException, IOException {
        ByteArrayInputStream input = new ByteArrayInputStream(data);
        AudioInputStream original = AudioSystem.getAudioInputStream(input);
        AudioFormat originalFormat = original.getFormat();
        AudioFormat targetFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, originalFormat.getSampleRate(), 16,
                originalFormat.getChannels(), originalFormat.getChannels() * 2, originalFormat.getSampleRate(), false);
        this.stream = AudioSystem.getAudioInputStream(targetFormat, original);
        this.frameSize = stream.getFormat().getFrameSize();
        if (frameSize <= 0) {
            stream.close();
            throw new UnsupportedAudioFileException("无法确定 WAV 帧大小");
        }
        this.frame = new byte[frameSize];
    }

    /** 识别 RIFF/WAVE 文件头 */
    public static boolean isWave(byte[] data) {
        if (data == null || data.length < 12) {
            return false;
        }
        return data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'A' && data[10] == 'V' && data[11] == 'E';
    }

    @Override
    public AudioFormat getFormat() {
        return stream.getFormat();
    }

    @Override
    public ByteBuffer read(int size) throws IOException {
        ByteBuffer byteBuffer = BufferUtils.createByteBuffer(size);
        int bytesRead = 0, count;
        do {
            count = this.stream.read(frame);
            if (count != -1) {
                byteBuffer.put(frame);
            }
        } while (count != -1 && (bytesRead += frameSize) < size);
        byteBuffer.flip();
        return byteBuffer;
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
