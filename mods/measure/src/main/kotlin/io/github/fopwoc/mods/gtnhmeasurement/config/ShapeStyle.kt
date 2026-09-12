package io.github.fopwoc.mods.gtnhmeasurement.config

/** How an area or sphere is drawn in the world. */
enum class ShapeStyle(val lines: Boolean, val glass: Boolean) {
  LINES(lines = true, glass = false),
  GLASS(lines = false, glass = true),
  BOTH(lines = true, glass = true),
}
