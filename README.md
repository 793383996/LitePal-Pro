# LitePal Pro (LitePal 4.x)

![Logo](https://github.com/LitePalFramework/LitePal/blob/master/sample/src/main/logo/mini_logo.png)

中文深度文档（按代码实况维护）: [`docs/README.md`](./docs/README.md)

---

## 中文版

### 1. 项目定位
LitePal Pro 是一个 Kotlin-first 的 Android SQLite ORM。  
4.x 的核心目标不是“继续堆反射能力”，而是建立 **生成式元数据（Generated Metadata）驱动** 的稳定主链路：

- 编译期通过 `compiler-ksp` 或 `compiler-kapt` 生成 registry/schema/mapping 代码与元数据。
- 运行期 `core` 直接消费生成产物，完成建库、升级、CRUD、关联装配和 schema 校验。
- API 层保持同步语义，但内部通过可配置执行器调度，方便业务统一接入协程/线程池策略。

### 2. 当前技术基线（以仓库代码为准）

- Android: `minSdk 24`, `compileSdk 36`（`sample` 目标 `targetSdk 36`）
- JVM bytecode target: `17`
- CI runtime: `JDK 21`
- Gradle Wrapper: `9.4.1`
- AGP: `9.1.0`
- Kotlin: `2.3.20`
- KSP plugin: `2.3.6`

### 3. 仓库模块与职责

- `core`: 运行时引擎（初始化、开库、CRUD、关联、事务、多库、监听、schema 校验）
- `compiler-common`: 编译器共享模型与代码渲染逻辑
- `compiler-ksp`: KSP 处理器实现
- `compiler-kapt`: KAPT 处理器实现
- `sample`: Android 示例应用（Compose + KSP 接线 + 自动 Full 稳定性测试入口）
- `sample-test`: 独立 instrumentation 测试模块（8 套件门禁 + 统一运行入口）
- `fixture-*`: 处理器一致性与负例验证工程（KSP/KAPT parity、编译期报错断言）
- `compat-3x-*`: 3.x 迁移辅助目录（维护态；默认不在 `settings.gradle` 主构建图中）

### 3.1 Sample 最新架构（App/Test 双模块拆分）

1. `sample`（应用演示模块）
   - `MyApplication` 仅负责 LitePal 初始化与运行时策略，不直接执行压测。
   - `MainActivity` 使用双 ViewModel：`SampleDataViewModel`（演示数据流）+ `TestDashboardViewModel`（测试任务流）。
   - Activity `onCreate` 自动触发一次 Full 套件：`startAutoFullRunIfNeeded()`。
   - 首页第一个子项为测试任务模块，展示进度、当前 Case、已完成/待处理、失败堆栈、历史任务。
   - UI 已按功能拆分：`SampleComposeApp`（壳层）+ `Home/Crud/Aggregate/Table/Diagnostics` 独立 Screen 文件。
   - 稳定性执行器已拆分：`StartupStabilityTestRunner` + `RunnerCore` + `CaseCatalog` + `EventLogger`。

2. `sample-test`（独立门禁模块）
   - `sample/src/androidTest` 已迁空，测试资产迁移到 `sample-test/src/androidTest`。
   - 默认 instrumentation 入口固定为 `org.litepal.sampletest.suite.SampleTestAllSuites`。
   - 套件分层固定为 8 组：
     - `boot_open`
     - `crud_relation`
     - `transaction_concurrency`
     - `multi_db_epoch`
     - `listener_preload`
     - `schema_migration_validation`
     - `dashboard_orchestration`
     - `stress_full`
   - `SampleTestRuntimeBootstrap` 统一测试运行时策略，测试 Manifest 移除 `androidx.startup.InitializationProvider` 避免进程初始化冲突。

3. 根任务与 CI 门禁
   - 根任务 `sampleTestAll`：串联 `:sample:assembleDebug -> :sample:installDebug -> :sample-test:connectedDebugAndroidTest`。
   - GitHub Actions `sample-pr-gate.yml` 在 PR/Push 场景运行 `sampleTestAll`，形成 `sample + sample-test` 独立门禁。

### 4. 核心流程（Core Flow，中文）

#### 4.1 编译期：Schema 建模与生成
1. 处理器扫描 `@LitePalSchemaAnchor`，并强制约束：
   - 必须且只能有 1 个 Anchor。
   - `entities` 不能为空。
   - 持久化字段必须有 public getter/setter（编译期直接报错）。
2. 基于实体字段与关系生成统一模型：
   - 持久化字段元数据（类型、nullable、unique、index、defaultValue、encrypt）。
   - 泛型字段元数据（如 `List<String>`）。
   - 关联元数据（`1:1 / 1:N / N:N`）。
3. 输出产物：
   - `LitePalGeneratedRegistryImpl.kt`
   - `schema-v{version}.json`
   - `schema-hash.txt`
   - `migration-diff-report.txt`
   - `META-INF/services/org.litepal.generated.LitePalGeneratedRegistry`

#### 4.2 运行期启动：初始化与开库
1. `LitePal.initialize(context)` 注入 `ApplicationContext`。
2. 首次 `LitePal.getDatabase()` 进入 `Operator -> Connector`。
3. `Connector` 在开库过程中执行 `epoch` 一致性校验（配置变更会重试与 fallback reopen）。
4. `LitePalOpenHelper.onCreate/onUpgrade` 调用 `Generator.create/upgrade` 执行 DDL。
5. 建库/升级事件先入队，随后在主线程 flush 派发监听器（超时告警后放行）。
6. 开库成功后触发 `SchemaValidationGate.validate`：
   - hash 匹配则快速通过。
   - hash 不匹配则做列级检查（`STRICT` 抛错，`LOG` 仅告警）。

#### 4.3 查询链路
1. 入口：`LitePal.find*` 或 `LitePal.where(...).find(...)`。
2. `QueryHandler -> DataHandler.query`：
   - 用生成的 `entityFactory` 创建对象实例。
   - 用生成的 `cursorMapper` 做游标到对象映射。
3. 若 `isEager=true`，执行一层关联装配：
   - 当前模型 FK 关联批量回填。
   - 对端关联（含 `N:N`）分块查询装配。
4. 泛型表（generic table）按 `chunk=500` 批量拉取并回填集合字段。

#### 4.4 写入链路
1. 入口：`LitePalSupport.save/update/delete` 或 `LitePal.saveAll/updateAll/deleteAll`。
2. `SaveHandler/UpdateHandler/DeleteHandler` 在事务内处理主表 + 关联表 + 泛型表。
3. 字段写入通过生成的 `fieldBinder` 完成，避免运行时字段反射。
4. 加密字段在绑定阶段按策略处理（`AES` 或 `MD5`，MD5 仅推荐单向摘要场景）。

#### 4.5 事务、锁与并发
- 全局并发基于公平 `ReentrantReadWriteLock`（`DatabaseRuntimeLock`）。
- `beginTransaction` 持有读锁并绑定线程上下文（支持嵌套深度）。
- `endTransaction` 保证收口并释放锁，避免泄露。
- 查询/写入 API 为同步语义，但可通过 `LitePalRuntimeOptions` 将执行派发到 query/transaction executor。

#### 4.6 多数据库切换与 epoch 一致性
- `LitePal.use(...) / useDefault()` 在写锁区切换配置并递增 `dbConfigEpoch`。
- 并发开库时如检测到 epoch 变化，`Connector` 自动重试（上限 5 次）并做 fallback 复检。
- 旧 epoch 的预热任务会被取消并回调错误，避免“旧库预热结果污染新配置”。

#### 4.7 监听与预热
- 监听器支持：
  - `registerDatabaseListener(listener)`（手动反注册）
  - `registerDatabaseListener(owner, listener)`（`LifecycleOwner` 自动反注册）
- `preloadDatabase` 以 epoch 去重，提前异步触发开库，降低主线程首开阻塞风险。

#### 4.8 Sample 自动测试编排链路（最新）
1. `MainActivity` 启动后由 `TestDashboardViewModel` 自动发起 `runSuite(HIGH)`。
2. 执行器事件统一收敛为 `RunStarted/CaseStarted/Checkpoint/CasePassed/CaseFailed/RunCancelled/RunFinished/RunCrashed`。
3. Dashboard 状态由 `StateFlow<TestDashboardUiState>` 维护，UI 只订阅状态不执行业务逻辑。
4. 长任务计时通过每秒 ticker 刷新（`delay(1000)`），并回写当前 Case 耗时。
5. 支持取消与重跑：取消后保留已完成 Case，未开始 Case 保持 pending，并按 `runId` 进入历史环形缓冲。
6. 异常路径保留完整 `stackTrace` 与 `rootCauseChain`，UI 与日志可双向追溯。

### 5. 核心原理（Core Principles，中文）

1. **生成式元数据是单一真值（Single Source of Truth）**  
   实体结构与关系来自 `@LitePalSchemaAnchor` 生成产物，运行期按 registry 消费。  
   `litepal.xml` 在 4.x 仅承担数据库配置（`dbname/version/cases/storage`），不是实体来源。

2. **Generated-Only 运行时主链**  
   4.x 主链按生成产物执行（factory/mapper/binder/accessor）。缺失即 fail fast，避免“悄悄回退”。

3. **同步 API + 可控调度**  
   API 语义保持同步，便于行为可预测；并通过 runtime options 接入你自己的线程/协程调度策略。

4. **锁 + epoch 的一致性设计**  
   数据库配置切换与开库并发由读写锁和 epoch 协同约束，减少“连接与配置错位”窗口。

5. **Schema 校验门（SchemaValidationGate）**  
   用 `schema-hash` 做快速路径，不匹配时再做列级校验；可在 `STRICT/LOG` 两种模式中选择发布策略。

6. **策略可配置但默认安全**  
   - 主线程访问默认禁止（`allowMainThreadAccess=false`）。
   - 默认主线程违规策略为 `THROW`。
   - 默认 schema 校验策略为 `STRICT`（在 runtime options 中）。

### 6. 完整使用说明（中文）

#### 6.1 环境准备
- Android 项目建议与本仓库基线一致：`minSdk >= 24`，JDK 17+ 编译。
- 选择 **KSP（推荐）** 或 **KAPT（兼容）** 二选一，不要同时接同一模块。

#### 6.2 接入依赖（源码仓库模式）

##### 6.2.1 KSP（推荐）
```groovy
plugins {
    id 'com.android.application'
    id 'com.google.devtools.ksp'
}

dependencies {
    implementation project(':core')
    ksp project(':compiler-ksp')
}
```

##### 6.2.2 KAPT（可选）
```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.kapt'
}

dependencies {
    implementation project(':core')
    kapt project(':compiler-kapt')
}
```

> 若你使用仓库外发布制品，`core` 与处理器必须来自同一版本/同一发布通道。

#### 6.3 声明且只声明一个 Schema Anchor
```kotlin
import org.litepal.annotation.LitePalSchemaAnchor
import org.litepal.litepalsample.model.Album
import org.litepal.litepalsample.model.Singer
import org.litepal.litepalsample.model.Song

@LitePalSchemaAnchor(
    version = 1,
    entities = [Singer::class, Album::class, Song::class]
)
class AppSchemaAnchor
```

硬约束：
- 全应用 classpath 只能出现一个 `@LitePalSchemaAnchor`。
- `entities` 必须非空。
- 持久化字段需 public getter + public setter（处理器编译期校验）。

#### 6.4 定义实体模型
```kotlin
import org.litepal.annotation.Column
import org.litepal.annotation.Encrypt
import org.litepal.crud.LitePalSupport

class Song : LitePalSupport() {
    var id: Long = 0

    @Column(index = true)
    var name: String? = null

    @Column(unique = true, index = true)
    @Encrypt("AES")
    var lyric: String? = null

    var duration: String? = null
    var album: Album? = null
}
```

字段说明：
- `@Column(nullable/unique/defaultValue/index/ignore)` 控制列约束。
- `@Encrypt("AES")` 推荐用于字符串加密。
- `@Encrypt("MD5")` 仍可用，但仅建议单向摘要场景。

当前持久化字段类型（处理器支持）：
- `Boolean/Float/Double/Int/Long/Short/Char/String/Date`（含常见装箱与 Kotlin 对应类型）。

#### 6.5 配置 `assets/litepal.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<litepal>
    <dbname value="app" />
    <version value="1" />
    <cases value="lower" />
    <storage value="internal" />
</litepal>
```

说明：
- 默认数据库场景建议保留 `litepal.xml`。
- 4.x 不再依赖 `<list><mapping .../></list>` 作为实体真值来源（可保留但不作为 schema 源头）。

#### 6.6 在 Application 初始化 LitePal
`AndroidManifest.xml`：
```xml
<application
    android:name=".MyApplication"
    ... />
```

`MyApplication.kt`：
```kotlin
import android.app.Application
import org.litepal.LitePal
import org.litepal.LitePalCryptoPolicy
import org.litepal.LitePalErrorPolicy
import org.litepal.LitePalRuntimeOptions
import org.litepal.MainThreadViolationPolicy
import org.litepal.SchemaValidationMode
import java.util.concurrent.Executors

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        LitePal.initialize(this)

        LitePal.setRuntimeOptions(
            LitePalRuntimeOptions(
                allowMainThreadAccess = false,
                mainThreadViolationPolicy = MainThreadViolationPolicy.THROW,
                queryExecutor = Executors.newSingleThreadExecutor(),
                transactionExecutor = Executors.newSingleThreadExecutor(),
                schemaValidationMode = SchemaValidationMode.STRICT
            )
        )

        LitePal.setErrorPolicy(LitePalErrorPolicy.STRICT)
        LitePal.setCryptoPolicy(LitePalCryptoPolicy.V2_WRITE_DUAL_READ)
    }
}
```

#### 6.7 预热与监听（推荐）
```kotlin
import org.litepal.LitePal
import org.litepal.tablemanager.callback.DatabaseListener
import org.litepal.tablemanager.callback.DatabasePreloadListener

LitePal.registerDatabaseListener(object : DatabaseListener {
    override fun onCreate() {}
    override fun onUpgrade(oldVersion: Int, newVersion: Int) {}
})

LitePal.preloadDatabase(object : DatabasePreloadListener {
    override fun onSuccess(path: String) {}
    override fun onError(throwable: Throwable) {}
})
```

Activity/Fragment 场景优先使用生命周期绑定版本：
```kotlin
LitePal.registerDatabaseListener(this, listener)
```

#### 6.8 CRUD 与查询
```kotlin
import org.litepal.LitePal
import org.litepal.extension.findAll
import org.litepal.litepalsample.model.Song

val song = Song().apply {
    name = "Track A"
    lyric = "..."
    duration = "03:30"
}
val saved = song.save()
val id = song.getBaseObjId()

val allSongs: List<Song> = LitePal.findAll()
val first = LitePal.findFirst(Song::class.java)
val eagerOne = LitePal.find(Song::class.java, id, true)

val page = LitePal
    .select("id", "name", "duration")
    .where("name like ?", "%Track%")
    .order("id desc")
    .limit(20)
    .offset(0)
    .find(Song::class.java, true)

song.duration = "03:45"
song.update(id)
LitePal.delete(Song::class.java, id)
```

#### 6.9 聚合与原生 SQL
```kotlin
val count = LitePal.count(Song::class.java)
val avg = LitePal.average(Song::class.java, "id")
val maxId = LitePal.max(Song::class.java, "id", Long::class.java)

val cursor = LitePal.findBySQL(
    "select id, name from song where name = ?",
    "Track A"
)
cursor?.close()
```

#### 6.10 事务与协程调度
```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.litepal.LitePal
import org.litepal.extension.runInTransaction

suspend fun insertInTx(): Boolean = withContext(Dispatchers.IO) {
    LitePal.runInTransaction {
        Song().apply { name = "A"; lyric = "L1" }.save()
        Song().apply { name = "B"; lyric = "L2" }.save()
        true
    }
}
```

建议：
- 事务块内不要跨线程再发 DB 操作。
- 事务尽量短，避免锁持有时间过长。

#### 6.11 多数据库切换
```kotlin
import org.litepal.LitePal
import org.litepal.LitePalDB

val tenantDb = LitePalDB("tenant_a", 1).apply {
    storage = "internal"
}

LitePal.use(tenantDb)
LitePal.getDatabase()

LitePal.useDefault()
```

删除数据库：
```kotlin
LitePal.deleteDatabase("tenant_a")
```

#### 6.12 加密策略
```kotlin
LitePal.aesKey("your-32-char-or-custom-key")
LitePal.setCryptoPolicy(LitePalCryptoPolicy.V2_WRITE_DUAL_READ)
```

策略说明：
- `V2_WRITE_DUAL_READ`：新写入使用 V2；读取兼容历史格式。
- `LEGACY_WRITE_LEGACY_READ`：仅历史模式，迁移期兜底。

#### 6.13 运行时指标与观测
```kotlin
val generatedHits = LitePal.getGeneratedPathHitCount()
val fallbackHits = LitePal.getReflectionFallbackCount()
val mainThreadBlockMs = LitePal.getMainThreadDbBlockTotalMs()
LitePal.resetRuntimeMetrics()
```

说明：
- 4.x 主链设计为 generated-only，`fallbackHits` 理想值应长期接近 `0`。

#### 6.14 3.x -> 4.x 迁移要点
- 旧 async API（如 `findAsync/countAsync`）已移除。
- 迁移方式：同步 API + 业务层协程/执行器封装。
- `compat-3x-*` 仅作迁移辅助，不建议新项目依赖。

#### 6.15 常见错误与排查

1. `LitePal requires exactly one @LitePalSchemaAnchor. None found.`
   - 检查是否声明 Anchor。
   - 检查是否接入 KSP/KAPT 处理器。

2. `LitePal requires exactly one @LitePalSchemaAnchor. Found N.`
   - 保证 classpath 仅有一个 Anchor。

3. `must expose a public setter/getter`
   - 持久化字段改为可访问 `var` 并暴露 public 访问器。

4. `Generated metadata is REQUIRED but no LitePal generated registry was found.`
   - 处理器未生效或生成产物未进 classpath。
   - 先执行 `:sample:assembleDebug`（内置 `verifyGeneratedRegistry`）。

5. `Database main-thread access blocked`
   - 将 DB 调用放到后台线程/协程。
   - 或明确配置 `allowMainThreadAccess=true`（不推荐在生产默认开启）。

6. `Schema mismatch detected...`
   - 实体/版本调整后检查 schema 变更是否符合预期。
   - 在灰度可用 `LOG` 模式观察，正式发布建议保持 `STRICT` 门禁。

### 7. 开发与验证命令

```bash
# PR Gate 级别组合验证（core/compiler）
./gradlew \
  :compiler-common:test \
  :core:assembleDebug \
  :core:lint \
  :core:testDebugUnitTest \
  verifyGeneratedOnlyRuntimeGuards \
  verifyKspKaptGeneratedParity \
  verifyProcessorNegativeCases \
  :core:publishReleasePublicationToMavenLocal

# 示例应用（包含 generated registry 校验）
./gradlew :sample:assembleDebug
./gradlew :sample:installDebug
adb shell am start -n org.litepal.litepalsample/.activity.MainActivity

# sample-test 全量 instrumentation（默认执行 SampleTestAllSuites）
./gradlew :sample-test:assembleDebugAndroidTest
./gradlew :sample-test:connectedDebugAndroidTest --stacktrace

# 根任务一键执行 sample + sample-test 门禁
./gradlew sampleTestAll --stacktrace

# 单套件定向执行示例（Dashboard 编排）
./gradlew :sample-test:connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=org.litepal.sampletest.suite.dashboard_orchestration.DashboardOrchestrationSuite" \
  --stacktrace

# 核心 instrumentation
./gradlew :core:connectedDebugAndroidTest --stacktrace
```

### 8. 参考文档

- 文档索引: [`docs/README.md`](./docs/README.md)
- 初始化与开库链路: [`docs/01-core-init-open-chain.md`](./docs/01-core-init-open-chain.md)
- 配置与实体来源链路: [`docs/02-config-entity-source.md`](./docs/02-config-entity-source.md)
- 查询/写入与对象映射: [`docs/03-query-crud-mapping.md`](./docs/03-query-crud-mapping.md)
- 事务并发: [`docs/04-transaction-lock-concurrency.md`](./docs/04-transaction-lock-concurrency.md)
- 多库切换: [`docs/05-multi-db-epoch-switch.md`](./docs/05-multi-db-epoch-switch.md)
- 监听与预热: [`docs/06-listener-lifecycle-preload.md`](./docs/06-listener-lifecycle-preload.md)
- Schema 升级与校验: [`docs/07-schema-upgrade-validation.md`](./docs/07-schema-upgrade-validation.md)

---

## English Version

### 1. Positioning
LitePal Pro is a Kotlin-first SQLite ORM for Android.  
In the 4.x line, the architecture is intentionally **generated-metadata-first**:

- Compile-time processors (`compiler-ksp` / `compiler-kapt`) generate registry/schema/mapping artifacts.
- Runtime `core` consumes those generated artifacts directly for open/migrate/CRUD/relations/schema validation.
- APIs remain synchronous for deterministic behavior, while execution can be dispatched via configurable executors.

### 2. Baseline (from this repository)

- Android: `minSdk 24`, `compileSdk 36` (`sample` targets `36`)
- JVM bytecode target: `17`
- CI runtime: `JDK 21`
- Gradle Wrapper: `9.4.1`
- AGP: `9.1.0`
- Kotlin: `2.3.20`
- KSP plugin: `2.3.6`

### 3. Modules and Responsibilities

- `core`: runtime engine (init/open/CRUD/relations/transactions/multi-db/listeners/schema gate)
- `compiler-common`: shared compiler model + rendering
- `compiler-ksp`: KSP processor implementation
- `compiler-kapt`: KAPT processor implementation
- `sample`: Android sample app (Compose + KSP wiring + auto Full stability entry)
- `sample-test`: dedicated instrumentation module (8-suite gate + unified runner entry)
- `fixture-*`: processor parity/negative-case verification fixtures
- `compat-3x-*`: migration helper directories (maintenance only, not in default `settings.gradle` graph)

### 3.1 Latest Sample Architecture (App/Test Split)

1. `sample` (app demo module)
   - `MyApplication` only initializes LitePal runtime policies; it no longer runs stress tests directly.
   - `MainActivity` binds two ViewModels: `SampleDataViewModel` (demo data flow) + `TestDashboardViewModel` (test task flow).
   - Activity auto-triggers one Full suite on startup via `startAutoFullRunIfNeeded()`.
   - Home first item is the dashboard card: progress/current case/completed-pending/error stack/history.
   - UI is split by feature: `SampleComposeApp` shell + dedicated `Home/Crud/Aggregate/Table/Diagnostics` screens.
   - Stability executor boundaries are split into `StartupStabilityTestRunner` + `RunnerCore` + `CaseCatalog` + `EventLogger`.

2. `sample-test` (independent test gate module)
   - `sample/src/androidTest` is emptied; instrumentation assets are moved to `sample-test/src/androidTest`.
   - Default instrumentation class is fixed to `org.litepal.sampletest.suite.SampleTestAllSuites`.
   - Test suite taxonomy is locked to 8 groups:
     - `boot_open`
     - `crud_relation`
     - `transaction_concurrency`
     - `multi_db_epoch`
     - `listener_preload`
     - `schema_migration_validation`
     - `dashboard_orchestration`
     - `stress_full`
   - `SampleTestRuntimeBootstrap` unifies runtime options for tests; test manifest removes `androidx.startup.InitializationProvider` to avoid startup conflicts.

3. Root task and CI gate
   - Root task `sampleTestAll` chains `:sample:assembleDebug -> :sample:installDebug -> :sample-test:connectedDebugAndroidTest`.
   - GitHub workflow `sample-pr-gate.yml` runs `sampleTestAll` on PR/Push to enforce a dedicated sample gate.

### 4. Core Flow (English)

#### 4.1 Compile-time: Schema Modeling and Artifact Generation
1. Processor scans `@LitePalSchemaAnchor` and enforces:
   - exactly one anchor in classpath,
   - non-empty `entities`,
   - public getter/setter for persistent fields.
2. It models:
   - persistent field metadata (type/nullable/unique/index/default/encrypt),
   - generic field metadata,
   - relationship metadata (`1:1`, `1:N`, `N:N`).
3. It generates:
   - `LitePalGeneratedRegistryImpl.kt`
   - `schema-v{version}.json`
   - `schema-hash.txt`
   - `migration-diff-report.txt`
   - service descriptor under `META-INF/services/...`

#### 4.2 Runtime Boot and Open
1. `LitePal.initialize(context)` stores application context.
2. `LitePal.getDatabase()` enters `Operator -> Connector`.
3. `Connector` performs epoch-stability checks during open (retry + fallback reopen if config changes).
4. `LitePalOpenHelper.onCreate/onUpgrade` invokes `Generator.create/upgrade`.
5. DB lifecycle events are queued first, then flushed on main thread.
6. `SchemaValidationGate.validate` runs after open:
   - fast-pass when schema hash matches,
   - deep column checks on mismatch (`STRICT` throws, `LOG` warns).

#### 4.3 Query Path
1. Entry via `LitePal.find*` or `LitePal.where(...).find(...)`.
2. `QueryHandler -> DataHandler.query`:
   - instantiate via generated `entityFactory`,
   - map cursor via generated `cursorMapper`.
3. With `isEager=true`, one-level relations are batch-attached.
4. Generic values are loaded in chunks (`chunk=500`) and filled back into collection fields.

#### 4.4 Write Path
1. Entry via `LitePalSupport.save/update/delete` or `LitePal.saveAll/updateAll/deleteAll`.
2. Handlers run in transactions and coordinate main table + associations + generic tables.
3. Generated `fieldBinder` is used for value binding (no runtime field reflection on the main path).

#### 4.5 Transactions and Concurrency
- Global fairness lock: `ReentrantReadWriteLock` (`DatabaseRuntimeLock`).
- Transaction context is thread-bound and supports nesting depth.
- APIs are synchronous in semantics; execution can still be routed by runtime executors.

#### 4.6 Multi-DB and Epoch Consistency
- `LitePal.use(...) / useDefault()` updates config under write lock and increments epoch.
- `Connector` detects epoch drift during open/flush and retries (max 5), then runs fallback validation logic.
- Outdated preload jobs are canceled per epoch.

#### 4.7 Listeners and Preload
- Listener registration supports manual and lifecycle-bound modes.
- `preloadDatabase` de-duplicates by epoch and helps reduce first synchronous open cost.

#### 4.8 Sample Auto-Test Orchestration Flow
1. After `MainActivity` starts, `TestDashboardViewModel` launches `runSuite(HIGH)` automatically.
2. Runner events are normalized as `RunStarted/CaseStarted/Checkpoint/CasePassed/CaseFailed/RunCancelled/RunFinished/RunCrashed`.
3. Dashboard state is reduced into `StateFlow<TestDashboardUiState>`; UI remains subscriber-only.
4. Long-running case elapsed time is refreshed every second (`delay(1000)` ticker).
5. Cancel/re-run semantics preserve completed results, keep unstarted cases as pending, and isolate history by `runId`.
6. Failure paths persist full `stackTrace` + `rootCauseChain` for UI/log traceability.

### 5. Core Principles (English)

1. **Generated metadata is the source of truth** for entity schema/relations.
2. **Generated-only runtime main path**: missing generated pieces fail fast.
3. **Sync API + configurable scheduling**: deterministic behavior, flexible integration with your threading model.
4. **Lock + epoch consistency**: reduces config/open race windows.
5. **Schema validation gate** with hash fast-path and strict/log policy control.
6. **Safe defaults**: main-thread DB access blocked by default, strict schema validation enabled in runtime options.

### 6. Complete Usage Guide (English)

#### 6.1 Environment
- Align with repository baseline (`minSdk >= 24`, JDK 17+ build toolchain).
- Choose one processor path: **KSP (recommended)** or **KAPT**.

#### 6.2 Dependency Wiring (source-repo style)

##### 6.2.1 KSP
```groovy
plugins {
    id 'com.android.application'
    id 'com.google.devtools.ksp'
}

dependencies {
    implementation project(':core')
    ksp project(':compiler-ksp')
}
```

##### 6.2.2 KAPT
```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.kapt'
}

dependencies {
    implementation project(':core')
    kapt project(':compiler-kapt')
}
```

If consuming published artifacts, keep `core` and processor artifacts on the same release/version channel.

#### 6.3 Declare Exactly One Anchor
```kotlin
@LitePalSchemaAnchor(
    version = 1,
    entities = [Singer::class, Album::class, Song::class]
)
class AppSchemaAnchor
```

#### 6.4 Define Entities
```kotlin
class Song : LitePalSupport() {
    var id: Long = 0

    @Column(index = true)
    var name: String? = null

    @Column(unique = true, index = true)
    @Encrypt("AES")
    var lyric: String? = null

    var duration: String? = null
    var album: Album? = null
}
```

#### 6.5 Configure `assets/litepal.xml`
```xml
<litepal>
    <dbname value="app" />
    <version value="1" />
    <cases value="lower" />
    <storage value="internal" />
</litepal>
```

In 4.x, `<list><mapping .../></list>` is not the schema truth source anymore.

#### 6.6 Initialize in `Application`
```kotlin
LitePal.initialize(this)

LitePal.setRuntimeOptions(
    LitePalRuntimeOptions(
        allowMainThreadAccess = false,
        mainThreadViolationPolicy = MainThreadViolationPolicy.THROW,
        queryExecutor = Executors.newSingleThreadExecutor(),
        transactionExecutor = Executors.newSingleThreadExecutor(),
        schemaValidationMode = SchemaValidationMode.STRICT
    )
)

LitePal.setErrorPolicy(LitePalErrorPolicy.STRICT)
LitePal.setCryptoPolicy(LitePalCryptoPolicy.V2_WRITE_DUAL_READ)
```

#### 6.7 Preload and Listener
```kotlin
LitePal.registerDatabaseListener(object : DatabaseListener {
    override fun onCreate() {}
    override fun onUpgrade(oldVersion: Int, newVersion: Int) {}
})

LitePal.preloadDatabase(object : DatabasePreloadListener {
    override fun onSuccess(path: String) {}
    override fun onError(throwable: Throwable) {}
})
```

#### 6.8 CRUD and Query
```kotlin
val song = Song().apply { name = "Track A"; lyric = "..."; duration = "03:30" }
song.save()

val allSongs: List<Song> = LitePal.findAll()
val eagerOne = LitePal.find(Song::class.java, song.getBaseObjId(), true)

val page = LitePal
    .where("name like ?", "%Track%")
    .order("id desc")
    .limit(20)
    .find(Song::class.java, true)
```

#### 6.9 Aggregates and Raw SQL
```kotlin
val count = LitePal.count(Song::class.java)
val avg = LitePal.average(Song::class.java, "id")
val maxId = LitePal.max(Song::class.java, "id", Long::class.java)

val cursor = LitePal.findBySQL("select id, name from song where name = ?", "Track A")
cursor?.close()
```

#### 6.10 Transactions with Coroutine Dispatch
```kotlin
suspend fun insertInTx(): Boolean = withContext(Dispatchers.IO) {
    LitePal.runInTransaction {
        Song().apply { name = "A"; lyric = "L1" }.save()
        Song().apply { name = "B"; lyric = "L2" }.save()
        true
    }
}
```

#### 6.11 Multi-Database Switch
```kotlin
val tenantDb = LitePalDB("tenant_a", 1).apply { storage = "internal" }
LitePal.use(tenantDb)
LitePal.getDatabase()
LitePal.useDefault()
LitePal.deleteDatabase("tenant_a")
```

#### 6.12 Crypto Policy
```kotlin
LitePal.aesKey("your-custom-key")
LitePal.setCryptoPolicy(LitePalCryptoPolicy.V2_WRITE_DUAL_READ)
```

#### 6.13 Runtime Metrics
```kotlin
val generatedHits = LitePal.getGeneratedPathHitCount()
val fallbackHits = LitePal.getReflectionFallbackCount()
val mainThreadBlockMs = LitePal.getMainThreadDbBlockTotalMs()
LitePal.resetRuntimeMetrics()
```

#### 6.14 Migration Notes (3.x -> 4.x)
- Legacy async APIs were removed.
- Use sync APIs plus your own coroutine/executor orchestration.
- `compat-3x-*` is migration-only, not for new integrations.

#### 6.15 Troubleshooting
Typical errors and fixes:
- missing/duplicate anchor,
- missing generated registry,
- non-public entity accessors,
- main-thread DB access violation,
- schema mismatch under strict validation.

### 7. Build and Verification Commands

```bash
# Core/compiler gate
./gradlew \
  :compiler-common:test \
  :core:assembleDebug \
  :core:lint \
  :core:testDebugUnitTest \
  verifyGeneratedOnlyRuntimeGuards \
  verifyKspKaptGeneratedParity \
  verifyProcessorNegativeCases \
  :core:publishReleasePublicationToMavenLocal

# Sample app build/install/start
./gradlew :sample:assembleDebug
./gradlew :sample:installDebug
adb shell am start -n org.litepal.litepalsample/.activity.MainActivity

# sample-test full instrumentation (default class = SampleTestAllSuites)
./gradlew :sample-test:assembleDebugAndroidTest
./gradlew :sample-test:connectedDebugAndroidTest --stacktrace

# One-shot sample gate task
./gradlew sampleTestAll --stacktrace

# Run a single suite (example: dashboard orchestration)
./gradlew :sample-test:connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=org.litepal.sampletest.suite.dashboard_orchestration.DashboardOrchestrationSuite" \
  --stacktrace

# Core instrumentation
./gradlew :core:connectedDebugAndroidTest --stacktrace
```

### 8. Documentation Index

- [`docs/README.md`](./docs/README.md)
- [`docs/01-core-init-open-chain.md`](./docs/01-core-init-open-chain.md)
- [`docs/02-config-entity-source.md`](./docs/02-config-entity-source.md)
- [`docs/03-query-crud-mapping.md`](./docs/03-query-crud-mapping.md)
- [`docs/04-transaction-lock-concurrency.md`](./docs/04-transaction-lock-concurrency.md)
- [`docs/05-multi-db-epoch-switch.md`](./docs/05-multi-db-epoch-switch.md)
- [`docs/06-listener-lifecycle-preload.md`](./docs/06-listener-lifecycle-preload.md)
- [`docs/07-schema-upgrade-validation.md`](./docs/07-schema-upgrade-validation.md)

---

License: [Apache-2.0](./LICENSE)
