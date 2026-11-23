# 岚播放器 (Arashi Player)

- **应用名称**：岚播放器
- **包名 (applicationId)**：`com.arashiplayer`（Android 规范要求包名至少包含一个点号分隔符）
- **技术栈**：Kotlin + Jetpack Compose + Material 3 + AndroidX Media3 (ExoPlayer) + Coil + OkHttp

## 核心功能模块
1. **现代苹果风 UI (`ui/screens/HomeScreen.kt`, `music/MusicPlayerScreen.kt`)**：深邃暗夜磨砂玻璃质感、底部悬浮 Mini 音乐播放条、Apple Music 风格全屏播放页。
2. **全格式本地视频播放与外挂字幕 (`video/VideoPlayerScreen.kt`)**：基于 Media3 ExoPlayer，支持左右手势亮度/音量调节、倍速切换、画面比例切换、锁屏防误触，以及 `.srt` / `.ass` / `.vtt` 外挂字幕加载与实时调整**颜色、文字/背景透明度、垂直位置、字体大小**。
3. **在线封面与歌词匹配 (`music/OnlineTagMatcher.kt`, `music/LrcParser.kt`)**：参考「音乐标签」，通过 iTunes Search API 获取高清封面、通过 LRCLIB 获取时间轴同步 LRC 歌词。
4. **零空间占用私密加密 (`vault/PrivacyVaultManager.kt`, `ui/screens/VaultScreen.kt`)**：参考 QQ 影音，支持视频、音频、图片原地重命名修改后缀为 `.arashi_enc` 并写入 `.nomedia` + 移除 MediaStore 索引，不占用额外存储空间且不被系统相册或其他软件扫描，支持应用内直接播放与一键还原。
5. **自适应矢量图标 (`res/drawable/ic_launcher_foreground.xml`)**：左上角视频播放三角、右上角音符、中央「岚」风漩涡。

## 如何一键打包生成 APK
- **方式一（Android Studio 本地打包）**：解压后使用 Android Studio Ladybug+ 打开 `ArashiPlayer` 目录，等待 Gradle 同步完成后点击 `Build -> Build Bundle(s) / APK(s) -> Build APK(s)`。
- **方式二（免装环境 · GitHub Actions 云端自动打包）**：将本项目上传至你的 GitHub 仓库，仓库内已内置 `.github/workflows/build-apk.yml`，推送后在 GitHub `Actions` 页面即可直接下载编译好的 `ArashiPlayer-debug-apk`。
