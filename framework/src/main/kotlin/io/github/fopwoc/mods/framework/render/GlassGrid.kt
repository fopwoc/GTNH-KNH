package io.github.fopwoc.mods.framework.render

/** When the grid is drawn on a glass surface. */
enum class GlassGrid {
  OFF,
  /** Only while the eye is inside the shape — the depth cue that a uniform tint lacks. */
  INSIDE,
  ALWAYS,
}
