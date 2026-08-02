package com.xm2nd.tlmmimotts.ai.service.tts.mimo;

import com.github.tartaricacid.touhoulittlemaid.ai.service.tts.TTSConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.xm2nd.tlmmimotts.server.MimoCloneSampleRepository;
import com.xm2nd.tlmmimotts.server.MimoCloneSampleRepository.MimoSampleException;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.xm2nd.tlmmimotts.selftest.SelftestUtil.check;
import static com.xm2nd.tlmmimotts.selftest.SelftestUtil.checkContains;
import static com.xm2nd.tlmmimotts.selftest.SelftestUtil.checkEquals;
import static com.xm2nd.tlmmimotts.selftest.SelftestUtil.checkNotContains;
import static com.xm2nd.tlmmimotts.selftest.SelftestUtil.checkThrows;

/**
 * HTTP Mock 自检（JDK 标准库 com.sun.net.httpserver）：
 * 预置音色请求正确模型与 voiceId；克隆音色请求正确模型、只读取固定目录文件、
 * 携带正确 data URI、且不记录 API Key；响应解析、无效 Base64、4xx/5xx、
 * 超限样本、删除后仍被选中的旧音色均明确失败。
 */
public final class MimoClientHttpSelfTest {
    private static final String API_KEY = "sk-test-secret-123456";
    /** 模拟 MiMo 返回的 WAV 字节 */
    private static final byte[] FAKE_WAV = "RIFFfake-wave-bytes-0123456789".getBytes(StandardCharsets.UTF_8);

    private MimoClientHttpSelfTest() {
    }

    public static int run() {
        int failures = 0;
        try {
            testPresetRequestPath();
            testCloneRequestPath();
            testCloneMp3Mime();
            testCloneDescriptionInRequest();
            testMissingAndDeletedSamples();
            testUnknownVoiceId();
            testOversizedSampleViaClient();
            testFullRoundTripAndRedaction();
            testResponseParsingFailures();
            System.out.println("[通过] MimoClientHttpSelfTest");
        } catch (AssertionError | Exception e) {
            failures++;
            System.err.println("[失败] MimoClientHttpSelfTest: " + e);
            e.printStackTrace();
        }
        return failures;
    }

    /** 预置音色：正确模型 mimo-v2.5-tts 与 voiceId，api-key 头正确且不进请求体 */
    private static void testPresetRequestPath() throws Exception {
        try (MockServer server = MockServer.start(200, okBody(FAKE_WAV))) {
            TTSMimoClient client = newClient(server, tempRepo(new String[0][]));
            HttpRequest request = client.buildRequest("你好", new TTSConfig("preset:冰糖", "zh"));
            JsonObject body = sendAndParse(server, request);

            checkEquals("mimo-v2.5-tts", body.get("model").getAsString(), "预置音色应使用 mimo-v2.5-tts");
            checkEquals("冰糖", body.getAsJsonObject("audio").get("voice").getAsString(), "voice 应为预置 voiceId");
            checkEquals("wav", body.getAsJsonObject("audio").get("format").getAsString(), "格式应为 wav");
            checkEquals(false, body.get("stream").getAsBoolean(), "应使用非流式");
            JsonObject assistant = body.getAsJsonArray("messages").get(0).getAsJsonObject();
            checkEquals("assistant", assistant.get("role").getAsString(), "文本应放在 assistant 消息");
            checkEquals("你好", assistant.get("content").getAsString(), "assistant 内容应为待合成文本");
            checkEquals("/v1/chat/completions", server.lastPath(), "请求路径应为 Chat Completions 端点");
            checkEquals(API_KEY, server.lastApiKey(), "Mock 应收到 api-key 头");
            checkEquals("custom-value", server.lastHeader("X-Custom-Header"), "站点附加请求头应生效");
            checkNotContains(body.toString(), API_KEY, "请求体不应包含 API Key");
        }
    }

    /** 克隆音色：正确模型 mimo-v2.5-tts-voiceclone、只读固定目录、正确 data URI */
    private static void testCloneRequestPath() throws Exception {
        byte[] sample = "FAKE-CLONE-SAMPLE-DATA".getBytes(StandardCharsets.UTF_8);
        try (MockServer server = MockServer.start(200, okBody(FAKE_WAV))) {
            TTSMimoClient client = newClient(server,
                    tempRepo(new String[][]{{"voice_sample.wav", new String(sample, StandardCharsets.UTF_8)}}));
            HttpRequest request = client.buildRequest("hello", new TTSConfig("clone:voice_sample.wav", "en"));
            JsonObject body = sendAndParse(server, request);

            checkEquals("mimo-v2.5-tts-voiceclone", body.get("model").getAsString(), "克隆音色应使用 voiceclone 模型");
            String voice = body.getAsJsonObject("audio").get("voice").getAsString();
            String expectedUri = "data:audio/wav;base64," + Base64.getEncoder().encodeToString(sample);
            checkEquals(expectedUri, voice, "voice 应为固定目录样本的 data URI（只读取固定目录文件）");
            checkNotContains(body.toString(), API_KEY, "请求体不应包含 API Key");

            // 确认 data URI 内容 == 固定目录内文件内容（而非任何其他位置的同名文件）
            checkEquals(Base64.getEncoder().encodeToString(sample),
                    voice.substring(voice.indexOf("base64,") + "base64,".length()),
                    "data URI 内容必须来自固定目录内的文件");
        }
    }

    /** MP3 克隆样本的 MIME */
    private static void testCloneMp3Mime() throws Exception {
        try (MockServer server = MockServer.start(200, okBody(FAKE_WAV))) {
            TTSMimoClient client = newClient(server, tempRepo(new String[][]{{"voice.mp3", "mp3data"}}));
            HttpRequest request = client.buildRequest("x", new TTSConfig("clone:voice.mp3", "zh"));
            String voice = sendAndParse(server, request).getAsJsonObject("audio").get("voice").getAsString();
            check(voice.startsWith("data:audio/mpeg;base64,"), "mp3 样本应使用 audio/mpeg MIME");
        }
    }

    /** 克隆音色描述：有描述 → 作为可选的 user 消息传入；无描述 → 仅 assistant 消息 */
    private static void testCloneDescriptionInRequest() throws Exception {
        Path repoDir = Files.createTempDirectory("mimo-client-desc");
        Files.writeString(repoDir.resolve("voice_a.wav"), "sample-data");
        Path descDir = repoDir.resolve(MimoCloneSampleRepository.DESCRIPTION_DIR_NAME);
        Files.createDirectories(descDir);
        Files.writeString(descDir.resolve("voice_a.wav.txt"), "温柔的女声，语速适中");
        MimoCloneSampleRepository repo = new MimoCloneSampleRepository(repoDir);

        try (MockServer server = MockServer.start(200, okBody(FAKE_WAV))) {
            TTSMimoClient client = newClient(server, repo);

            // 有描述：messages[0] 为 user（音色描述），messages[1] 为 assistant
            HttpRequest withDesc = client.buildRequest("你好", new TTSConfig("clone:voice_a.wav", "zh"));
            JsonObject bodyWithDesc = sendAndParse(server, withDesc);
            checkEquals(2, bodyWithDesc.getAsJsonArray("messages").size(), "有描述时应为 user + assistant 两条消息");
            JsonObject userMsg = bodyWithDesc.getAsJsonArray("messages").get(0).getAsJsonObject();
            checkEquals("user", userMsg.get("role").getAsString(), "第一条消息应为 user");
            checkEquals("温柔的女声，语速适中", userMsg.get("content").getAsString(), "user 消息内容应为音色描述");
            checkEquals("assistant",
                    bodyWithDesc.getAsJsonArray("messages").get(1).getAsJsonObject().get("role").getAsString(),
                    "第二条消息应为 assistant");

            // 无描述（voice_b 没有同名 txt）：仅 assistant 消息
            Files.writeString(repoDir.resolve("voice_b.mp3"), "b");
            HttpRequest withoutDesc = client.buildRequest("hi", new TTSConfig("clone:voice_b.mp3", "en"));
            JsonObject bodyWithoutDesc = sendAndParse(server, withoutDesc);
            checkEquals(1, bodyWithoutDesc.getAsJsonArray("messages").size(), "无描述时应仅 assistant 一条消息");
            checkEquals("assistant",
                    bodyWithoutDesc.getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString(),
                    "仅有的消息应为 assistant");

            // 预置音色不受描述影响：仅 assistant 消息
            HttpRequest preset = client.buildRequest("hi", new TTSConfig("preset:冰糖", "zh"));
            checkEquals(1, sendAndParse(server, preset).getAsJsonArray("messages").size(),
                    "预置音色不应携带描述消息");
        }
    }

    /** 样本缺失与删除后仍被选中的旧音色：明确失败 */
    private static void testMissingAndDeletedSamples() throws Exception {
        try (MockServer server = MockServer.start(200, okBody(FAKE_WAV))) {
            Path repoDir = Files.createTempDirectory("mimo-client-missing");
            Files.writeString(repoDir.resolve("exists.wav"), "data");
            MimoCloneSampleRepository repo = new MimoCloneSampleRepository(repoDir);
            TTSMimoClient client = newClient(server, repo);

            // 从未存在
            checkThrows(MimoSampleException.class,
                    () -> client.buildRequest("x", new TTSConfig("clone:ghost.wav", "zh")),
                    "不存在的样本应明确失败");
            // 刷新后删除（旧音色仍被女仆选中）
            checkEquals(1, repo.refresh().size(), "刷新应发现 1 个样本");
            Files.delete(repoDir.resolve("exists.wav"));
            checkThrows(MimoSampleException.class,
                    () -> client.buildRequest("x", new TTSConfig("clone:exists.wav", "zh")),
                    "删除后仍被选中的旧音色应明确失败");
        }
    }

    /** 未知音色 ID：绝不静默替换 */
    private static void testUnknownVoiceId() throws Exception {
        try (MockServer server = MockServer.start(200, okBody(FAKE_WAV))) {
            TTSMimoClient client = newClient(server, tempRepo(new String[0][]));
            checkThrows(MimoSampleException.class,
                    () -> client.buildRequest("x", new TTSConfig("unknown:whatever", "zh")),
                    "未知音色 ID 应明确失败");
            checkThrows(MimoSampleException.class,
                    () -> client.buildRequest("x", new TTSConfig("", "zh")),
                    "空音色 ID 应明确失败");
            checkThrows(MimoSampleException.class,
                    () -> client.buildRequest("x", new TTSConfig("preset:", "zh")),
                    "空预置音色 ID 应明确失败");
        }
    }

    /** 超限样本：客户端链路明确失败 */
    private static void testOversizedSampleViaClient() throws Exception {
        try (MockServer server = MockServer.start(200, okBody(FAKE_WAV))) {
            byte[] tooBig = new byte[MimoCloneSampleRepository.MAX_SAMPLE_BYTES + 1];
            TTSMimoClient client = newClient(server,
                    tempRepo(new String[][]{{"big.wav", new String(tooBig, StandardCharsets.ISO_8859_1)}}));
            checkThrows(MimoSampleException.class,
                    () -> client.buildRequest("x", new TTSConfig("clone:big.wav", "zh")),
                    "超限样本应明确失败");
        }
    }

    /** 完整请求 → Mock 响应 → 解析；4xx/5xx 与 API Key 脱敏 */
    private static void testFullRoundTripAndRedaction() throws Exception {
        AtomicReference<String> receivedApiKey = new AtomicReference<>();

        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/v1/chat/completions", exchange -> {
            receivedApiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            respond(exchange, 200, okBody(FAKE_WAV));
        });
        httpServer.createContext("/fail500", exchange -> {
            // 模拟服务端把 API Key 回显进错误响应体
            respond(exchange, 500,
                    "{\"error\":\"bad key " + exchange.getRequestHeaders().getFirst("api-key") + "\"}");
        });
        httpServer.createContext("/fail404", exchange -> respond(exchange, 404, "Not Found"));
        httpServer.start();

        try {
            TTSMimoSite site = newSite(httpServer.getAddress().getPort());
            MimoCloneSampleRepository repo = tempRepo(new String[][]{{"s.wav", "sample-data"}});
            TTSMimoClient client = new TTSMimoClient(HttpClient.newHttpClient(), site, repo);

            // 预置音色完整链路：请求 → 2xx → 解析出 WAV 字节
            HttpRequest presetRequest = client.buildRequest("你好", new TTSConfig("preset:冰糖", "zh"));
            HttpResponse<byte[]> presetResponse = HttpClient.newHttpClient()
                    .send(presetRequest, HttpResponse.BodyHandlers.ofByteArray());
            checkEquals(200, presetResponse.statusCode(), "预置音色请求应成功");
            checkEquals(API_KEY, receivedApiKey.get(), "Mock 应收到 api-key 头");
            checkEquals(new String(FAKE_WAV, StandardCharsets.UTF_8),
                    new String(TTSMimoClient.processResponse(presetResponse.statusCode(), presetResponse.body(), API_KEY),
                            StandardCharsets.UTF_8),
                    "解析出的 WAV 字节应与 Mock 返回一致");

            // 5xx + 响应体回显密钥 → 诊断必须脱敏
            HttpRequest failRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + httpServer.getAddress().getPort() + "/fail500"))
                    .header("api-key", API_KEY)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<byte[]> failResponse = HttpClient.newHttpClient()
                    .send(failRequest, HttpResponse.BodyHandlers.ofByteArray());
            checkEquals(500, failResponse.statusCode(), "Mock 应返回 500");
            String diagnostic = failMessage(failResponse, API_KEY);
            checkContains(diagnostic, "HTTP Error Code: 500", "5xx 诊断应包含状态码");
            checkNotContains(diagnostic, API_KEY, "诊断信息必须不包含 API Key");
            checkContains(diagnostic, "***", "诊断信息中密钥应被脱敏");

            // 4xx
            HttpRequest notFound = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + httpServer.getAddress().getPort() + "/fail404"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<byte[]> notFoundResponse = HttpClient.newHttpClient()
                    .send(notFound, HttpResponse.BodyHandlers.ofByteArray());
            checkContains(failMessage(notFoundResponse, API_KEY), "HTTP Error Code: 404", "4xx 诊断应包含状态码");
        } finally {
            httpServer.stop(0);
        }
    }

    /** 响应解析失败：字段缺失、无效 Base64、非法 JSON；合法 Base64 应成功 */
    private static void testResponseParsingFailures() throws Exception {
        checkThrows(TTSMimoClient.MimoResponseException.class,
                () -> TTSMimoClient.parseWavResponse("{}".getBytes(StandardCharsets.UTF_8)), "缺少 choices 应失败");
        checkThrows(TTSMimoClient.MimoResponseException.class,
                () -> TTSMimoClient.parseWavResponse("{\"choices\":[]}".getBytes(StandardCharsets.UTF_8)), "空 choices 应失败");
        checkThrows(TTSMimoClient.MimoResponseException.class,
                () -> TTSMimoClient.parseWavResponse("{\"choices\":[{}]}".getBytes(StandardCharsets.UTF_8)), "缺少 message 应失败");
        checkThrows(TTSMimoClient.MimoResponseException.class,
                () -> TTSMimoClient.parseWavResponse("{\"choices\":[{\"message\":{}}]}".getBytes(StandardCharsets.UTF_8)), "缺少 audio 应失败");
        checkThrows(TTSMimoClient.MimoResponseException.class,
                () -> TTSMimoClient.parseWavResponse("{\"choices\":[{\"message\":{\"audio\":{}}}]}".getBytes(StandardCharsets.UTF_8)), "缺少 data 应失败");
        checkThrows(TTSMimoClient.MimoResponseException.class,
                () -> TTSMimoClient.parseWavResponse("{\"choices\":[{\"message\":{\"audio\":{\"data\":\"!!!not-base64!!!\"}}}]}"
                        .getBytes(StandardCharsets.UTF_8)), "无效 Base64 应失败");
        checkThrows(TTSMimoClient.MimoResponseException.class,
                () -> TTSMimoClient.parseWavResponse("not json".getBytes(StandardCharsets.UTF_8)), "非法 JSON 应失败");

        byte[] parsed = TTSMimoClient.parseWavResponse(
                ("{\"choices\":[{\"message\":{\"audio\":{\"data\":\"" + Base64.getEncoder().encodeToString(FAKE_WAV) + "\"}}}]}")
                        .getBytes(StandardCharsets.UTF_8));
        checkEquals(new String(FAKE_WAV, StandardCharsets.UTF_8), new String(parsed, StandardCharsets.UTF_8),
                "合法响应应解析出 WAV 字节");
    }

    // ---------- 工具 ----------

    private static TTSMimoClient newClient(MockServer server, MimoCloneSampleRepository repo) {
        TTSMimoSite site = newSite(server.port);
        return new TTSMimoClient(HttpClient.newHttpClient(), site, repo);
    }

    private static TTSMimoSite newSite(int port) {
        return new TTSMimoSite("mimo",
                ResourceLocation.fromNamespaceAndPath("tlm_mimo_tts", "textures/gui/ai_chat/mimo.png"),
                "http://127.0.0.1:" + port + "/v1/chat/completions", true, API_KEY,
                Map.of("X-Custom-Header", "custom-value"),
                TTSMimoSiteSerializer.defaultPresetVoices());
    }

    private static MimoCloneSampleRepository tempRepo(String[][] files) throws IOException {
        Path dir = Files.createTempDirectory("mimo-client-repo");
        for (String[] file : files) {
            Files.writeString(dir.resolve(file[0]), file[1]);
        }
        return new MimoCloneSampleRepository(dir);
    }

    /** 通过 Mock 服务器发送请求并返回解析后的请求体 JSON */
    private static JsonObject sendAndParse(MockServer server, HttpRequest request) throws Exception {
        HttpResponse<byte[]> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofByteArray());
        checkEquals(200, response.statusCode(), "Mock 请求应成功");
        return JsonParser.parseString(server.lastBody()).getAsJsonObject();
    }

    private static String failMessage(HttpResponse<byte[]> response, String key) throws Exception {
        try {
            TTSMimoClient.processResponse(response.statusCode(), response.body(), key);
        } catch (TTSMimoClient.MimoResponseException e) {
            return e.getMessage();
        }
        throw new AssertionError("应抛出 MimoResponseException");
    }

    private static String okBody(byte[] wav) {
        return "{\"choices\":[{\"message\":{\"audio\":{\"data\":\"" + Base64.getEncoder().encodeToString(wav) + "\"}}}]}";
    }

    /** 简易 Mock 服务器：记录请求体与请求头并统一响应 */
    private static final class MockServer implements AutoCloseable {
        private final HttpServer server;
        private final int port;
        private final AtomicReference<String> lastBody = new AtomicReference<>();
        private final AtomicReference<String> lastPath = new AtomicReference<>();
        private final AtomicReference<String> lastApiKey = new AtomicReference<>();
        private final AtomicReference<String> lastCustomHeader = new AtomicReference<>();

        private MockServer(HttpServer server) {
            this.server = server;
            this.port = server.getAddress().getPort();
        }

        static MockServer start(int status, String body) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            MockServer mock = new MockServer(server);
            server.createContext("/", exchange -> {
                mock.lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                mock.lastPath.set(exchange.getRequestURI().getPath());
                mock.lastApiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
                mock.lastCustomHeader.set(exchange.getRequestHeaders().getFirst("X-Custom-Header"));
                respond(exchange, status, body);
            });
            server.start();
            return mock;
        }

        String lastBody() {
            return lastBody.get();
        }

        String lastPath() {
            return lastPath.get();
        }

        String lastApiKey() {
            return lastApiKey.get();
        }

        String lastHeader(String name) {
            return "X-Custom-Header".equals(name) ? lastCustomHeader.get() : null;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    public static void main(String[] args) {
        System.exit(run());
    }
}
