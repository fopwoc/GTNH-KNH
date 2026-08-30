package io.github.fopwoc.mods.framework

import java.io.File

@Suppress("unused")
open class ModProxy {
  open fun preInit(configDirectory: File) = Unit

  open fun init() = Unit
}
