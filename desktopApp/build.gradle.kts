import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)

    implementation(libs.koin.core)
    implementation(libs.koin.compose) // для Compose
}

compose.desktop {
    application {
        mainClass = "com.github.tor1ant.govnach.MainKt"

        // Иконки — логотип двача (стрелка), без фона. Наборы собирает
        // tools/gen_desktop_icons.py: кадры заголовка/трея отдельно от ярлыка,
        // потому что Windows спрашивает у окна 16–32px и не должен масштабировать.
        val appIconDir = layout.projectDirectory.dir("icons")
        val windowsIcon = appIconDir.file("govnach.ico")
        val setupIcon = appIconDir.file("govnach-setup.ico")
        val macIcon = appIconDir.file("govnach.icns")

        jvmArgs(
            // Папка кадров иконки для окна при `run`/`hotRun` и из IDE: без неё
            // ОС рисует дефолтную картинку JVM. Установленное приложение берёт
            // иконку ярлыка от jpackage.
            "-Dgovnach.iconDir=${appIconDir.asFile.absolutePath}"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.github.tor1ant.govnach"
            packageVersion = "1.0.0"

            // У каждого пакета своя иконка: exe/ярлык окна — одна, установщик MSI — другая.
            windows {
                iconFile.set(windowsIcon)
                // Пункт в меню «Пуск» и ярлык на рабочем столе — с той же иконкой.
                menu = true
                shortcut = true
                // Версия MSI не меняется, а upgradeUuid должен оставаться стабильным:
                // так новая сборка обновляет старую, а не встаёт второй копией.
                upgradeUuid = "6F3F0B7B-6F8E-4F1B-9E1E-9A0C3C9E2A11"
                iconFile.set(setupIcon)
            }
            macOS {
                iconFile.set(macIcon)
            }
        }
    }
}
