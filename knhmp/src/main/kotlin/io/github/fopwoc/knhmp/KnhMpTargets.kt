package io.github.fopwoc.knhmp

import org.gradle.api.Action

class KnhMpTargets internal constructor() {

    private val targets = LinkedHashMap<String, KnhMpTarget>()

    fun gtnh() = gtnh {}
    fun gtnh(configure: Action<in KnhMpTarget>) =
        configure.execute(add("gtnh", "gtnhMain", "reobfJar", "runClient25", 8, "1.7.10"))

    fun fabric() = fabric {}
    fun fabric(configure: Action<in KnhMpTarget>) =
        configure.execute(add("fabric", "fabricMain", "build", "runClient", 8))

    fun neoforge() = neoforge {}
    fun neoforge(configure: Action<in KnhMpTarget>) =
        configure.execute(add("neoforge", "neoforgeMain", "build", "runClient", 8))

    fun all(): Collection<KnhMpTarget> = targets.values

    private fun add(
        name: String,
        sourceSet: String,
        buildTask: String,
        runTask: String,
        bytecodeMinimum: Int,
        impliedMinecraftVersion: String? = null,
    ): KnhMpTarget {
        val target =
            KnhMpTarget(
                name,
                sourceSet,
                buildTask,
                runTask,
                bytecodeMinimum,
                impliedMinecraftVersion,
            )
        require(targets.putIfAbsent(name, target) == null) { "Target already declared: $name" }
        return target
    }
}
