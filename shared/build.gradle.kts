import org.gradle.kotlin.dsl.support.kotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("org.openapi.generator") version "7.25.0"
    kotlin("plugin.serialization") version "2.4.10"
}

kotlin {
    jvm("desktopApp")

    android {
        namespace = "com.github.tor1ant.govnach.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation(libs.koin.android)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.media.player)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }

    sourceSets {
        commonMain.configure {
            kotlin.srcDir("${layout.buildDirectory.get()}/generated/2ch/src/main/kotlin")
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

openApiGenerate {
    generatorName = "kotlin"
    inputSpec.set("$rootDir/openapi/api.yml")
    outputDir.set("$buildDir/generated/2ch")

    configOptions.set(
        mapOf(
            "library" to "jvm-ktor",
            "serializationLibrary" to "kotlinx_serialization",
            "modelNameMappings" to "java.io.File=FileModel"
        )
    )
}

tasks.withType(KotlinCompile::class) {
    dependsOn(tasks.openApiGenerate)
}

tasks.configureEach {
    if (name.startsWith("prepare") && name.contains("Android")) {
        dependsOn(tasks.openApiGenerate)
    }
}
