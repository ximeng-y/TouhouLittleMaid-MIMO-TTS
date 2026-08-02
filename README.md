# TLM MiMo TTS

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

为 [Touhou Little Maid](https://github.com/TartaricAcid/TouhouLittleMaid)（车万女仆）女仆 AI 聊天接入**小米 MiMo 语音合成**（预置音色 + 语音克隆）的 NeoForge 附属 Mod。

## 功能

- **预置音色**：内置 MiMo 预置音色（如冰糖等），随模型集群可用音色动态获取
- **语音克隆**：将参考音频放入服务端固定目录即可使用克隆音色朗读；可为每个克隆音色设置描述（风格指令）
- **语言跟随**：女仆未显式指定语言时，朗读语言跟随游戏语言（游戏设为简体中文时语音为中文）
- **MP3 输出**：合成结果以 MP3 传输，规避 MC 网络包大小上限导致的长文本失败
- **权限安全**：站点配置、克隆音色刷新与描述修改仅限单机 / 局域网房主 / OP2 以上；合成请求在服务端发起，API Key 不离开服务端

## 环境要求

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x |
| Touhou Little Maid | 1.5.3 – 2.0.0（不含） |
| Java | 21 |

## 安装

1. 安装 NeoForge 21.1.x 与 Touhou Little Maid 1.5.3
2. 从 [Releases](../../releases) 下载成品 jar，放入 `mods/` 目录
3. 启动游戏，在女仆 AI 设置中添加 MiMo TTS 站点（URL 与 API Key）

## 使用

### 配置站点

女仆 AI 聊天设置 → TTS 站点 → 添加 / 编辑 MiMo 站点：

- **URL**：MiMo Chat Completions 端点地址
- **API Key**：仅保存在服务端配置文件中，不进入客户端与日志

### 添加克隆音色

1. 将参考音频（`.mp3` / `.wav`，Base64 后不超过 10MB）放入服务端 `config/touhou_little_maid/mimo-clone/`
2. 在站点编辑页点击「刷新克隆音色」
3. 可为每个音色填写描述（风格指令），保存至服务端 `mimo-clone/descriptions/` 同名 txt
4. 在女仆聊天页选择音色即可朗读

## 许可

本项目采用 [MIT](LICENSE) 许可。
