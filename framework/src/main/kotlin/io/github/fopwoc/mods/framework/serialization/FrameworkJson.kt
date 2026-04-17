package io.github.fopwoc.mods.framework.serialization

import kotlinx.serialization.json.Json

object FrameworkJson {
    val prettyConfig: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
}

