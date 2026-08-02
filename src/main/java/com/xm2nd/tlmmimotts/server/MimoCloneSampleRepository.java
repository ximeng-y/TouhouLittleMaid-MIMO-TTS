package com.xm2nd.tlmmimotts.server;

import com.github.tartaricacid.touhoulittlemaid.TouhouLittleMaid;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * MiMo 语音克隆参考音频仓库（服务端）。
 * <p>
 * 唯一允许读取的根目录固定为 {@code config/touhou_little_maid/mimo-clone/}：
 * 仅扫描目录根层、仅接受常规且非符号链接的 .mp3/.wav 文件，按稳定顺序生成克隆音色；
 * 不递归扫描子目录。任何 {@code ../}、绝对路径、目录名、符号链接或超限样本均明确失败。
 * 克隆样本永不出现在站点配置中，也永远不会被客户端读取。
 */
public final class MimoCloneSampleRepository {
    /** MiMo 要求样本 Base64 字符串不超过 10MB，对应原始字节上限 */
    public static final int MAX_SAMPLE_BYTES = 10 * 1024 * 1024 * 3 / 4;
    public static final String CLONE_PREFIX = "clone:";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp3", "wav");

    private final Path root;

    public MimoCloneSampleRepository(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** 服务端默认仓库：config/touhou_little_maid/mimo-clone/ */
    public static MimoCloneSampleRepository defaultInstance() {
        return new MimoCloneSampleRepository(
                FMLPaths.CONFIGDIR.get().resolve(TouhouLittleMaid.MOD_ID).resolve("mimo-clone"));
    }

    public Path getRoot() {
        return root;
    }

    /** 首次加载与刷新时自动创建目录 */
    public Path ensureDirectory() throws IOException {
        Files.createDirectories(root);
        return root;
    }

    /**
     * 扫描根层（不递归），按文件名稳定排序生成克隆音色。
     * 仅接受常规且非符号链接的 .mp3/.wav；超限样本不入列。
     */
    public List<CloneVoice> refresh() throws IOException {
        Path dir = ensureDirectory();
        List<CloneVoice> voices = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> isAllowedSampleName(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> {
                        try {
                            long size = Files.size(path);
                            if (size > 0 && size <= MAX_SAMPLE_BYTES) {
                                String fileName = path.getFileName().toString();
                                voices.add(new CloneVoice(CLONE_PREFIX + fileName, fileName));
                            }
                        } catch (IOException ignored) {
                            // 扫描期间文件被删除等竞态，跳过即可
                        }
                    });
        }
        return List.copyOf(voices);
    }

    /**
     * 合成前读取克隆样本：完整安全校验后返回文件字节。
     * 任何样本缺失、类型不合法、大小超限、路径越界均抛出明确异常，绝不静默替换。
     */
    public byte[] readSample(String voiceId) throws MimoSampleException, IOException {
        if (voiceId == null || !voiceId.startsWith(CLONE_PREFIX)) {
            throw new MimoSampleException("不是克隆音色 ID: " + voiceId);
        }
        String fileName = voiceId.substring(CLONE_PREFIX.length());
        Path file = validateAndResolve(fileName);
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > MAX_SAMPLE_BYTES) {
            throw new MimoSampleException(
                    "克隆音色样本大小超限（最大 %d 字节）: %s".formatted(MAX_SAMPLE_BYTES, fileName));
        }
        return bytes;
    }

    /**
     * 安全校验：文件名为单一文件名、无路径分隔符、规范化后仍位于根目录、
     * 真实路径仍位于根目录、拒绝符号链接、必须为常规 .mp3/.wav 且大小未超限。
     */
    private Path validateAndResolve(String fileName) throws MimoSampleException, IOException {
        if (fileName == null || fileName.isEmpty()) {
            throw new MimoSampleException("克隆音色文件名不能为空");
        }
        if (fileName.contains("/") || fileName.contains("\\")) {
            throw new MimoSampleException("克隆音色文件名包含路径分隔符: " + fileName);
        }
        if (fileName.contains("..")) {
            throw new MimoSampleException("克隆音色文件名不允许包含 '..': " + fileName);
        }
        if (!isAllowedSampleName(fileName)) {
            throw new MimoSampleException("克隆音色仅支持 .mp3/.wav 文件: " + fileName);
        }

        Path dir = ensureDirectory();
        Path candidate = dir.resolve(fileName).normalize();
        // 规范化后仍位于根目录
        if (!candidate.startsWith(dir)) {
            throw new MimoSampleException("克隆音色路径越界: " + fileName);
        }
        if (Files.isSymbolicLink(candidate)) {
            throw new MimoSampleException("不允许读取符号链接样本: " + fileName);
        }
        // 真实路径（解析符号链接后）仍位于根目录
        Path realDir = dir.toRealPath();
        Path realFile;
        try {
            realFile = candidate.toRealPath();
        } catch (IOException e) {
            // 文件不存在或无法访问（如已被删除的旧音色）
            throw new MimoSampleException("克隆音色文件不存在或无法访问: " + fileName);
        }
        if (!realFile.startsWith(realDir)) {
            throw new MimoSampleException("克隆音色真实路径越界（可能为符号链接）: " + fileName);
        }
        if (!Files.isRegularFile(realFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new MimoSampleException("克隆音色不是常规文件: " + fileName);
        }
        long size = Files.size(realFile);
        if (size <= 0 || size > MAX_SAMPLE_BYTES) {
            throw new MimoSampleException(
                    "克隆音色样本大小超限（最大 %d 字节）: %s".formatted(MAX_SAMPLE_BYTES, fileName));
        }
        return realFile;
    }

    private static boolean isAllowedSampleName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return false;
        }
        return ALLOWED_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    public static boolean isMp3(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".mp3");
    }

    /** 克隆音色条目：voiceId = clone:&lt;filename&gt;，显示名 = 完整文件名 */
    public record CloneVoice(String voiceId, String displayName) {
    }

    /** 样本校验/读取失败；诊断信息不含 API Key 等敏感内容 */
    public static class MimoSampleException extends Exception {
        public MimoSampleException(String message) {
            super(message);
        }

        public MimoSampleException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
