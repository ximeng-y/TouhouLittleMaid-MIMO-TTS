# TLM MiMo TTS

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

为 [Touhou Little Maid](https://github.com/TartaricAcid/TouhouLittleMaid)（车万女仆）女仆 AI 聊天接入**小米 MiMo 语音合成**（预置音色 + 语音克隆）的附属 Mod（NeoForge 1.21.1 / Forge 1.20.1）。

## 功能

- **预置音色**：内置 MiMo 预置音色（如冰糖等），随模型集群可用音色动态获取
- **语音克隆**：将参考音频放入服务端固定目录即可使用克隆音色朗读；可为每个克隆音色设置描述（风格指令）
- **语言跟随**：女仆未显式指定语言时，朗读语言跟随游戏语言（游戏设为简体中文时语音为中文）
- **MP3 输出**：合成结果以 MP3 传输，规避 MC 网络包大小上限导致的长文本失败
- **权限安全**：站点配置、克隆音色刷新与描述修改仅限单机 / 局域网房主 / OP2 以上；合成请求在服务端发起，API Key 不离开服务端

## 环境要求

| 平台 | Minecraft | 加载器 | Touhou Little Maid | Java |
| --- | --- | --- | --- | --- |
| NeoForge（main 分支） | 1.21.1 | NeoForge 21.1.x | 1.5.3+ | 21 |
| Forge（forge-1.20.1 分支） | 1.20.1 | Forge 47.2.0+ | 1.5.3+ | 17 |

## 安装

1. 按平台安装对应加载器与 Touhou Little Maid 1.5.3+（Forge 版需使用 forge-1.20.1 分支构建的 jar）
2. 下载成品 jar，放入 `mods/` 目录：
    - CurseForge：https://www.curseforge.com/minecraft/mc-mods/touhoulittlemaid-mimo-tts
    - Modrinth：审核中
3. 启动游戏，在女仆 AI 设置中添加 MiMo TTS 站点（URL 与 API Key）

## 使用

### 配置站点

女仆 AI 聊天设置 → TTS 站点 → 添加 / 编辑 MiMo 站点：

- **URL**：MiMo Chat Completions 端点地址
- **API Key**：仅保存在服务端配置文件中，不进入客户端与日志；可前往[小米 MiMo 开放平台](https://platform.xiaomimimo.com/)申请

### 添加克隆音色

1. 将参考音频（`.mp3` / `.wav`，Base64 后不超过 10MB）放入服务端 `config/touhou_little_maid/mimo-clone/`
2. 在站点编辑页点击「刷新克隆音色」
3. 可为每个音色填写描述（风格指令），保存至服务端 `mimo-clone/descriptions/` 同名 txt
4. 在女仆聊天页选择音色即可朗读

## 许可

本项目采用 [MIT](LICENSE) 许可。

---

# TLM MiMo TTS (English)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A mod that integrates **Xiaomi MiMo text-to-speech** (preset voices & voice cloning) into the AI chat of [Touhou Little Maid](https://github.com/TartaricAcid/TouhouLittleMaid) maids (NeoForge 1.21.1 / Forge 1.20.1).

## Features

- **Preset voices**: Built-in MiMo preset voices (e.g. 冰糖), fetched dynamically from the available voices of the model cluster
- **Voice cloning**: Place reference audio into a fixed server directory to use cloned voices; each clone voice can have a description (style instruction)
- **Language following**: When a maid has no explicit language setting, the spoken language follows the game language (e.g. Chinese UI → Chinese voice)
- **MP3 output**: Synthesis results are delivered as MP3, avoiding failures on long texts caused by the Minecraft network packet size limit
- **Permission safety**: Site configuration, clone voice refresh and description edits are limited to singleplayer / LAN host / OP level 2+; synthesis requests are made server-side and the API key never leaves the server

## Requirements

| Platform | Minecraft | Loader | Touhou Little Maid | Java |
| --- | --- | --- | --- | --- |
| NeoForge (main branch) | 1.21.1 | NeoForge 21.1.x | 1.5.3+ | 21 |
| Forge (forge-1.20.1 branch) | 1.20.1 | Forge 47.2.0+ | 1.5.3+ | 17 |

## Installation

1. Install the matching loader and Touhou Little Maid 1.5.3+ for your platform (the Forge version requires the jar built from the forge-1.20.1 branch)
2. Download the release jar and put it into the `mods/` folder:
    - CurseForge: https://www.curseforge.com/minecraft/mc-mods/touhoulittlemaid-mimo-tts
    - Modrinth: under review
3. Launch the game and add a MiMo TTS site in the maid AI settings (URL & API key)

## Usage

### Configure the site

Maid AI chat settings → TTS site → Add / Edit the MiMo site:

- **URL**: MiMo Chat Completions endpoint URL
- **API Key**: Stored only in the server-side config file, never exposed to clients or logs; apply for one on the [Xiaomi MiMo Open Platform](https://platform.xiaomimimo.com/)

### Add clone voices

1. Put reference audio (`.mp3` / `.wav`, no larger than 10MB after Base64) into `config/touhou_little_maid/mimo-clone/` on the server
2. Click "Refresh Clone Voices" on the site editor page
3. Optionally fill in a description (style instruction) for each voice; it is saved as a `.txt` with the same name under `mimo-clone/descriptions/` on the server
4. Select the voice on the maid chat page and it will speak

## License

Licensed under the [MIT](LICENSE) license.
