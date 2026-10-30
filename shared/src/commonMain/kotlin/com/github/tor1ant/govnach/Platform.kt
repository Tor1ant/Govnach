package com.github.tor1ant.govnach

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform