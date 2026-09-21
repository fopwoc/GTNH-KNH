package io.github.fopwoc.knhmp;

import java.util.LinkedHashSet;
import java.util.Set;

public final class KnhMpSourceSet {

    private final String name;
    private final Set<String> parents = new LinkedHashSet<>();
    private Integer jvmTarget;

    KnhMpSourceSet(String name) {
        this.name = name;
    }

    public void dependsOn(KnhMpSourceSet... sourceSets) {
        for (KnhMpSourceSet sourceSet : sourceSets) {
            if (sourceSet == this) {
                throw new IllegalArgumentException(name + " cannot depend on itself");
            }
            parents.add(sourceSet.name);
        }
    }

    public Integer getJvmTarget() {
        return jvmTarget;
    }

    public void setJvmTarget(int jvmTarget) {
        if (jvmTarget < 8) {
            throw new IllegalArgumentException("jvmTarget must be at least 8: " + jvmTarget);
        }
        this.jvmTarget = jvmTarget;
    }

    String name() {
        return name;
    }

    Set<String> parents() {
        return Set.copyOf(parents);
    }
}
