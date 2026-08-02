package com.xm2nd.tlmmimotts.selftest;

import com.xm2nd.tlmmimotts.client.sound.WavAudioStream;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static com.xm2nd.tlmmimotts.selftest.SelftestUtil.check;
import static com.xm2nd.tlmmimotts.selftest.SelftestUtil.checkEquals;
import static com.xm2nd.tlmmimotts.selftest.SelftestUtil.checkThrows;

/**
 * WAV 音频流自检：RIFF/WAVE 识别、16-bit PCM 解码、浮点 WAV 转 16-bit PCM。
 */
public final class WavAudioStreamSelfTest {
    private WavAudioStreamSelfTest() {
    }

    public static int run() {
        int failures = 0;
        try {
            testIsWaveDetection();
            testDecodePcm16();
            testDecodeFloat32Stereo();
            testRejectMalformed();
            System.out.println("[通过] WavAudioStreamSelfTest");
        } catch (AssertionError | Exception e) {
            failures++;
            System.err.println("[失败] WavAudioStreamSelfTest: " + e);
            e.printStackTrace();
        }
        return failures;
    }

    /** RIFF/WAVE 识别 */
    private static void testIsWaveDetection() throws Exception {
        byte[] pcm = createPcm16Wav(8000, 1, new short[]{100, 200, 300});
        byte[] flt = createFloat32Wav(44100, 2, new float[]{0.5f, -0.5f, 0.25f});

        check(WavAudioStream.isWave(pcm), "16-bit PCM WAV 应被识别");
        check(WavAudioStream.isWave(flt), "浮点 WAV 应被识别");
        check(!WavAudioStream.isWave(null), "null 不应被识别");
        check(!WavAudioStream.isWave(new byte[0]), "空数组不应被识别");
        check(!WavAudioStream.isWave("RIFFxxxxWAV".getBytes(StandardCharsets.US_ASCII)),
                "短于 12 字节不应被识别");
        check(!WavAudioStream.isWave("RIFF".getBytes(StandardCharsets.US_ASCII)),
                "仅 RIFF 头不应被识别");

        // 头部残缺：RIFF 但不是 WAVE
        byte[] notWave = "RIFF????????WXYZ".getBytes(StandardCharsets.US_ASCII);
        check(!WavAudioStream.isWave(notWave), "RIFF 但非 WAVE 不应被识别");

        // 随机字节不应被误判
        Random random = new Random(42);
        byte[] randomBytes = new byte[256];
        random.nextBytes(randomBytes);
        check(!WavAudioStream.isWave(randomBytes), "随机字节不应被识别");
    }

    /** 16-bit PCM WAV 解码 */
    private static void testDecodePcm16() throws Exception {
        short[] samples = new short[800];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) (i * 10);
        }
        byte[] wav = createPcm16Wav(8000, 1, samples);

        try (WavAudioStream stream = new WavAudioStream(wav)) {
            AudioFormat format = stream.getFormat();
            checkEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding(), "应为 PCM_SIGNED");
            checkEquals(16, format.getSampleSizeInBits(), "应为 16-bit");
            checkEquals(8000.0f, format.getSampleRate(), "采样率应为 8000");
            checkEquals(1, format.getChannels(), "应为单声道");

            ByteBuffer buffer = stream.read(4096);
            check(buffer.limit() > 0, "read 应返回数据");
            short first = buffer.order(ByteOrder.LITTLE_ENDIAN).getShort(0);
            checkEquals(samples[0], first, "第一个采样值应一致");
        }
    }

    /** 浮点 32-bit 立体声 WAV → 转 16-bit PCM */
    private static void testDecodeFloat32Stereo() throws Exception {
        float[] samples = new float[200];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (float) Math.sin(i / 10.0);
        }
        byte[] wav = createFloat32Wav(44100, 2, samples);

        try (WavAudioStream stream = new WavAudioStream(wav)) {
            AudioFormat format = stream.getFormat();
            checkEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding(), "浮点 WAV 应转为 PCM_SIGNED");
            checkEquals(16, format.getSampleSizeInBits(), "应转为 16-bit");
            checkEquals(44100.0f, format.getSampleRate(), "采样率应保持 44100");
            checkEquals(2, format.getChannels(), "应保持双声道");

            ByteBuffer buffer = stream.read(8192);
            check(buffer.limit() > 0, "read 应返回数据");
        }
    }

    /** 畸形 WAV 拒绝 */
    private static void testRejectMalformed() throws Exception {
        // 有 RIFF/WAVE 头但没有任何 chunk
        byte[] malformed = "RIFF0000WAVE".getBytes(StandardCharsets.US_ASCII);
        checkThrows(javax.sound.sampled.UnsupportedAudioFileException.class,
                () -> new WavAudioStream(malformed), "畸形 WAV 应抛出 UnsupportedAudioFileException");
    }

    /** 手工构造 16-bit PCM WAV */
    static byte[] createPcm16Wav(int sampleRate, int channels, short[] samples) {
        int dataLen = samples.length * 2;
        ByteBuffer buf = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(36 + dataLen);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(16);
        buf.putShort((short) 1); // PCM
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(sampleRate * channels * 2);
        buf.putShort((short) (channels * 2));
        buf.putShort((short) 16);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(dataLen);
        for (short s : samples) {
            buf.putShort(s);
        }
        return buf.array();
    }

    /** 手工构造 32-bit 浮点 WAV（format code 3） */
    static byte[] createFloat32Wav(int sampleRate, int channels, float[] samples) {
        int dataLen = samples.length * 4;
        ByteBuffer buf = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(36 + dataLen);
        buf.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        buf.put("fmt ".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(16);
        buf.putShort((short) 3); // IEEE float
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(sampleRate * channels * 4);
        buf.putShort((short) (channels * 4));
        buf.putShort((short) 32);
        buf.put("data".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(dataLen);
        for (float s : samples) {
            buf.putFloat(s);
        }
        return buf.array();
    }

    public static void main(String[] args) {
        System.exit(run());
    }
}
