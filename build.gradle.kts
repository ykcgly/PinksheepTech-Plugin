plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.pinksheep"
version = "1.1.3-standalone"

java {
    // 编译期必须用 JDK 25：paper-api 26.3 的 class 是 major 69，JDK 21 的 javac 无法读取。
    // 产物仍以 --release 21 输出，因此 1.21.x（Java 21）与 26.3（Java 25）服务端都能加载。
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.compileJava {
    options.encoding = "UTF-8"
    // 单 jar 同时兼容 1.21.x 与 26.x，字节码目标取较低的一侧（Java 21）。
    // 不用 17：paper-api 26.3 的签名可能引用 JDK 21+ 类型，--release 17 会编译失败。
    options.release.set(21)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // 以最高目标版本编译：26.3 相对 1.21.11 只新增 Material 常量、未移除任何 API，
    // 本项目实际调用的符号在两个版本均存在（另见「跨版本符号校验」），
    // 故这一份产物可同时运行于 1.21.x 与 26.3 服务端。固定 build 号，不用 26.3.build.+ 以免漂到 alpha。
    compileOnly("io.papermc.paper:paper-api:26.3.build.249-stable")
    // 编译目标 = 测试服实际运行的中文版 Slimefun4（API 与官方版路径一致，行为核对过 registerMobDrop）
    compileOnly(files("libs/Slimefun-f172239-Beta.jar"))
    // 旧格式 saveditem 净化解析需要原生 SnakeYAML（运行时由 Paper 服务器提供 2.2）
    compileOnly("org.yaml:snakeyaml:2.2")
    // Slimefun jar 的类签名引用了 JetBrains 注解；缺失时 javac 在特定推断场景会崩溃
    compileOnly("org.jetbrains:annotations:26.0.2")
    // bStats 匿名统计（serviceId 35674）：必须 implementation 让类打进 jar，
    // shadowJar 阶段 relocate 到插件包下，避免与其他附属的 bStats 冲突
    implementation("org.bstats:bstats-bukkit:3.2.1")
}

configurations.compileClasspath {
    attributes {
        // paper-api 26.3 由 JDK 25 编译，Gradle 需按 25 解析 JVM 变体，否则该依赖会被过滤掉。
        attribute(org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

// 把 content/ 下的 YAML 内容与 saveditems/ 一并打进 jar（插件运行期从自身资源读取）
tasks.processResources {
    filteringCharset = "UTF-8"
    from(rootProject.projectDir.resolve("content")) {
        include("*.yml")
        into("")
    }
    from(rootProject.projectDir.resolve("content/saveditems")) {
        include("*.yml")
        into("saveditems")
    }
}

// 未 shaded 的普通 jar（缺 bStats 类，仅供调试参考，不可部署）
tasks.jar {
    archiveBaseName.set("PinksheepTech")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("unshaded")
}

// 发布产物 = shaded jar（bStats relocate 进插件包，避免与其它附属冲突）
tasks.shadowJar {
    archiveBaseName.set("PinksheepTech")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")
    relocate("org.bstats", "com.pinksheep.pinksheeptech.libs.bstats")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
