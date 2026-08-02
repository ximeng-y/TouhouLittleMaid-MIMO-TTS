package com.xm2nd.tlmmimotts.selftest;

import com.xm2nd.tlmmimotts.ai.service.tts.mimo.MimoClientHttpSelfTest;

/**
 * 全部自检入口（无第三方测试依赖，纯 JDK 标准库）。
 * 退出码 0 表示全部通过。
 */
public final class RunAllSelfTests {
    private RunAllSelfTests() {
    }

    public static void main(String[] args) {
        int failures = 0;
        failures += CloneRepositorySelfTest.run();
        failures += WavAudioStreamSelfTest.run();
        failures += MimoClientHttpSelfTest.run();
        failures += TTSMimoSiteSelfTest.run();

        if (failures > 0) {
            System.err.println("========================================");
            System.err.println("自检未通过：共 " + failures + " 个测试类失败");
            System.exit(1);
        }
        System.out.println("========================================");
        System.out.println("全部自检测试通过");
    }
}
