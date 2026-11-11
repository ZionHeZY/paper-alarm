# F-Droid Request for Packaging 材料

当前状态：**尚未提交，尚未收录**。公开源码仓库已经建立；GitLab 提交仍需维护者登录。不要把准备好的材料当作审核或发布结果。

## 提交前

- [x] 公开源码：https://github.com/ZionHeZY/paper-alarm
- [x] MIT 许可与 HeZY 版权声明随仓库保留；项目自制图像按 MIT 发布，不包含 Apple 字体。
- [x] 提供构建配方和 en-US / zh-CN 本地化介绍及 code 3 版本说明。
- [ ] 确认公开标签 `v1.2.0` 已推送，版本为 `1.2.0` / `3`；确认 CI 和实际响铃验证完成。
- [ ] 在 fdroiddata 环境运行 `fdroid lint tech.hezy.alarm` 与 `fdroid build --server tech.hezy.alarm:3`，并检查已有收录/请求以免重复。

## 可粘贴的申请内容

Title: Paper Alarm — offline alarms and birthday reminders

- Application name: Paper Alarm
- Application ID: tech.hezy.alarm
- License: MIT
- Categories: Alarm Clock
- Source code: https://github.com/ZionHeZY/paper-alarm
- Issue tracker: https://github.com/ZionHeZY/paper-alarm/issues
- Release version: 1.2.0 (versionCode 3); source tag: v1.2.0 (confirm published before submitting)
- Upstream APK: GitHub Releases at https://github.com/ZionHeZY/paper-alarm/releases (confirm the v1.2.0 signed APK and SHA256SUMS are available before submitting)
- Build recipe: metadata/tech.hezy.alarm.yml in the source repository; app subdirectory, Gradle 8.9, JDK 17, Android SDK 35, Build Tools 34.0.0
- Store descriptions: fastlane/metadata/android/en-US and zh-CN

Description: Offline one-time and weekly alarms, adjustable snooze, and solar/Chinese lunar birthday reminders for Android 13+. Birthdays can be read from and written to Android contacts. The app has no Internet permission, ads, analytics, account requirement or bundled Apple fonts. It uses Android system fonts. Contact-account sync is controlled by Android, not this app.

Packaging notes: The recipe builds from source without release signing secrets, and does not download a prebuilt APK. No upstream-signature reproducibility claim is made. F-Droid can build and sign the release independently. The app is not currently listed on F-Droid. Please review the build recipe and inclusion eligibility; local preparation alone does not establish acceptance.

Adoption: This newly published project is used by its maintainer. No established public adoption or three-month public development history is claimed; please assess eligibility under the current inclusion policy.

## 官方提交路线

1. 按 [fdroiddata 贡献指南](https://gitlab.com/fdroid/fdroiddata/-/blob/master/CONTRIBUTING.md) 检查是否已有收录或请求。
2. 初次贡献可在 [Request for Packaging](https://gitlab.com/fdroid/rfp/-/issues/new) 登录后填写模板，粘贴上面的材料并按真实验证结果勾选。不要绕过登录或安全验证。
3. 若自行打包，将配方复制到自己的 fdroiddata 分支，运行官方 CI 后提交 metadata Merge Request；可在 RFP 中关联它。是否接收、构建及收录由 F-Droid 维护者决定。

参考：[官方提交指南](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)、[RFP 项目说明](https://gitlab.com/fdroid/rfp/-/blob/master/README.md)。
