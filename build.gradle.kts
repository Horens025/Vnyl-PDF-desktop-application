import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

group = "app.vnyl"
version = "0.9.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(kotlin("test"))
}

val buildRustCore by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds rust/vnyl-core (release cdylib) and copies it to native/win-x64 and native/resources/windows-x64"
    commandLine("cargo", "build", "--release", "-p", "vnyl-core")
    doLast {
        val targetDll = file("target/release/vnyl_core.dll")
        val outDirs = listOf(file("native/win-x64"), file("native/resources/windows-x64"))
        if (targetDll.exists()) {
            outDirs.forEach { dir ->
                dir.mkdirs()
                copy {
                    from(targetDll)
                    into(dir)
                }
            }
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(buildRustCore)
}

tasks.withType<JavaExec>().configureEach {
    dependsOn(buildRustCore)
    jvmArgs(
        "-Djava.library.path=${file("native/win-x64").absolutePath}",
        "-Dvnyl.native.dir=${file("native/win-x64").absolutePath}",
    )
}

tasks.test {
    dependsOn(buildRustCore)
    useJUnitPlatform()
    jvmArgs(
        "-Djava.library.path=${file("native/win-x64").absolutePath}",
        "-Dvnyl.native.dir=${file("native/win-x64").absolutePath}",
    )
}

compose.desktop {
    application {
        mainClass = "app.vnyl.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Vnyl"
            packageVersion = "0.9.0"
            description = "Vnyl PDF & Word Studio with Single-Canvas Skiko Liquid Glass"
            appResourcesRootDir.set(project.layout.projectDirectory.dir("native/resources"))
            windows {
                iconFile.set(project.file("native/resources/vnyl.ico"))
                menu = true
                shortcut = true
                menuGroup = "Vnyl"
            }
        }
    }
}
