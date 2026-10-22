UMI Browser v1.0-beta Android 源代码
===================================
目标：Android 13 TCL TAB 10L Gen 2；兼容 Android 8+
语言：简体中文、葡萄牙语（应用内切换）

当前包含：
- 内置 Camera2 摄像头实时扫码（ZXing 解码，不依赖 Google Lens）
- 识别普通网页二维码与文字二维码
- 每次扫码选择普通浏览 / 客人模式
- 普通浏览器：网址输入、搜索、前进后退、刷新、扫码
- 客人模式：隐藏网址输入、同源主页面导航限制、PIN 解锁
- 客人结束后清理 WebView 缓存、Cookie、WebStorage
- 不要求 Root / 设备管理员 / Google Play 登录

注意：这是未编译、未在实机验证的 beta 源码，不是已经测试通过的 APK。

Windows 编译：
1. 安装 Android Studio（官方 developer.android.com/studio）。
2. Android Studio -> Open -> 选择 UMI-Browser-v1.0 文件夹。
3. 按提示安装 Android SDK 35、Gradle 所需组件；首次编译需联网下载依赖。
4. Build -> Build Bundle(s) / APK(s) -> Build APK(s)。
5. APK 位于 app/build/outputs/apk/debug/app-debug.apk。
6. CMD: adb install -r "APK实际路径"

使用前务必：
- 先在首页设置员工 PIN，再进入客人模式。
- 对真实聚客二维码测试桌号、购物车、下单、会话清理。
- 当前 PIN 存在应用私有偏好中，不是加密凭据；属于轻量阻挡，不是高安全权限控制。
- 同源网站导航限制不等于桌号锁定；聚客不同桌台可能同域。
- 当前仅拦截主框架的跨源跳转；嵌入网页、弹窗、重定向与特殊支付流程需要实机测试。
- Android 系统导航仍可能退出应用；可选用系统自带屏幕固定功能。
- 当前清理网站存储为 WebView 全局清理；普通浏览器中保留的登录状态也可能被清除。
- 不支持文件下载、上传选择器、多个标签页、推送通知、完整 Chrome 扩展等高级浏览器功能。
- 本版本使用 targetSdk 28 便于 Android 13 调试；正式发布应更新 targetSdk 并验证新系统行为。
- 对于二维码扫描速度、摄像头预览旋转、扫码识别率需要 TCL 实机测试。

安全提示：
- 不要把此 beta 当作完全隔离或防逃逸的企业级 Kiosk。
- WebView 安全更新依赖平板系统的 Android System WebView。
- 未强制使用 https 的网站可能遭到中间人攻击；应优先扫描 HTTPS 页面。
