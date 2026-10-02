"""Run three alternating tag/current JVM pairs after exporting and compiling the baseline."""

from pathlib import Path
import subprocess
import sys

work = Path(sys.argv[1]).resolve()
runtime = (work / "runtime-classpath.txt").read_text().strip()
java = (work / "java.txt").read_text().strip()
for round_number in range(1, 4):
    modes = ("tag", "current") if round_number % 2 else ("current", "tag")
    for mode in modes:
        output = work / f"{mode}-{round_number}"
        if output.exists():
            raise RuntimeError(f"Output already exists: {output}; choose a fresh report directory")
        classpath = f"{work / 'baseline-classes'}:{runtime}" if mode == "tag" else runtime
        print(f"Running {mode}, round {round_number}", flush=True)
        with (work / f"{mode}-{round_number}.log").open("w") as log:
            subprocess.run([
                java, "-Xmx9g", "-cp", classpath,
                "io.github.fopwoc.mods.palimpsest.benchmark.checkpoint.CheckpointExperimentMainKt",
                str(output),
            ], stdout=log, stderr=subprocess.STDOUT, check=True)
        print(f"PASS {mode}, round {round_number}", flush=True)
