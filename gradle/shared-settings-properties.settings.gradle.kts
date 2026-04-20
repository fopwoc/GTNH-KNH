val sharedPropertiesDir = generateSequence(rootDir) { it.parentFile }
    .map { it.resolve("gradle") }
    .firstOrNull {
        it.resolve("shared-build.properties").isFile
    } ?: error("Could not find shared Gradle properties directory from ${rootDir.absolutePath}")

val sharedProjectProperties = java.util.Properties().apply {
    sharedPropertiesDir.resolve("shared-build.properties").inputStream().use { input ->
        load(input)
    }
}

gradle.beforeProject(object : org.gradle.api.Action<org.gradle.api.Project> {
    override fun execute(project: org.gradle.api.Project) {
        sharedProjectProperties.entries.forEach { entry ->
            val propertyName = entry.key.toString()
            if (!project.hasProperty(propertyName)) {
                project.extensions.extraProperties.set(propertyName, entry.value.toString())
            }
        }
    }
})

