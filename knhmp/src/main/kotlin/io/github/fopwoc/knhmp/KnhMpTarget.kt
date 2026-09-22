package io.github.fopwoc.knhmp

import org.gradle.api.Action

/**
 * One Minecraft variant of a target: a configuration scope plus, optionally, the source
 * compatibility family it compiles. Variants never create source sets; they select one.
 */
class KnhMpMinecraftVariant internal constructor(val version: String) : KnhMpBuildScope() {

    /** Leaf source set for this variant; `null` means the target's default source set. */
    var sourceSet: String? = null
}

/**
 * One loader target. Target-level plugins and dependencies apply to every Minecraft variant;
 * `minecraft(version) { ... }` scopes add to a single variant without touching the source graph.
 */
class KnhMpTarget internal constructor(
    val name: String,
    var sourceSet: String,
    val buildTask: String,
    val runTask: String,
    val bytecodeMinimum: Int,
    internal val impliedMinecraftVersion: String? = null,
) : KnhMpBuildScope() {

    private val variants = LinkedHashMap<String, KnhMpMinecraftVariant>()

    fun minecraft(vararg versions: String) {
        versions.forEach { variant(it) }
    }

    fun minecraft(vararg versions: String, configure: Action<in KnhMpMinecraftVariant>) {
        versions.forEach { configure.execute(variant(it)) }
    }

    private fun variant(version: String): KnhMpMinecraftVariant {
        require(version.isNotBlank()) { "Target $name declares a blank Minecraft version" }
        return variants.getOrPut(version) { KnhMpMinecraftVariant(version) }
    }

    /** Declared Minecraft versions in declaration order; empty when the backend implies the version. */
    internal val minecraftVersions: List<String> get() = variants.keys.toList()

    internal fun variantSourceSet(minecraftVersion: String?): String =
        minecraftVersion?.let { variants[it]?.sourceSet } ?: sourceSet

    /** Every leaf source set this target compiles: the default plus family overrides. */
    internal fun sourceSets(): Set<String> =
        linkedSetOf(sourceSet).apply { variants.values.mapNotNullTo(this) { it.sourceSet } }

    internal fun variantScope(minecraftVersion: String): KnhMpBuildScope? = variants[minecraftVersion]
}
