package io.github.fopwoc.knhmp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.api.Action;

public final class KnhMpSourceSets {

    private final Map<String, KnhMpSourceSet> sourceSets = new LinkedHashMap<>();

    public KnhMpSourceSet sourceSet(String name) {
        return sourceSets.computeIfAbsent(name, KnhMpSourceSet::new);
    }

    public void sourceSet(String name, Action<? super KnhMpSourceSet> configure) {
        configure.execute(sourceSet(name));
    }

    public void commonMain(Action<? super KnhMpSourceSet> configure) {
        configure.execute(getCommonMain());
    }

    public void gtnhMain(Action<? super KnhMpSourceSet> configure) {
        configure.execute(getGtnhMain());
    }

    public void fabricLegacyMain(Action<? super KnhMpSourceSet> configure) {
        configure.execute(getFabricLegacyMain());
    }

    public void fabricMain(Action<? super KnhMpSourceSet> configure) {
        configure.execute(getFabricMain());
    }

    public void neoforgeMain(Action<? super KnhMpSourceSet> configure) {
        configure.execute(getNeoforgeMain());
    }

    public KnhMpSourceSet getCommonMain() {
        return sourceSet("commonMain");
    }

    public KnhMpSourceSet getGtnhMain() {
        return sourceSet("gtnhMain");
    }

    public KnhMpSourceSet getFabricLegacyMain() {
        return sourceSet("fabricLegacyMain");
    }

    public KnhMpSourceSet getFabricMain() {
        return sourceSet("fabricMain");
    }

    public KnhMpSourceSet getNeoforgeMain() {
        return sourceSet("neoforgeMain");
    }

    List<String> closure(String leaf) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        collect(leaf, result, new LinkedHashSet<>());
        return new ArrayList<>(result);
    }

    int effectiveJvmTarget(String leaf, int backendMinimum) {
        int sourceRequirement = closure(leaf).stream()
                .map(sourceSets::get)
                .map(KnhMpSourceSet::getJvmTarget)
                .filter(target -> target != null)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(backendMinimum);
        return Math.max(sourceRequirement, backendMinimum);
    }

    Set<String> names() {
        return Set.copyOf(sourceSets.keySet());
    }

    Collection<KnhMpSourceSet> all() {
        return List.copyOf(sourceSets.values());
    }

    boolean isEmpty() {
        return sourceSets.isEmpty();
    }

    private void collect(String name, LinkedHashSet<String> result, LinkedHashSet<String> visiting) {
        KnhMpSourceSet sourceSet = sourceSets.get(name);
        if (sourceSet == null) {
            throw new IllegalStateException("Unknown source set: " + name);
        }
        if (!visiting.add(name)) {
            throw new IllegalStateException("Source-set cycle: " + visiting + " -> " + name);
        }
        for (String parent : sourceSet.parents()) {
            collect(parent, result, visiting);
        }
        visiting.remove(name);
        result.add(name);
    }
}
