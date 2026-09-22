package io.github.fopwoc.knhmp

import org.gradle.api.Action

class KnhMpSourceSets {

    private val sourceSets = linkedMapOf<String, KnhMpSourceSet>()

    val commonMain: KnhMpSourceSet get() = sourceSet("commonMain")
    val gtnhMain: KnhMpSourceSet get() = sourceSet("gtnhMain")
    val fabricLegacyMain: KnhMpSourceSet get() = sourceSet("fabricLegacyMain")
    val fabricMain: KnhMpSourceSet get() = sourceSet("fabricMain")
    val neoforgeMain: KnhMpSourceSet get() = sourceSet("neoforgeMain")

    fun sourceSet(name: String): KnhMpSourceSet =
        sourceSets.getOrPut(name) { KnhMpSourceSet(name) }

    fun sourceSet(name: String, configure: Action<in KnhMpSourceSet>) =
        configure.execute(sourceSet(name))

    fun commonMain(configure: Action<in KnhMpSourceSet>) = configure.execute(commonMain)

    fun gtnhMain(configure: Action<in KnhMpSourceSet>) = configure.execute(gtnhMain)

    fun fabricLegacyMain(configure: Action<in KnhMpSourceSet>) = configure.execute(fabricLegacyMain)

    fun fabricMain(configure: Action<in KnhMpSourceSet>) = configure.execute(fabricMain)

    fun neoforgeMain(configure: Action<in KnhMpSourceSet>) = configure.execute(neoforgeMain)

    internal fun closure(leaf: String): List<String> = buildList {
        collect(leaf, this, linkedSetOf())
    }

    internal fun effectiveJvmTarget(leaf: String, backendMinimum: Int): Int =
        closure(leaf)
            .mapNotNull { sourceSets.getValue(it).jvmTarget }
            .maxOrNull()
            ?.coerceAtLeast(backendMinimum)
            ?: backendMinimum

    internal fun names(): Set<String> = sourceSets.keys.toSet()

    internal fun all(): Collection<KnhMpSourceSet> = sourceSets.values.toList()

    internal val isEmpty: Boolean get() = sourceSets.isEmpty()

    private fun collect(name: String, result: MutableList<String>, visiting: MutableSet<String>) {
        val sourceSet = checkNotNull(sourceSets[name]) { "Unknown source set: $name" }
        check(visiting.add(name)) { "Source-set cycle: $visiting -> $name" }
        sourceSet.parents().forEach { collect(it, result, visiting) }
        visiting.remove(name)
        if (name !in result) result += name
    }
}
