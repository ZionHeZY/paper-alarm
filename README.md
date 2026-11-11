# Paper Alarm

Android 13+ 的离线闹钟与生日提醒应用，使用纸张风格界面。包名：`tech.hezy.alarm`。

## 功能

- 一次性或按星期重复的闹钟；标签、五种铃声与可调整时长的稍后提醒。
- 公历、农历生日及每年响铃提醒；生日卡片和列表，可选记录出生年份。
- 从系统通讯录读取生日，也可将生日写回已有联系人或新建联系人。农历使用「农历生日」自定义事件；写入会替换同一历法的原记录。
- 锁屏/全屏响铃、通知操作；设备重启或时区变化后重新安排提醒。

农历闰月在当年不存在时使用对应平月；大月三十遇到小月时使用月末。系统限制、强行停止应用或未开放必要权限可能影响响铃，请在自己的设备上试响。

## 隐私与权限

应用不申请网络权限，无广告、分析或遥测 SDK，不需要账户。闹钟数据保存在应用私有目录，系统备份已关闭；卸载会删除这些数据。通讯录读写通过 Android 系统接口完成；**联系人所属账户的系统同步服务仍可能同步写入的生日**，不受本应用控制。

| 权限/系统设置 | 用途 |
| --- | --- |
| 通讯录读取、写入 | 用户选择读取或写回生日时使用；不使用通讯录功能可拒绝 |
| 精确闹钟、通知、全屏提醒 | 安排准点闹钟，展示响铃通知与锁屏提醒 |
| 显示在其他应用上层 | 在其他应用上显示响铃界面 |
| 前台服务、唤醒锁、振动 | 响铃期间保持运行与提示 |
| 开机广播、电池优化豁免/厂商自启动设置 | 重启后恢复调度，减少后台限制 |

## 构建与安装

准备 JDK 17、Android SDK Platform 35、Build Tools 34.0.0、Platform Tools，并设置 `ANDROID_HOME`（或本机 `local.properties` 的 `sdk.dir`）。仓库使用 Gradle Wrapper 8.9；首次构建需联网下载工具与依赖，应用运行不联网。

```sh
sdkmanager 'platforms;android-35' 'build-tools;34.0.0' 'platform-tools'
bash ./gradlew :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

也可在 [Actions](https://github.com/ZionHeZY/paper-alarm/actions) 成功的 Build 运行中下载 debug APK artifact，解压后安装。它使用调试签名，不应作为正式发布版；不同签名之间不能直接覆盖安装。

## 正式发布

[Releases](https://github.com/ZionHeZY/paper-alarm/releases) 提供发布成功后的签名 APK 和 `SHA256SUMS`。下载到同一目录后运行 `sha256sum --check SHA256SUMS` 校验完整性；校验和不是独立的身份认证。

1. 维护者建立 `release` environment，仅允许 `v*` 标签部署；用仓库规则限制发布标签的创建、修改和删除只能由维护者执行，并要求 `main` 经 PR 审查。
2. 在该 environment 配置 `RELEASE_KEYSTORE_BASE64`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD` secrets。签名密钥须离线备份，不能提交到仓库。
3. 合并审查通过的代码，递增版本名/版本号和两种语言的 changelog，确认 Build 成功；首个版本为 `1.2.0`（code `3`），创建并推送 `v1.2.0` 标签。
4. Release 自动检查标签是否属于 `main` 历史，构建并验证签名，发布 APK 和校验和。PR/普通构建不接触发布密钥。

本地发布构建使用环境变量 `RELEASE_STORE_FILE`（keystore 路径）、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`，再执行 `bash ./gradlew :app:assembleRelease`。未设置签名变量时生成未签名 release，供独立构建/签名使用。

## F-Droid

**尚未被 F-Droid 收录；本仓库提供待提交的构建配方，不代表已经提交或通过审核。**

配方为 [metadata/tech.hezy.alarm.yml](metadata/tech.hezy.alarm.yml)，本地化介绍与版本说明位于 `fastlane/metadata/android/`。将配方复制到 `fdroiddata` checkout 的 `metadata/` 目录后，在已配置的 F-Droid buildserver 环境执行：

```sh
fdroid readmeta
fdroid lint tech.hezy.alarm
fdroid build --server tech.hezy.alarm:3
```

F-Droid 从公开的 `v1.2.0` 标签构建未签名 APK，由其基础设施签名；本配方不声明与 GitHub 发布签名兼容或可复现构建。提交路线与如实填写的材料见 [fdroid/SUBMISSION.md](fdroid/SUBMISSION.md)。官方参考：[构建元数据](https://f-droid.org/docs/Build_Metadata_Reference/)、[提交指南](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)。

## 贡献与许可

任何人都可 Fork、提交 Issue 或 PR，所有改动由维护者审查后合并，不自动合并贡献；详见 [CONTRIBUTING.md](CONTRIBUTING.md)。

代码与项目自制图像按 [MIT](LICENSE) 许可发布，保留 Copyright (c) 2026 HeZY。使用 Android 系统字体，不随包分发 Apple 字体，也不声明与其他平台视觉一致。第三方依赖遵循各自许可。
