gradle.projectsEvaluated {
    val target = rootProject.findProject(":palimpsest") ?: return@projectsEvaluated
    target.tasks.register("checkpointClasspath") {
        dependsOn(target.tasks.named("compileBenchmarkKotlinIde"))
        doLast {
            val directory = File(checkNotNull(System.getenv("REPORT_DIR"))).apply { mkdirs() }
            val suite = target.tasks.named("checkpointExperiment").get() as JavaExec
            directory.resolve("runtime-classpath.txt").writeText(suite.classpath.asPath)
            directory
                .resolve("java.txt")
                .writeText(suite.javaLauncher.get().executablePath.asFile.absolutePath)
        }
    }
}
