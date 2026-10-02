"""Compile a baseline's storage/render sources against the current headless benchmark runtime."""

from pathlib import Path
import os
import subprocess
import sys


def git(*args):
    return subprocess.check_output(["git", *args])


def main():
    work = Path(sys.argv[1]).resolve()
    work.mkdir(parents=True, exist_ok=True)
    baseline = sys.argv[2] if len(sys.argv) > 2 else "2.2.2"
    base = "mods/palimpsest/src/commonMain/kotlin/io/github/fopwoc/mods/palimpsest/"
    benchmark = "mods/palimpsest/src/benchmark/kotlin/io/github/fopwoc/mods/palimpsest/benchmark"
    paths = git(
        "ls-tree", "-r", "--name-only", baseline,
        base + "tree", base + "render", base + "benchmark", benchmark,
    ).decode().splitlines()
    paths += [base + "map/" + name + ".kt" for name in ("MapPageCache", "MapPageKey", "MapPageRaster")]
    paths += [str(path) for path in Path(benchmark, "checkpoint").glob("*.kt")]
    paths = list(dict.fromkeys(paths))
    sources = []
    for name in paths:
        if "/page/" in name or "/baseline/" in name or not name.endswith(".kt"):
            continue
        target = work / "baseline-sources" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        data = Path(name).read_bytes() if "/checkpoint/" in name or name.endswith("/GiantWorldScenario.kt") else git("show", baseline + ":" + name)
        target.write_bytes(data)
        sources.append(str(target))

    cache = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle")))
    cache = cache / "caches/modules-2/files-2.1"

    def jar(group, artifact, version):
        candidates = [
            path for path in (cache / group / artifact / version).rglob("*.jar")
            if not path.name.endswith(("-sources.jar", "-javadoc.jar"))
        ]
        if len(candidates) != 1:
            raise RuntimeError(f"Expected one cached {group}:{artifact}:{version}; build the project first")
        return str(candidates[0])

    # Fixed to the compiler and dependencies used for this checkpoint comparison.
    compiler = jar("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.4.20")
    plugin = jar("org.jetbrains.kotlin", "kotlin-compose-compiler-plugin-embeddable", "2.4.20")
    extras = [
        jar("org.jetbrains", "annotations", "24.1.0"),
        jar("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10"),
        jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.0"),
    ]
    runtime = (work / "runtime-classpath.txt").read_text().strip()
    java = (work / "java.txt").read_text().strip()
    command = [
        java, "-cp", ":".join([compiler, *extras, runtime]),
        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-Xplugin=" + plugin,
        "-language-version", "2.1", "-api-version", "2.1", "-no-stdlib", "-no-reflect",
    ]
    output = str(work / "baseline-classes")
    common = [p for p in sources if "/src/commonMain/" in p]
    bench = [p for p in sources if "/src/benchmark/" in p]
    subprocess.run(command + ["-module-name", "checkpoint_common", "-jvm-target", "21", "-classpath", runtime, "-d", output, *common], check=True)
    subprocess.run(command + ["-module-name", "checkpoint_benchmark", "-Xfriend-paths=" + output, "-jvm-target", "25", "-classpath", output + ":" + runtime, "-d", output, *bench], check=True)

if __name__ == "__main__":
    main()
