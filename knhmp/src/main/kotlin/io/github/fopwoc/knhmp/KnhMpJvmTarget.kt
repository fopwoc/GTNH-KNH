package io.github.fopwoc.knhmp

internal fun Int.asKotlinJvmTarget(): String = if (this == 8) "1.8" else toString()
