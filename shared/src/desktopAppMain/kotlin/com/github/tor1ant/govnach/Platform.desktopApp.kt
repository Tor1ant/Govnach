package com.github.tor1ant.govnach

class PcPlatform : Platform {
    override val name: String = "Pc"
}

actual fun getPlatform(): Platform = PcPlatform()