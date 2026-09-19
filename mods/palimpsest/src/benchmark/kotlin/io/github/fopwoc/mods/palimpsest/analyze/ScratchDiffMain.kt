package io.github.fopwoc.mods.palimpsest.analyze

import io.github.fopwoc.mods.palimpsest.tree.BlockTable
import io.github.fopwoc.mods.palimpsest.tree.MachineId
import io.github.fopwoc.mods.palimpsest.tree.MapTree
import java.nio.file.Path

fun main(args: Array<String>) {
    val dim = Path.of(args[0])
    val machine = MachineId.load(dim)
    val blocks = BlockTable(dim, machine)
    MapTree(dim.resolve("y255"), machine).use { tree ->
        val epochs = tree.roots.epochs()
        println("${epochs.size} commits")
        val last = epochs.takeLast(args.getOrNull(1)?.toInt() ?: 8)
        val churn = HashMap<String, Int>()
        for (epoch in last) {
            val index = epochs.indexOf(epoch)
            val prev = if (index > 0) epochs[index - 1] else -1L
            val tiles = tree.changedTiles(prev, epoch)
            println("== commit $epoch (+${(epoch - prev) / 1000}s): ${tiles.size} tiles changed")
            for (tile in tiles) {
                val a = if (prev < 0) null else tree.tile(tile, prev)
                val b = tree.tile(tile, epoch) ?: continue
                if (a == null) {
                    println("  $tile: new")
                    continue
                }
                val positions = a.changedPositions(b)
                val pairs = HashMap<String, Int>()
                for (p in positions) {
                    val ka = blocks.key(a.block(p)) ?: "#${a.block(p)}"
                    val kb = blocks.key(b.block(p)) ?: "#${b.block(p)}"
                    val extra = buildString {
                        if (a.height(p) != b.height(p)) append(" h${a.height(p)}->${b.height(p)}")
                        if (a.depth(p) != b.depth(p)) append(" d${a.depth(p)}->${b.depth(p)}")
                        if (a.biome(p) != b.biome(p)) append(" biome")
                    }
                    val key = if (ka == kb) "$ka (same)$extra" else "$ka -> $kb$extra"
                    pairs.merge(key, 1, Int::plus)
                    churn.merge(key, 1, Int::plus)
                }
                println("  $tile: ${positions.size} px")
                for ((k, n) in pairs.entries.sortedByDescending { it.value }.take(6)) println(
                    "      $n × $k"
                )
            }
        }
        println("== totals")
        for ((k, n) in churn.entries.sortedByDescending { it.value }.take(30)) println("  $n × $k")
    }
}
