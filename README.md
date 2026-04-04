# LitePal for Android  
![Logo](https://github.com/LitePalFramework/LitePal/blob/master/sample/src/main/logo/mini_logo.png) 

[中文文档](https://blog.csdn.net/sinyu890807/category_9262963.html)

4.0 project docs are available in [`docs/`](./docs/README.md).

LitePal is an open source Android library that allows developers to use SQLite database extremely easy. You can finish most of the database operations without writing even a SQL statement, including create or upgrade tables, crud operations, aggregate functions, etc. The setup of LitePal is quite simple as well, you can integrate it into your project in less than 5 minutes. 

Experience the magic right now and have fun!

## Features
 * Using object-relational mapping (ORM) pattern.
 * Almost zero-configuration(only one configuration file with few properties).
 * Maintains all tables automatically(e.g. create, alter or drop tables).
 * Multi databases supported.
 * Encapsulated APIs for avoiding writing SQL statements.
 * Awesome fluent query API.
 * Alternative choice to use SQL still, but easier and better APIs than the originals.
 * More for you to explore.

## Quick Setup
#### 1. Include library

Edit your **build.gradle** file and add below dependency.

``` groovy
dependencies {
    implementation 'org.litepal.pro:core:4.0.1'
}
```

> LitePal 4.0:
> - Kotlin async APIs are removed.
> - minSdk is 24.
> - Schema upgrade uses safe migration strategy for unique/not-null changes.
> - Java static-call compatibility is no longer guaranteed; migrate to Kotlin-first API usage.

#### 2. Configure litepal.xml
Create a file in the **assets** folder of your project and name it as **litepal.xml**. Then copy the following codes into it.
``` xml
<?xml version="1.0" encoding="utf-8"?>
<litepal>
    <!--
    	Define the database name of your application. 
    	By default each database name should be end with .db. 
    	If you didn't name your database end with .db, 
    	LitePal would plus the suffix automatically for you.
    	For example:    
    	<dbname value="demo" />
    -->
    <dbname value="demo" />

    <!--
    	Define the version of your database. Each time you want 
    	to upgrade your database, the version tag would helps.
    	Modify the models you defined in the mapping tag, and just 
    	make the version value plus one, the upgrade of database
    	will be processed automatically without concern.
			For example:    
    	<version value="1" />
    -->
    <version value="1" />

    <!--
    	Define your models in the list with mapping tag, LitePal will
    	create tables for each mapping class. The supported fields
    	defined in models will be mapped into columns.
    	For example:    
    	<list>
    		<mapping class="com.test.model.Reader" />
    		<mapping class="com.test.model.Magazine" />
    	</list>
    -->
    <list>
    </list>
    
    <!--
        Define where the .db file should be. "internal" means the .db file
        will be stored in the database folder of internal storage which no
        one can access. "external" means the .db file will be stored in the
        path to the directory on the primary external storage device where
        the application can place persistent files it owns which everyone
        can access. "internal" will act as default.
        For example:
        <storage value="external" />
    -->
    
</litepal>
```
This is the only configuration file, and the properties are simple. 
 * **dbname** configure the database name of project.
 * **version** configure the version of database. Each time you want to upgrade database, plus the value here.
 * **list** configure the mapping classes.
 * **storage** configure where the database file should be stored. **internal** and **external** are the only valid options.
 
#### 3. Configure LitePalApplication
You don't want to pass the Context param all the time. To makes the APIs simple, just configure the LitePalApplication in **AndroidManifest.xml** as below:
``` xml
<manifest>
    <application
        android:name="org.litepal.LitePalApplication"
        ...
    >
        ...
    </application>
</manifest>
```
Of course you may have your own Application and has already configured here, like:
``` xml
<manifest>
    <application
        android:name="com.example.MyOwnApplication"
        ...
    >
        ...
    </application>
</manifest>
```
That's OK. LitePal can still live with that. Just call **LitePal.initialize(context)** in your own Application:
```java
public class MyOwnApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        LitePal.initialize(this);
    }
    ...
}
```
Make sure to call this method as early as you can. In the **onCreate()** method of Application will be fine. And always remember to use the application context as parameter. Do not use any instance of activity or service as parameter, or memory leaks might happen.
## Get Started
After setup, you can experience the powerful functions now.

#### 1. Create tables
Define the models first. For example you have two models, **Album** and **Song**. The models can be defined as below:
``` java
public class Album extends LitePalSupport {
	
    @Column(unique = true, defaultValue = "unknown")
    private String name;
    
    @Column(index = true)
    private float price;
	
    private List<Song> songs = new ArrayList<>();

    // generated getters and setters.
    ...
}
```
``` java
public class Song extends LitePalSupport {
	
    @Column(nullable = false)
    private String name;
	
    private int duration;
	
    @Column(ignore = true)
    private String uselessField;
	
    private Album album;

    // generated getters and setters.
    ...
}
```
Then add these models into the mapping list in **litepal.xml**:
``` xml
<list>
    <mapping class="org.litepal.litepalsample.model.Album" />
    <mapping class="org.litepal.litepalsample.model.Song" />
</list>
```
OK! The tables will be generated next time you operate database. For example, gets the **SQLiteDatabase** with following codes:
``` java
SQLiteDatabase db = LitePal.getDatabase();
```
Now the tables will be generated automatically with SQLs like this:
``` sql
CREATE TABLE album (
	id integer primary key autoincrement,
	name text unique default 'unknown',
	price real
);

CREATE TABLE song (
	id integer primary key autoincrement,
	name text not null,
	duration integer,
	album_id integer
);
```

#### 2. Upgrade tables
Upgrade tables in LitePal is extremely easy. Just modify your models anyway you want:
```java
public class Album extends LitePalSupport {
	
    @Column(unique = true, defaultValue = "unknown")
    private String name;
	
    @Column(ignore = true)
    private float price;
	
    private Date releaseDate;
	
    private List<Song> songs = new ArrayList<>();

    // generated getters and setters.
    ...
}
```
A **releaseDate** field was added and **price** field was annotated to ignore.
Then increase the version number in **litepal.xml**:
```xml
<!--
    Define the version of your database. Each time you want 
    to upgrade your database, the version tag would helps.
    Modify the models you defined in the mapping tag, and just 
    make the version value plus one, the upgrade of database
    will be processed automatically without concern.
    For example:    
    <version value="1" />
-->
<version value="2" />
```
The tables will be upgraded next time you operate database. A **releasedate** column will be added into **album** table and the original **price** column will be removed. All the data in **album** table except those removed columns will be retained.

But there are some upgrading conditions that LitePal can't handle and all data in the upgrading table will be cleaned:
 * Add a field which annotated as `unique = true`.
 * Change a field's annotation into `unique = true`.
 * Change a field's annotation into `nullable = false`.

Be careful of the above conditions which will cause losing data.

#### 3. Save data
The saving API is quite object oriented. Each model which inherits from **LitePalSupport** would have the **save()** method for free.

Java:
``` java
Album album = new Album();
album.setName("album");
album.setPrice(10.99f);
album.setCover(getCoverImageBytes());
album.save();
Song song1 = new Song();
song1.setName("song1");
song1.setDuration(320);
song1.setAlbum(album);
song1.save();
Song song2 = new Song();
song2.setName("song2");
song2.setDuration(356);
song2.setAlbum(album);
song2.save();
```

Kotlin:
```kotlin
val album = Album()
album.name = "album"
album.price = 10.99f
album.cover = getCoverImageBytes()
album.save()
val song1 = Song()
song1.name = "song1"
song1.duration = 320
song1.album = album
song1.save()
val song2 = Song()
song2.name = "song2"
song2.duration = 356
song2.album = album
song2.save()
```
This will insert album, song1 and song2 into database with associations.

#### 4. Update data
The simplest way, use **save()** method to update a record found by **find()**.

Java:
``` java
Album albumToUpdate = LitePal.find(Album.class, 1);
albumToUpdate.setPrice(20.99f); // raise the price
albumToUpdate.save();
```

Kotlin:
```kotlin
val albumToUpdate = LitePal.find<Album>(1)
albumToUpdate.price = 20.99f // raise the price
albumToUpdate.save()
```

Each model which inherits from **LitePalSupport** would also have **update()** and **updateAll()** method. You can update a single record with a specified id.

Java:
``` java
Album albumToUpdate = new Album();
albumToUpdate.setPrice(20.99f); // raise the price
albumToUpdate.update(id);
```

Kotlin:
```kotlin
val albumToUpdate = Album()
albumToUpdate.price = 20.99f // raise the price
albumToUpdate.update(id)
```

Or you can update multiple records with a where condition.

Java:
``` java
Album albumToUpdate = new Album();
albumToUpdate.setPrice(20.99f); // raise the price
albumToUpdate.updateAll("name = ?", "album");
```

Kotlin:
```kotlin
val albumToUpdate = Album()
albumToUpdate.price = 20.99f // raise the price
albumToUpdate.updateAll("name = ?", "album")
```

#### 5. Delete data
You can delete a single record using the static **delete()** method in **LitePal**.

Java:
``` java
LitePal.delete(Song.class, id);
```

Kotlin:
```kotlin
LitePal.delete<Song>(id)
```

Or delete multiple records using the static **deleteAll()** method in **LitePal**.

Java:
``` java
LitePal.deleteAll(Song.class, "duration > ?" , "350");
```

Kotlin:
```kotlin
LitePal.deleteAll<Song>("duration > ?" , "350")
```

#### 6. Query data
Find a single record from song table with specified id.

Java:
``` java
Song song = LitePal.find(Song.class, id);
```

Kotlin:
```kotlin
val song = LitePal.find<Song>(id)
```

Find all records from song table.

Java:
``` java
List<Song> allSongs = LitePal.findAll(Song.class);
```

Kotlin:
```kotlin
val allSongs = LitePal.findAll<Song>()
```

Constructing complex query with fluent query.

Java:
``` java
List<Song> songs = LitePal.where("name like ? and duration < ?", "song%", "200").order("duration").find(Song.class);
```

Kotlin:
``` kotlin
val songs = LitePal.where("name like ? and duration < ?", "song%", "200").order("duration").find<Song>()
```

#### 7. Multiple databases
If your app needs multiple databases, LitePal support it completely. You can create as many databases as you want at runtime. For example:
```java
LitePalDB litePalDB = new LitePalDB("demo2", 1);
litePalDB.addClassName(Singer.class.getName());
litePalDB.addClassName(Album.class.getName());
litePalDB.addClassName(Song.class.getName());
LitePal.use(litePalDB);
```
This will create a **demo2** database with **singer**, **album** and **song** tables.

If you just want to create a new database but with same configuration as **litepal.xml**, you can do it with:
```java
LitePalDB litePalDB = LitePalDB.fromDefault("newdb");
LitePal.use(litePalDB);
```
You can always switch back to default database with:
```java
LitePal.useDefault();
```
And you can delete any database by specified database name:
```java
LitePal.deleteDatabase("newdb");
```

#### 8. Transaction
LitePal support transaction for atomic db operations. All operations in the transaction will be committed or rolled back together.

Java usage:
```java
LitePal.beginTransaction();
boolean result1 = // db operation1
boolean result2 = // db operation2
boolean result3 = // db operation3
if (result1 && result2 && result3) {
    LitePal.setTransactionSuccessful();
}
LitePal.endTransaction();
```

Kotlin usage:
```kotlin
LitePal.runInTransaction {
    val result1 = // db operation1
    val result2 = // db operation2
    val result3 = // db operation3
    result1 && result2 && result3
}
```

## ProGuard
If you are using ProGuard you might need to add the following option:

```proguard
-keep class org.litepal.** {*;}
-keep class * extends org.litepal.crud.LitePalSupport {*;}
```

## Maven 发布指南（目标坐标：org.litepal.pro:core:4.0.1）
当前仓库默认发布 `:core` 模块，坐标如下：

```text
groupId    = org.litepal.pro
artifactId = core
version    = 4.0.1
```

#### 1. 发布前分析（必须先确认）
- 坐标变更点：从旧坐标迁移到 `org.litepal.pro:core:4.0.1`，其中 `artifactId` 仍为 `core`。
- 命名空间建议：在 Central Portal 建议申请 `org.litepal`，再发布子命名空间 `org.litepal.pro`。
- 直接申请 `org.litepal.pro` 的前提：需证明你控制 `pro.litepal.org` 域名。
- Maven Central 强制要求：POM 元信息完整、带 sources/javadoc、并完成 GPG 签名。

#### 2. 准备账号与密钥
在 `~/.gradle/gradle.properties` 中配置（建议本机用户目录，不要提交到仓库）：

```properties
# 坐标（可选，默认就是下列值）
POM_GROUP_ID=org.litepal.pro
POM_ARTIFACT_ID=core
POM_VERSION=4.0.1

# Central Portal Token（或 OSSRH 兼容 token）
CENTRAL_USERNAME=your-central-token-username
CENTRAL_PASSWORD=your-central-token-password

# 提交到 Portal manual upload 接口时使用的命名空间
# 推荐填：org.litepal
CENTRAL_NAMESPACE=org.litepal

# ASCII-armored 私钥和口令
SIGNING_KEY=-----BEGIN PGP PRIVATE KEY BLOCK-----\n...\n-----END PGP PRIVATE KEY BLOCK-----
SIGNING_PASSWORD=your-gpg-passphrase
SIGNING_KEY_ID=0123456789ABCDEF
```

#### 3. 先做本地验证（避免远端失败）
Windows:

```powershell
.\gradlew.bat :core:publishReleasePublicationToMavenLocal
```

macOS / Linux:

```bash
./gradlew :core:publishReleasePublicationToMavenLocal
```

#### 4. 上传到 Sonatype 兼容发布端点
Windows:

```powershell
.\gradlew.bat :core:publishReleasePublicationToSonatypeRepository
```

macOS / Linux:

```bash
./gradlew :core:publishReleasePublicationToSonatypeRepository
```

#### 5. 提交到 Central Portal 可见性队列
Windows:

```powershell
.\gradlew.bat :core:submitReleaseToCentralPortal
```

macOS / Linux:

```bash
./gradlew :core:submitReleaseToCentralPortal
```

一条命令串行发布：

```powershell
.\gradlew.bat :core:publishReleaseToCentralPortal
```

#### 6. 发布后验收
- 在 Central Portal 检查 deployment 状态是否成功。
- 等待 Maven Central 索引完成，再对外公告。
- 对外依赖写法：

```groovy
dependencies {
    implementation 'org.litepal.pro:core:4.0.1'
}
```

## 4.0.1 可靠性增强说明
- 新增运行时错误策略：
  - `LitePal.setErrorPolicy(LitePalErrorPolicy.COMPAT)`（默认）
  - `LitePal.setErrorPolicy(LitePalErrorPolicy.STRICT)`
- 新增加密策略：
  - `LitePal.setCryptoPolicy(LitePalCryptoPolicy.V2_WRITE_DUAL_READ)`（默认，AES-GCM 新写入 + 兼容旧密文读取）
  - `LitePal.setCryptoPolicy(LitePalCryptoPolicy.LEGACY_WRITE_LEGACY_READ)`
- `@Encrypt(MD5)` 仍兼容，但已弃用，仅建议用于不可逆摘要场景。

## Bugs Report
If you find any bug when using LitePal, please report **[here](https://github.com/LitePalFramework/LitePal/issues/new)**. Thanks for helping us making better.

## Change logs

### 4.0.1
 * Runtime stability hardening with read/write lock based DB runtime coordination.
 * SQL safety improvements: delete/update cascade paths are parameterized.
 * Introduce crypto dual-stack migration (`AES-GCM v2` write + legacy read compatibility).
 * Add runtime error policy and crypto policy APIs.
 * Replace `printStackTrace()` with structured logging.
 * Add core-level tests and GitHub Actions verification workflows.

### 4.0.0
 * Kotlin first upgrade and toolchain refresh.
 * Remove built-in async APIs in Kotlin layer. Use your own coroutine/executor.
 * Introduce safer schema migration policy for unique/not-null upgrades.
 * Remove legacy 3.x compatibility helper modules (`compat-3x-*`) from the publish surface.

### 3.2.3
 * Support database index by adding @Column(index = true) on field.
 * Adding return value for **runInTransaction()** function for Kotlin.
 * Fix known bugs.

### 3.1.1
 * Support transaction.
 * Add return value for **LitePal.saveAll()** method.
 * No longer support byte array field as column in table.
 * Deprecate all async methods. You should handle async operations by yourself.
 * Fix known bugs.
 
### 3.0.0
 * Optimize generic usage for async operation APIs.
 * Add **LitePal.registerDatabaseListener()** method for listening create or upgrade database events.
 * Provider better CRUD API usage for using generic declaration instead of Class parameter for kotlin.
 * Fix known bugs.

### 2.0.0
 * Offer new APIs for CRUD operations. Deprecate **DataSupport**, use **LitePal** and **LitePalSupport** instead.
 * Fully support kotlin programming.
 * Fix known bugs.

### 1.6.1
 * Support AES and MD5 encryption with @Encrypt annotation on fields.
 * Support to store database file on any directory of external storage.
 * Fix known bugs.

### 1.5.1
 * Support async operations for all crud methods.
 * Add **saveOrUpdate()** method in DataSupport.
 * Fix known bugs.

### 1.4.1
 * Support multiple databases.
 * Support crud operations for generic collection data in models.
 * Add SQLite keywords convert function to avoid keywords conflict.
 * Fix bug of DateSupport.count error.
 * Fix bug of losing blob data when upgrading database.
 * Fix other known bugs.
 
### 1.3.2
 * Improve an outstanding speed up of querying and saving.
 * Support to store database file in external storage.
 * Support to mapping fields which inherit from superclass.
 * Add **findFirst()** and **findLast()** in fluent query.
 * Add **isExist()** and **saveIfNotExist()** method in DataSupport.

### 1.3.1
 * Support storing binary data. Byte array field will be mapped into database as blob type.
 * Add **saveFast()** method in DataSupport. If your model has no associations to handle, use **saveFast()** method will be much more efficient.
 * Improve query speed with optimized algorithm.
 
### 1.3.0
 * Add annotation functions to declare **unique**, **not null** and **default** constraints.
 * Remove the trick of ignore mapping fields with non-private modifier.
 * Support to use annotation to ignore mapping fields with `ignore = true`
 * Add some magical methods in DataSupport for those who understand LitePal deeper.
 * Fix known bugs.
 
## License
```
Copyright (C) Lin Guo, LitePal Framework Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
