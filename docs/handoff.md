# tvOS 风格 UI 重写：交接文档

最后更新：2026-11-12。本文件随 PR #32（`claude/ticket-26`）提交，描述截至该分支的全部工作。

## 1. 当前状态

**一句话**：票据 01–11、16、#26 的代码已完成，均在容器内编译通过（java64 与 python64 两个 release 变体）。**还没有任何 PR 合并，也没有在真机上验证过。** 维护者要求先停下来做真机验收，剩余票据（12、14、#27、13、15）暂缓。

### PR 堆栈

PR 是堆叠的，每个 PR 的 base 是上一张票的分支，diff 里只有本票的改动。**必须从 #19 开始按顺序合并。**

| 顺序 | 票 | PR | 分支 | 状态 |
|---|---|---|---|---|
| 1 | #1 构建变体收敛（含 cling 本地仓库） | #19 | `claude/dazzling-pasteur-4rjjyw` | 待合并 |
| 2 | #2 AGP 8 / Gradle 8 / JDK 17 | #20 | `claude/ticket-02` | 待合并 |
| 3 | #3 minSdk 33 / targetSdk 34 | #21 | `claude/ticket-03` | 待合并 |
| 4 | #4 移除 XWalkView | #22 | `claude/ticket-04` | 待合并 |
| 5 | #5 Compose / Haze / Coil（含 AGP 9） | #23 | `claude/ticket-05` | 待合并 |
| 6 | #6 玻璃 tab bar 与 shelf | #24 | `claude/ticket-06` | 待合并 |
| 7 | #7 首页真实数据 | #25 | `claude/ticket-07` | 待合并 |
| 8 | #8 详情页 | #28 | `claude/ticket-08` | 待合并 |
| 9 | #9 影视 tab | #29 | `claude/ticket-09` | 待合并 |
| 10 | #10 搜索 tab | #30 | `claude/ticket-10` | 待合并 |
| 11 | #11 我的 tab | #31 | `claude/ticket-11` | 待合并 |
| 12 | #26 推送与传输入口 | #32 | `claude/ticket-26` | 待合并 |
| — | #16 边缘功能去留（决策票） | — | — | 已关闭，结论见 #16 评论 |

PR 正文里写明了每张票的改动、与原票据的差异和需要真机验证的项目。#5、#6、#8 的 PR 用的是 `Refs`，不会自动关闭对应的票：#5 和 #6 要等真机验收，#8 的「删除旧详情 Activity」推迟到了 #13。

## 2. 已与维护者确认的决策

| 决策 | 内容 | 原因 |
|---|---|---|
| 保留 Python 支持 | 只保留 `java64` 和 `python64` 两个变体（#1 原计划只留 java64） | 上游仍在维护 Python 爬虫；chaquopy 有支持 AGP 8/9 的版本 |
| Python 3.8 → 3.10 | chaquopy 17 是唯一能配 AGP 8.13+ 的版本，它要求 Python 3.10+、minSdk 24 | 为后续 Compose 不被工具链卡住 |
| 升级到 AGP 9.5 | #2 原定 AGP 8.x。Haze 的玻璃折射模块（`haze-glass`，即原 `haze-liquidglass`）只在 Haze 2.x 中提供，而 Haze 2.x、Compose 1.12、Coil 3.6 都要求 AGP 9.1+ 和 compileSdk 37 | 不升级就没有玻璃折射效果 |
| 编译验证 | 新建个人云环境 `TVBoxOS`，放行 dl.google.com、jitpack.io、maven.aliyun.com、4thline.org、chaquo.com | 容器内需要 SDK 和依赖仓库 |
| cling 从源码构建 | cling 2.1.2 / seamless 1.1.2 的唯一仓库 4thline.org 已下线，从 GitHub 源码构建后放进 `third_party/m2/` | 否则干净环境下无法构建 |
| #8 播放暂用旧页面 | 详情页用 Compose 实现；选集后由旧 `DetailActivity` 新增的「直接全屏播放」模式起播 | `PlayFragment` 与 2103 行的 `DetailActivity` 深度耦合；搬迁放到 #13 |
| #16 结论 | 推送和传输：在「我的」tab 重建入口（#26，已完成）<br>投屏（DLNA + 远程 TV）：保留代码，不提供入口<br>本地配置文件选择：在设置页重建（#27）<br>备份还原：在设置页重建（#14） | 详见 #16 评论 |
| 合并方式 | 每张票一个 PR，堆叠提交；全部完成后统一验收（现已暂停，先验收已完成部分） | — |

## 3. 构建环境（容器内）

本地机器上如果 Android Studio 配置正常，下面这些步骤大部分不需要。

```bash
# JDK 17、Android SDK（platforms;android-37.0、build-tools;37.0.0）
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/root/android-sdk          # 需写入 local.properties: sdk.dir=...
export LC_ALL=C.UTF-8 LANG=C.UTF-8             # 必须：assets/js/lib/模板.js 是中文文件名，
                                               # LANG 为空时 Gradle 无法处理（报 "Changes are not tracked"）
uv python install 3.10                         # python64 变体需要 Python 3.10 作为 buildPython

sh gradlew :app:assembleJava64Release \
  -Ppyramid.buildPython=$(uv python find 3.10)
# 产物：app/build/outputs/tvbox/TVBox_release-<flavor>.apk（release 未签名，与改动前一致）
```

- `pyramid.buildPython` 不设置时，chaquopy 会自动查找 Python：Windows 上是 `py -3.10`，其他平台是 `python3.10`。
- Maven Central 经常返回 429。容器里用 `~/.gradle/init.d/central-mirror.gradle` 把请求重定向到 Google 的镜像 `maven-central.storage-download.googleapis.com`。这个脚本**不在仓库里**，换新容器后需要重新创建。
- 容器里没有 KVM，跑不了模拟器，**所有界面和播放相关的验证只能在真机上做**。

## 4. 新代码结构（`app/src/main/java/com/github/tvbox/osc/compose/`）

| 包 | 内容 |
|---|---|
| `main/MainActivity` | 唯一的 Compose Activity，开机入口（singleTop），NavHost 承载所有 Compose 屏幕。`openSearch()` 供旧页面和广播跳转到搜索 tab；`restart()` 在设置变更后重建主界面；负责局域网服务的启停 |
| `nav/` | `AppRoutes`（main / detail / push / transfer）；`LegacyNavigator`：还没重写的页面（详情起播、设置、直播）仍跳转到旧 Activity |
| `data/` | `LegacySources`：把旧数据层（ApiConfig 回调、SourceViewModel 的 LiveData、Room）包装成挂起函数，每个请求使用独立的 SourceViewModel<br>`AppSession`：配置只加载一次；全应用唯一的「当前数据源」<br>`SearchRepository`：搜索相关数据 |
| `theme/` | `TvTokens`：所有设计 token<br>`TvFonts`：Inter → 思源黑体 → 系统字体的回退链<br>`TvTheme` |
| `components/` | `GlassTabBar`、`Shelf` / `PosterCard` / `PosterImage` |
| `home/ movies/ search/ mine/ detail/ lan/` | 各 tab 和屏幕，每个对应一个 ViewModel |
| `image/` | Coil 的 `OkHttp3NetworkClient`。没有用 coil-network-okhttp，因为它依赖 OkHttp 4，会把全应用的 OkHttp 3.12 一并升级 |
| `lab/ComposeLabActivity` | #5 的技术验证屏，入口是设置页长按「关于」，屏幕右上角显示 FPS。**真机测完帧率后删除** |

字体放在 `res/font`（共 2.9 MB）。生成脚本和 OFL 许可证在 `third_party/fonts/`。**新增界面文案后需要重新跑子集化脚本**，脚本会扫描 compose 目录里的汉字，生成结果是确定的。

## 5. 与旧代码的桥接（后续票据需要拆除）

- **`DetailActivity` 的「直接全屏播放」模式**：extras 为 `autoFullPlay`、`playFlag`、`playIndex`，入口是 `LegacyNavigator.play / resume`。会跳过旧详情界面，直接全屏起播，退出全屏即关闭页面。不传线路和集数时按历史记录续播。传入的集数是原始顺序；旧版开启倒序时，在 `DetailActivity` 里换算。→ **#13 中删除**
- **`SettingActivity.onBackPressed`**：配置地址、首页推荐、直播地址或首页数据源有变化时，调用 `MainActivity.restart()`。→ #14 重写设置页时，改为在 Compose 内直接刷新
- **`SearchReceiver`**（局域网推送搜索词）调用 `MainActivity.openSearch()`。
- **直播 tab** 目前是占位页，按钮打开旧的 `LivePlayActivity`。→ **#12**

## 6. 剩余票据与注意事项

建议顺序：12 → 14 → #27 → 13 → 15。

- **#12 直播**：`LivePlayActivity` 有 3623 行。按票据要求，只用 Compose 重写它上面的列表和信息层（分组、频道、EPG），`VideoView` 保留不动。需要删除的旧控件：`LiveChannelGroupAdapter`、`LiveChannelItemAdapter`、`LiveEpg*Adapter`、`MyEpgAdapter` 等。直播密码（`LivePasswordDialog`）和数字键换台的功能不能丢。
- **#14 设置**：`ModelSettingFragment` 中的全部设置项，加上「备份还原」。#11 已把「我的 → 设置」入口指向旧 `SettingActivity`。重写后，原来「设置变更 → `MainActivity.restart()`」的逻辑改为在 Compose 内刷新。旧首页的「首页推荐」有三种模式：豆瓣热播、数据源推荐、历史，新首页目前只实现了「数据源推荐」，可以在这里补齐。
- **#27 本地配置文件选择**：用 Compose 重写 `LocalFileActivity` 的目录浏览，排在 #14 之后。
- **#13 播放器**：需要把 `PlayFragment` 搬出 `DetailActivity`，放进新的播放器宿主，然后删除 `DetailActivity`（#8 推迟下来的验收项）。必须保留旧详情页里与播放相关的能力：
  - 自动换源
  - 详情兜底换站（`detailFallback*`）
  - 推送详情（`push_agent`）
  - 写入历史
  - 倒序

  新控制条**不放投屏按钮**（#16 的决定）。详见 #13 的评论。
- **#15 清理**：阻塞它的票增加了 #26、#27。**不要删除投屏相关代码**：`dlna/`、`CastDeviceDialog`、`RemoteTVBox`、`SearchRemoteTvDialog`、`dialog_cast.xml`、cling 依赖、`third_party/m2`。其他待清理项：
  - 删除 #5 的 `ComposeLabActivity`
  - 删除 multidex 依赖和 `MultiDexApplication`（AGP 9 已提示不需要）
  - Gradle 10 会移除 Groovy 的空格赋值写法，需要逐处改为 `=` 赋值
  - 清理已删页面留下、不再被引用的 drawable 和字符串

## 7. 真机验收清单（重点）

1. **#3 权限与兼容**：
   - 首次启动的「所有文件访问权限」引导，以及拒绝后的提示
   - 依赖 jar 的 JS 源是否能用（修过 Android 14 可写 jar 被拒绝加载的问题）
   - 第三方播放器检测，靠新增的 `<queries>`
   - 音乐/听书前台服务
2. **#2 / #5 依赖升级**：
   - Python 源在 3.10 下能否运行
   - cronet 101 → 143 后，网络和 HTTP3 播放是否正常
   - DLNA 投屏，代码保留了，可以从旧详情页触发
3. **#5 / #6 观感**：
   - 玻璃折射和高光、焦点弹簧和投影、相邻卡片变暗
   - 验证屏上的 FPS 实测值，请记录到 #5
4. **#7–#11 新界面**：
   - 首页和影视 tab 的分类、筛选、翻页；切换数据源后两个 tab 是否都刷新
   - 详情页选集起播是否正确（包括倒序），退出全屏能否回到详情页，返回后焦点是否回到原卡片
   - 搜索：拼音联想、多源进度、搜索源选择
   - 历史续播、收藏、删除和清空的确认框
   - 推送和传输
   - 在旧设置页修改配置后返回，主界面能否重新加载；「开机默认进入直播」
5. **构建**：在 Windows 或 macOS 本地，用 Android Studio（支持 AGP 9.5）同步并编译成功。

## 8. 发现并修复的旧问题（顺带）

- 干净环境下无法构建：cling 仓库下线，见 #19。
- `JsLoader` 在 targetSdk 34 下会因为 jar 可写而加载失败，见 #21。
- 旧首页作为子页面时，连按两次返回会结束全部页面、停止局域网服务并杀掉进程；旧首页销毁时也会停掉局域网服务。#29 修复了这两点，之后旧首页在 #31 中被删除。
