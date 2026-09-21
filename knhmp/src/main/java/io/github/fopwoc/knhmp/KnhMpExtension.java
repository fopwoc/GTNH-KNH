package io.github.fopwoc.knhmp;

import org.gradle.api.Action;
import org.gradle.api.Project;

public class KnhMpExtension {

    private final Project project;
    private final KnhMpTargets targets;
    private final KnhMpSourceSets sourceSets = new KnhMpSourceSets();
    private final KnhMpBuildScope common = new KnhMpBuildScope();
    private String modId;
    private String modName;
    private String modGroup;
    private String modVersion;
    private int javaToolchain = 26;

    public KnhMpExtension(Project project) {
        this.project = project;
        targets = new KnhMpTargets();
        modId = stringProperty("modId", project.getName().replace("-", ""));
        modName = stringProperty("modName", modId);
        modGroup = stringProperty("modGroup", "io.github.example." + modId);
        modVersion = stringProperty("modVersion", "0.1.0");
    }

    public void targets(Action<? super KnhMpTargets> action) {
        action.execute(targets);
    }

    public void sourceSets(Action<? super KnhMpSourceSets> action) {
        action.execute(sourceSets);
    }

    /** Build plugins applied to every target and variant of this module. */
    public void plugins(Action<? super KnhMpPlugins> action) {
        action.execute(common.getPlugins());
    }

    /** Dependencies of every target and variant of this module, e.g. {@code module(":framework")}. */
    public void dependencies(Action<? super KnhMpDependencies> action) {
        action.execute(common.getDependencies());
    }

    public KnhMpBuildScope getCommon() {
        return common;
    }

    public KnhMpTargets getTargets() {
        return targets;
    }

    public KnhMpSourceSets getSourceSets() {
        return sourceSets;
    }

    public String getModId() {
        return modId;
    }

    public void setModId(String modId) {
        this.modId = modId;
    }

    public String getModName() {
        return modName;
    }

    public void setModName(String modName) {
        this.modName = modName;
    }

    public String getModGroup() {
        return modGroup;
    }

    public void setModGroup(String modGroup) {
        this.modGroup = modGroup;
    }

    public String getModVersion() {
        return modVersion;
    }

    public void setModVersion(String modVersion) {
        this.modVersion = modVersion;
    }

    public int getJavaToolchain() {
        return javaToolchain;
    }

    public void setJavaToolchain(int javaToolchain) {
        this.javaToolchain = javaToolchain;
    }

    private String stringProperty(String name, String fallback) {
        Object value = project.findProperty(name);
        return value == null ? fallback : value.toString();
    }
}
