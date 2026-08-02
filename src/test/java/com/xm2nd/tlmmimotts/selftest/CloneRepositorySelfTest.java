package com.xm2nd.tlmmimotts.selftest;

import com.xm2nd.tlmmimotts.server.MimoCloneSampleRepository;
import com.xm2nd.tlmmimotts.server.MimoCloneSampleRepository.MimoSampleException;

import java.nio.charset.StandardCharsets;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.xm2nd.tlmmimotts.selftest.SelftestUtil.check;
import static com.xm2nd.tlmmimotts.selftest.SelftestUtil.checkEquals;
import static com.xm2nd.tlmmimotts.selftest.SelftestUtil.checkThrows;

/**
 * 文件仓库自检（JDK 临时目录）：目录自动创建、MP3/WAV 发现、稳定排序、
 * 删除后失效、空目录、路径穿越、绝对路径、子目录、符号链接和超限样本拒绝。
 */
public final class CloneRepositorySelfTest {
    private CloneRepositorySelfTest() {
    }

    public static int run() {
        int failures = 0;
        try {
            testAutoCreateAndDiscovery();
            testStableOrderAndDeleteInvalidation();
            testEmptyDir();
            testPathTraversal();
            testAbsolutePathAndSubdir();
            testRejectDirectory();
            testRejectInvalidExtension();
            testOversizedSample();
            testSymlinkRejection();
            testDescriptionSaveAndRead();
            testDescriptionRejects();
            System.out.println("[通过] CloneRepositorySelfTest");
        } catch (AssertionError | Exception e) {
            failures++;
            System.err.println("[失败] CloneRepositorySelfTest: " + e);
            e.printStackTrace();
        }
        return failures;
    }

    /** 目录自动创建 + MP3/WAV 发现 */
    private static void testAutoCreateAndDiscovery() throws Exception {
        Path root = Files.createTempDirectory("mimo-repo-test");
        Path cloneDir = root.resolve("config/touhou_little_maid/mimo-clone");
        MimoCloneSampleRepository repo = new MimoCloneSampleRepository(cloneDir);

        check(!Files.exists(cloneDir), "前置条件：目录尚未创建");
        List<MimoCloneSampleRepository.CloneVoice> voices = repo.refresh();
        check(Files.isDirectory(cloneDir), "refresh 后目录应自动创建");
        check(voices.isEmpty(), "空目录应返回空列表");

        Files.writeString(cloneDir.resolve("b.mp3"), "b");
        Files.writeString(cloneDir.resolve("a.wav"), "a");
        Files.writeString(cloneDir.resolve("c.mp3"), "c");

        voices = repo.refresh();
        checkEquals(3, voices.size(), "应发现 3 个克隆音色");
        checkEquals("clone:a.wav", voices.get(0).voiceId(), "第一个音色应为 a.wav");
        checkEquals("a.wav", voices.get(0).displayName(), "显示名应为完整文件名");
        checkEquals("clone:b.mp3", voices.get(1).voiceId(), "第二个音色应为 b.mp3");
        checkEquals("clone:c.mp3", voices.get(2).voiceId(), "第三个音色应为 c.mp3");
    }

    /** 稳定排序 + 删除后失效 */
    private static void testStableOrderAndDeleteInvalidation() throws Exception {
        Path cloneDir = Files.createTempDirectory("mimo-repo-order");
        MimoCloneSampleRepository repo = new MimoCloneSampleRepository(cloneDir);
        Files.writeString(cloneDir.resolve("z.wav"), "z");
        Files.writeString(cloneDir.resolve("m.mp3"), "m");
        Files.writeString(cloneDir.resolve("a.wav"), "a");

        List<MimoCloneSampleRepository.CloneVoice> first = repo.refresh();
        List<MimoCloneSampleRepository.CloneVoice> second = repo.refresh();
        checkEquals(first, second, "两次扫描顺序应稳定一致（按文件名排序）");
        checkEquals("clone:a.wav", first.get(0).voiceId(), "排序后第一个应为 a.wav");
        checkEquals("clone:m.mp3", first.get(1).voiceId(), "排序后第二个应为 m.mp3");
        checkEquals("clone:z.wav", first.get(2).voiceId(), "排序后第三个应为 z.wav");

        // 删除后：扫描消失 + 读取明确失败（女仆仍选中旧音色的场景）
        Files.delete(cloneDir.resolve("m.mp3"));
        checkEquals(2, repo.refresh().size(), "删除后刷新应只剩 2 个");
        checkThrows(MimoSampleException.class, () -> repo.readSample("clone:m.mp3"),
                "删除后读取应明确失败");
        // 从未存在过的音色同样明确失败
        checkThrows(MimoSampleException.class, () -> repo.readSample("clone:ghost.wav"),
                "不存在的样本应明确失败");
    }

    /** 空目录 */
    private static void testEmptyDir() throws Exception {
        Path cloneDir = Files.createTempDirectory("mimo-repo-empty");
        MimoCloneSampleRepository repo = new MimoCloneSampleRepository(cloneDir);
        check(repo.refresh().isEmpty(), "空目录应返回空列表");
    }

    /** 路径穿越：../ 与 ..\\ */
    private static void testPathTraversal() throws Exception {
        Path cloneDir = Files.createTempDirectory("mimo-repo-traversal");
        MimoCloneSampleRepository repo = new MimoCloneSampleRepository(cloneDir);

        checkThrows(MimoSampleException.class, () -> repo.readSample("clone:../evil.mp3"),
                "正斜杠 ../ 应被拒绝");
        checkThrows(MimoSampleException.class, () -> repo.readSample("clone:..\\evil.mp3"),
                "反斜杠 ..\\ 应被拒绝");
        checkThrows(MimoSampleException.class, () -> repo.readSample("clone:../../x/evil.wav"),
                "多层 ../ 应被拒绝");
        checkThrows(MimoSampleException.class, () -> repo.readSample("clone:.."),
                "裸 .. 应被拒绝");
    }

    /** 绝对路径与子目录 */
    private static void testAbsolutePathAndSubdir() throws Exception {
        Path cloneDir = Files.createTempDirectory("mimo-repo-abs");
        MimoCloneSampleRepository repo = new MimoCloneSampleRepository(cloneDir);

        checkThrows(MimoSampleException.class, () -> repo.readSample("clone:/etc/passwd"),
                "Unix 绝对路径应被拒绝");
        checkThrows(MimoSampleException.class, () -> repo.readSample("clone:C:\\evil\\x.mp3"),
                "Windows 绝对路径应被拒绝");
        checkThrows(MimoSampleException.class, () -> repo.readSample("clone:sub/x.mp3"),
                "子目录（正斜杠）应被拒绝");
        checkThrows(MimoSampleException.class, () -> repo.readSample("clone:sub\\x.mp3"),
                "子目录（反斜杠）应被拒绝");
        checkThrows(MimoSampleException.class, () -> repo.readSample("clone:"),
                "空文件名应被拒绝");
    }

    /** 目录名伪装成样本 */
    private static void testRejectDirectory() throws Exception {
        Path cloneDir = Files.createTempDirectory("mimo-repo-dir");
        MimoCloneSampleRepository repo = new MimoCloneSampleRepository(cloneDir);
        Files.createDirectories(cloneDir.resolve("dir.mp3"));

        check(repo.refresh().isEmpty(), "目录不应被扫描为样本");
        checkThrows(MimoSampleException.class, () -> repo.readSample("clone:dir.mp3"),
                "目录名应被拒绝（不是常规文件）");
    }

    /** 非法扩展名 */
    private static void testRejectInvalidExtension() throws Exception {
        Path cloneDir = Files.createTempDirectory("mimo-repo-ext");
        MimoCloneSampleRepository repo = new MimoCloneSampleRepository(cloneDir);
        Files.writeString(cloneDir.resolve("evil.txt"), "x");
        Files.writeString(cloneDir.resolve("evil.mp4"), "x");
        Files.writeString(cloneDir.resolve("noext"), "x");

        check(repo.refresh().isEmpty(), "非法扩展名不应被扫描");
        checkThrows(MimoSampleException.class, () -> repo.readSample("clone:evil.txt"),
                ".txt 应被拒绝");
        checkThrows(MimoSampleException.class, () -> repo.readSample("clone:evil.mp4"),
                ".mp4 应被拒绝");
        checkThrows(MimoSampleException.class, () -> repo.readSample("clone:noext"),
                "无扩展名应被拒绝");

        // 扩展名大小写不敏感
        Files.writeString(cloneDir.resolve("upper.WAV"), "x");
        checkEquals(1, repo.refresh().size(), "大写扩展名应被接受");
    }

    /** 超限样本 */
    private static void testOversizedSample() throws Exception {
        Path cloneDir = Files.createTempDirectory("mimo-repo-size");
        MimoCloneSampleRepository repo = new MimoCloneSampleRepository(cloneDir);

        byte[] tooBig = new byte[MimoCloneSampleRepository.MAX_SAMPLE_BYTES + 1];
        Files.write(cloneDir.resolve("big.wav"), tooBig);
        check(repo.refresh().isEmpty(), "超限样本不应入列");
        checkThrows(MimoSampleException.class, () -> repo.readSample("clone:big.wav"),
                "超限样本读取应明确失败");

        // 恰好等于上限应允许（边界）
        byte[] atLimit = new byte[MimoCloneSampleRepository.MAX_SAMPLE_BYTES];
        Files.write(cloneDir.resolve("limit.wav"), atLimit);
        checkEquals(1, repo.refresh().size(), "恰好等于上限的样本应被接受");
        checkEquals(MimoCloneSampleRepository.MAX_SAMPLE_BYTES,
                repo.readSample("clone:limit.wav").length, "恰好等于上限应可读取");
    }

    /** 符号链接拒绝（Windows 无权限时跳过并提示） */
    private static void testSymlinkRejection() throws Exception {
        Path cloneDir = Files.createTempDirectory("mimo-repo-link");
        MimoCloneSampleRepository repo = new MimoCloneSampleRepository(cloneDir);

        // 根目录外的真实文件
        Path outside = Files.createTempFile("mimo-outside", ".mp3");
        Files.writeString(outside, "outside-data");

        Path link = cloneDir.resolve("link.mp3");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | SecurityException | java.io.IOException e) {
            System.out.println("[跳过] 当前环境无法创建符号链接（" + e.getClass().getSimpleName()
                    + "），符号链接拒绝用例未执行");
            return;
        }

        // 刷新：符号链接不入列
        check(repo.refresh().isEmpty(), "符号链接不应被扫描为样本");
        // 读取：明确失败
        checkThrows(MimoSampleException.class, () -> repo.readSample("clone:link.mp3"),
                "符号链接样本应被拒绝");
    }

    /** 音色描述：自动建目录、保存/覆盖/读取/清空、readAllDescriptions 与列表一致 */
    private static void testDescriptionSaveAndRead() throws Exception {
        Path cloneDir = Files.createTempDirectory("mimo-repo-desc");
        MimoCloneSampleRepository repo = new MimoCloneSampleRepository(cloneDir);
        Files.writeString(cloneDir.resolve("voice_a.wav"), "a");
        Files.writeString(cloneDir.resolve("voice_b.mp3"), "b");

        Path descDir = cloneDir.resolve(MimoCloneSampleRepository.DESCRIPTION_DIR_NAME);
        check(!Files.exists(descDir), "前置条件：描述目录尚未创建");

        // 保存描述 → 同名 txt 自动创建
        repo.saveDescription("clone:voice_a.wav", "温柔的女声，语速适中");
        check(Files.isDirectory(descDir), "保存描述后描述目录应自动创建");
        check(Files.isRegularFile(descDir.resolve("voice_a.wav.txt")), "应生成与音频同名的 txt");
        checkEquals("温柔的女声，语速适中",
                Files.readString(descDir.resolve("voice_a.wav.txt"), StandardCharsets.UTF_8),
                "txt 内容应与保存一致（UTF-8）");
        checkEquals("温柔的女声，语速适中", repo.readDescription("clone:voice_a.wav"), "readDescription 应一致");

        // 覆盖保存
        repo.saveDescription("clone:voice_a.wav", "改为：活泼的少女音");
        checkEquals("改为：活泼的少女音", repo.readDescription("clone:voice_a.wav"), "覆盖保存应生效");

        // 无描述返回 null
        checkEquals(null, repo.readDescription("clone:voice_b.mp3"), "无描述应返回 null");

        // readAllDescriptions 只包含有描述的克隆音色
        Map<String, String> all = repo.readAllDescriptions();
        checkEquals(1, all.size(), "readAllDescriptions 应只有 1 条");
        checkEquals("改为：活泼的少女音", all.get("clone:voice_a.wav"), "readAllDescriptions 内容应正确");

        // 清空描述 → 删除 txt
        repo.saveDescription("clone:voice_a.wav", "");
        check(!Files.exists(descDir.resolve("voice_a.wav.txt")), "空描述应删除 txt 文件");
        checkEquals(null, repo.readDescription("clone:voice_a.wav"), "清空后读取应返回 null");
    }

    /** 音色描述：超长拒绝、非法文件名/路径穿越拒绝 */
    private static void testDescriptionRejects() throws Exception {
        Path cloneDir = Files.createTempDirectory("mimo-repo-desc-reject");
        MimoCloneSampleRepository repo = new MimoCloneSampleRepository(cloneDir);

        // 超长描述
        String tooLong = "长".repeat(MimoCloneSampleRepository.MAX_DESCRIPTION_CHARS + 1);
        checkThrows(MimoSampleException.class,
                () -> repo.saveDescription("clone:a.wav", tooLong), "超长描述应被拒绝");

        // 非法 voiceId
        checkThrows(MimoSampleException.class,
                () -> repo.saveDescription("preset:冰糖", "x"), "非克隆音色 ID 应被拒绝");
        checkThrows(MimoSampleException.class,
                () -> repo.saveDescription(null, "x"), "null voiceId 应被拒绝");

        // 路径穿越 / 绝对路径 / 子目录 / 非法扩展名
        checkThrows(MimoSampleException.class,
                () -> repo.saveDescription("clone:../evil.wav", "x"), "描述保存应拒绝 ../");
        checkThrows(MimoSampleException.class,
                () -> repo.saveDescription("clone:C:\\evil.wav", "x"), "描述保存应拒绝绝对路径");
        checkThrows(MimoSampleException.class,
                () -> repo.saveDescription("clone:sub/a.wav", "x"), "描述保存应拒绝子目录");
        checkThrows(MimoSampleException.class,
                () -> repo.saveDescription("clone:evil.txt", "x"), "描述保存应拒绝非法扩展名");
        checkThrows(MimoSampleException.class,
                () -> repo.saveDescription("clone:..\\a.wav", "x"), "描述保存应拒绝反斜杠穿越");

        // 读取同样拒绝
        checkThrows(MimoSampleException.class,
                () -> repo.readDescription("clone:../a.wav"), "描述读取应拒绝 ../");
    }

    public static void main(String[] args) {
        System.exit(run());
    }
}
