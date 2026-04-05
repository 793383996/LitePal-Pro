# LitePal Pro (LitePal 4.x)

![Logo](https://github.com/LitePalFramework/LitePal/blob/master/sample/src/main/logo/mini_logo.png)

中文文档（代码实况）: [`docs/README.md`](./docs/README.md)

---

## 中文版

### 1. 项目概览
LitePal Pro 是一个 Kotlin-first 的 Android SQLite ORM。当前仓库是 4.x 主线，核心特点是：

- 运行时 `core` 只接受生成式元数据（Generated Metadata）。
- 编译期通过 `compiler-ksp` 或 `compiler-kapt` 生成 registry / schema / mapping 辅助代码。
- 支持常用 ORM 场景：建表升级、CRUD、聚合、关联查询、多数据库切换、事务、预热、监听。

> 关键约束：必须声明且只声明一个 `@LitePalSchemaAnchor`，并接入 KSP/KAPT 处理器。

### 2. 当前技术基线（以仓库代码为准）

- Android: `minSdk 24`, `compileSdk 36`（`sample` 目标 `targetSdk 36`）
- JVM bytecode target: `17`
- CI 运行时: `JDK 21`（Robolectric + SDK 36 需要）
- Gradle Wrapper: `9.4.1`

### 3. 仓库模块

- `core`: LitePal 运行时核心
- `compiler-common`: 编译器共享模型与渲染逻辑
- `compiler-ksp`: KSP 处理器
- `compiler-kapt`: KAPT 处理器
- `sample`: Android 示例应用（含 Compose UI + KSP 接线）
- `compat-3x-*`: 3.x 迁移辅助模块（维护态，已 `@Deprecated`）

### 4. 核心能力

- 查询链式 API：`select / where / order / limit / offset`
- CRUD：`find/findAll/findFirst/findLast/save/update/delete`、`saveAll`
- 聚合：`count/average/max/min/sum`
- 事务：`beginTransaction/endTransaction/setTransactionSuccessful`
- Kotlin 扩展：`runInTransaction`、`findAll<T>()` 等
- 多数据库：`LitePal.use(LitePalDB)`、`useDefault()`、`deleteDatabase()`
- 运行时策略：
  - `LitePalErrorPolicy`: `COMPAT` / `STRICT`
  - `LitePalCryptoPolicy`: `V2_WRITE_DUAL_READ` / `LEGACY_WRITE_LEGACY_READ`
  - `LitePalRuntimeOptions`: 主线程访问策略、查询/事务执行器、schema 校验策略
- Schema 运行时校验：`SchemaValidationMode.STRICT / LOG`
- 预热与监听：`preloadDatabase`、`registerDatabaseListener`
- 运行时指标：
  - `getGeneratedPathHitCount()`
  - `getReflectionFallbackCount()`
  - `getMainThreadDbBlockTotalMs()`

### 5. 接入（基于仓库源码）

#### 5.1 使用 KSP（推荐）

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

#### 5.2 使用 KAPT（可选）

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

> 如果你使用的是仓库外发布制品，处理器依赖也必须与 `core` 同版本、同发布源，保证生成 registry 可用。

### 6. 声明 Schema Anchor 与实体

#### 6.1 Anchor（必须且唯一）

```kotlin
import org.litepal.annotation.LitePalSchemaAnchor

@LitePalSchemaAnchor(
    version = 1,
    entities = [Singer::class, Album::class, Song::class]
)
class AppSchemaAnchor
```

#### 6.2 实体模型

```kotlin
import org.litepal.annotation.Column
import org.litepal.crud.LitePalSupport

class Song : LitePalSupport() {
    var id: Long = 0

    @Column(index = true)
    var name: String? = null

    @Column(unique = true, index = true)
    var lyric: String? = null

    var duration: String? = null
}
```

字段约束说明：

- 用 `@Column` 做 `nullable/unique/defaultValue/index/ignore`
- 用 `@Encrypt("AES")` 做字符串字段加密
- `@Encrypt("MD5")` 仍可用但已不推荐（仅适合单向摘要场景）
- 持久化字段需要可访问的 public setter（KSP/KAPT 会在编译期校验）

### 7. 配置 `litepal.xml`

`assets/litepal.xml` 仍用于默认数据库配置（至少建议配置 `dbname`、`version`、`storage`）：

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

- 4.x 实体来源以 `@LitePalSchemaAnchor` 生成元数据为准。
- `litepal.xml` 中旧的 `<list><mapping .../></list>` 不是 4.x 实体真值来源。

### 8. 初始化与运行时选项

```kotlin
import android.app.Application
import org.litepal.GeneratedMetadataMode
import org.litepal.LitePal
import org.litepal.LitePalRuntimeOptions
import org.litepal.MainThreadViolationPolicy
import java.util.concurrent.Executors

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        LitePal.initialize(this)

        LitePal.setRuntimeOptions(
            LitePalRuntimeOptions(
                allowMainThreadAccess = false,
                mainThreadViolationPolicy = MainThreadViolationPolicy.THROW,
                queryExecutor = Executors.newSingleThreadExecutor(),
                transactionExecutor = Executors.newSingleThreadExecutor(),
                generatedMetadataMode = GeneratedMetadataMode.REQUIRED
            )
        )
    }
}
```

### 9. 常用 API 示例

#### 9.1 CRUD + 查询

```kotlin
import org.litepal.LitePal
import org.litepal.extension.findAll

val song = Song().apply {
    name = "Track A"
    lyric = "..."
}
val saved = song.save()

val allSongs: List<Song> = LitePal.findAll()
val first = LitePal.where("name = ?", "Track A").findFirst(Song::class.java)

song.duration = "03:30"
song.update(song.getBaseObjId())

LitePal.delete(Song::class.java, song.getBaseObjId())
```

#### 9.2 事务

```kotlin
import org.litepal.LitePal
import org.litepal.extension.runInTransaction

val ok = LitePal.runInTransaction {
    Song().apply { name = "A"; lyric = "L1" }.save()
    Song().apply { name = "B"; lyric = "L2" }.save()
    true
}
```

#### 9.3 预热与监听

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

### 10. 3.x 迁移说明

- 4.x 已移除旧 async API（如 `countAsync/findAsync` 等）
- 建议使用同步 API + 你自己的协程/线程调度
- `compat-3x-*` 仅用于迁移诊断，处于维护态并计划在未来主版本移除

### 11. 开发与验证命令

```bash
# 全链路校验（近似 PR Gate）
./gradlew \
  :compiler-common:test \
  :core:assembleDebug \
  :core:lint \
  :core:testDebugUnitTest \
  verifyGeneratedOnlyRuntimeGuards \
  verifyKspKaptGeneratedParity \
  verifyProcessorNegativeCases \
  :core:publishReleasePublicationToMavenLocal

# 示例应用
./gradlew :sample:assembleDebug

# 核心 instrumentation
./gradlew :core:connectedDebugAndroidTest --stacktrace
```

### 12. 参考文档

- 文档索引: [`docs/README.md`](./docs/README.md)
- 初始化与开库链路: [`docs/01-core-init-open-chain.md`](./docs/01-core-init-open-chain.md)
- 配置与实体来源: [`docs/02-config-entity-source.md`](./docs/02-config-entity-source.md)
- Schema 升级与校验: [`docs/07-schema-upgrade-validation.md`](./docs/07-schema-upgrade-validation.md)

---

## English Version

### 1. Overview
LitePal Pro is a Kotlin-first SQLite ORM for Android. In this 4.x codebase:

- Runtime `core` requires generated metadata.
- Compile-time metadata is produced by `compiler-ksp` or `compiler-kapt`.
- The project supports schema creation/migration, CRUD, aggregates, relations, multi-db switching, transactions, preload, and lifecycle-aware listeners.

> Hard requirement: declare exactly one `@LitePalSchemaAnchor` and wire KSP/KAPT.

### 2. Current Baseline (from repository code)

- Android: `minSdk 24`, `compileSdk 36` (`sample` targets `36`)
- JVM bytecode target: `17`
- CI runtime: `JDK 21` (needed for Robolectric + SDK 36)
- Gradle Wrapper: `9.4.1`

### 3. Repository Modules

- `core`: runtime ORM engine
- `compiler-common`: shared compiler modeling/rendering
- `compiler-ksp`: KSP processor
- `compiler-kapt`: KAPT processor
- `sample`: Android sample app (Compose + KSP wiring)
- `compat-3x-*`: 3.x migration helpers (maintenance mode, deprecated)

### 4. Core Capabilities

- Fluent querying: `select / where / order / limit / offset`
- CRUD: `find/findAll/findFirst/findLast/save/update/delete`, `saveAll`
- Aggregates: `count/average/max/min/sum`
- Transactions: `beginTransaction/endTransaction/setTransactionSuccessful`
- Kotlin extensions: `runInTransaction`, reified `findAll<T>()`, etc.
- Multi-db runtime switching: `LitePal.use(LitePalDB)`, `useDefault()`, `deleteDatabase()`
- Runtime policies:
  - `LitePalErrorPolicy`: `COMPAT` / `STRICT`
  - `LitePalCryptoPolicy`: `V2_WRITE_DUAL_READ` / `LEGACY_WRITE_LEGACY_READ`
  - `LitePalRuntimeOptions`: main-thread policy, executors, schema validation mode
- Runtime schema gate: `SchemaValidationMode.STRICT / LOG`
- Preload and listener hooks: `preloadDatabase`, `registerDatabaseListener`
- Runtime metrics:
  - `getGeneratedPathHitCount()`
  - `getReflectionFallbackCount()`
  - `getMainThreadDbBlockTotalMs()`

### 5. Integration (source-repo style)

#### 5.1 KSP (recommended)

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

#### 5.2 KAPT (alternative)

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

If you consume published artifacts outside this repo, the processor artifact must come from the same release channel/version as `core`.

### 6. Schema Anchor and Entity

#### 6.1 Anchor (exactly one)

```kotlin
import org.litepal.annotation.LitePalSchemaAnchor

@LitePalSchemaAnchor(
    version = 1,
    entities = [Singer::class, Album::class, Song::class]
)
class AppSchemaAnchor
```

#### 6.2 Entity example

```kotlin
import org.litepal.annotation.Column
import org.litepal.crud.LitePalSupport

class Song : LitePalSupport() {
    var id: Long = 0

    @Column(index = true)
    var name: String? = null

    @Column(unique = true, index = true)
    var lyric: String? = null

    var duration: String? = null
}
```

Notes:

- Use `@Column` for `nullable/unique/defaultValue/index/ignore`
- Use `@Encrypt("AES")` for string-field encryption
- `@Encrypt("MD5")` is still supported but deprecated for reversible-encryption scenarios
- Persistent fields must expose public setters (validated by processors)

### 7. `litepal.xml`

`assets/litepal.xml` is still used for default database runtime config (`dbname`, `version`, `storage`, optional `cases`):

```xml
<?xml version="1.0" encoding="utf-8"?>
<litepal>
    <dbname value="app" />
    <version value="1" />
    <cases value="lower" />
    <storage value="internal" />
</litepal>
```

In 4.x, entity truth comes from generated metadata via `@LitePalSchemaAnchor`, not from legacy `<list><mapping .../></list>`.

### 8. Initialization and Runtime Options

```kotlin
import android.app.Application
import org.litepal.GeneratedMetadataMode
import org.litepal.LitePal
import org.litepal.LitePalRuntimeOptions
import org.litepal.MainThreadViolationPolicy
import java.util.concurrent.Executors

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        LitePal.initialize(this)

        LitePal.setRuntimeOptions(
            LitePalRuntimeOptions(
                allowMainThreadAccess = false,
                mainThreadViolationPolicy = MainThreadViolationPolicy.THROW,
                queryExecutor = Executors.newSingleThreadExecutor(),
                transactionExecutor = Executors.newSingleThreadExecutor(),
                generatedMetadataMode = GeneratedMetadataMode.REQUIRED
            )
        )
    }
}
```

### 9. API Examples

```kotlin
import org.litepal.LitePal
import org.litepal.extension.findAll
import org.litepal.extension.runInTransaction

val song = Song().apply {
    name = "Track A"
    lyric = "..."
}
val saved = song.save()

val songs: List<Song> = LitePal.findAll()
val first = LitePal.where("name = ?", "Track A").findFirst(Song::class.java)

val committed = LitePal.runInTransaction {
    Song().apply { name = "A"; lyric = "L1" }.save()
    Song().apply { name = "B"; lyric = "L2" }.save()
    true
}
```

### 10. Migration Notes (3.x -> 4.x)

- Legacy async APIs were removed in 4.x.
- Use sync APIs with your own coroutine/executor strategy.
- `compat-3x-*` modules are migration-only and in maintenance mode.

### 11. Build & Validation Commands

```bash
./gradlew \
  :compiler-common:test \
  :core:assembleDebug \
  :core:lint \
  :core:testDebugUnitTest \
  verifyGeneratedOnlyRuntimeGuards \
  verifyKspKaptGeneratedParity \
  verifyProcessorNegativeCases \
  :core:publishReleasePublicationToMavenLocal

./gradlew :sample:assembleDebug
./gradlew :core:connectedDebugAndroidTest --stacktrace
```

### 12. More Docs

- Docs index: [`docs/README.md`](./docs/README.md)
- Core init/open flow: [`docs/01-core-init-open-chain.md`](./docs/01-core-init-open-chain.md)
- Config/entity source flow: [`docs/02-config-entity-source.md`](./docs/02-config-entity-source.md)
- Schema upgrade/validation: [`docs/07-schema-upgrade-validation.md`](./docs/07-schema-upgrade-validation.md)

---

License: [Apache-2.0](./LICENSE)
