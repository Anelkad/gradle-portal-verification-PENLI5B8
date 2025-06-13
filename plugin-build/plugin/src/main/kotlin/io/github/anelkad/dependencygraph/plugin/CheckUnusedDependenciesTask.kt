package io.github.anelkad.dependencygraph.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File

abstract class CheckUnusedDependenciesTask : DefaultTask() {
    init {
        group = BasePlugin.BUILD_GROUP
        description = "Check for unused dependencies of modules"
    }

    /** The project dependencies graph as [ParsedGraph]`. */
    @get:Input
    @get:Option(
        option = "graphDetails",
        description = "The project dependencies graph as [ParsedGraph]",
    )
    internal abstract val parsedGraph: Property<ParsedGraph>

    @TaskAction
    fun check() {
        val graph = parsedGraph.get()
        val androidProjects = graph.androidProjectsEnabledResources
        val modulesWithUnusedResources = mutableListOf<String>()

        var matchingModulesWithPackage = ""
        graph.projects.forEach {
            getModulePackageName(
                currentProject = it,
                appendResult = {
                    matchingModulesWithPackage += it
                }
            )
//            if (it in androidProjects) {
//                if (checkIfResourcesUnused(it)) {
//                    modulesWithUnusedResources.add(it.path)
//                }
//            }
        }
//        if (modulesWithUnusedResources.isNotEmpty()) {
//            throw GradleException("Modules to disable resources: ${modulesWithUnusedResources.joinToString(", ")}")
//        }

        val file = File(graph.rootProject.projectDir.path +"/build", "matchingModulesWithPackage.txt")
        file.parentFile.mkdirs()
        file.delete()
        file.writeText(matchingModulesWithPackage)
    }

    private fun checkIfResourcesUnused(
        currentProject: ModuleProject
    ): Boolean {

        val usingResourcesInFiles = usesResources(currentProject = currentProject)

        val file = File(currentProject.projectDir.path +"/build", "usingResourcesInFiles.txt")
        file.parentFile.mkdirs()
        file.delete()
        file.writeText(usingResourcesInFiles.joinToString("\n"))

        val resDir = File(currentProject.projectDir, "src/main/res")
        val hasAndroidResources = resDir.exists() && resDir.walkTopDown().any { it.isFile }

        return usingResourcesInFiles.isEmpty() && !hasAndroidResources
    }

    private fun usesResources(currentProject: ModuleProject): Set<String> {
        val srcDirs = listOf(
            "src/main/java",
            "src/main/kotlin",
            "src/gms/kotlin",
            "src/gms/java",
            "src/hms/kotlin",
            "src/hms/java",
        ).map { File(currentProject.projectDir, it) }.filter { it.exists() }

        val usedLibraryResources = Regex("""[a-zA-Z_][a-zA-Z0-9_]*\.[a-zA-Z_][a-zA-Z0-9_]*\.R\b""")
        val usedResources = listOf("R.drawable.", "R.string.", "R.plurals.", "R.color.")
        val usesResources = mutableSetOf<String>()

        srcDirs.forEach { srcDir ->
            srcDir.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    file.forEachLine { line ->
                        if (usedLibraryResources.find(line) != null || usedResources.any { line.contains(it) }) {
                            usesResources.add(line)
                        }
                    }
                }
        }
        return usesResources
    }

    private fun getModulePackageName(
        currentProject: ModuleProject,
        appendResult: (String) -> Unit
    ) {
        val sourceDirs = listOf(
            "src/main/java",
            "src/main/kotlin",
            "src/gms/kotlin",
            "src/gms/java",
            "src/hms/kotlin",
            "src/hms/java",
        )
        val commonDirs = listOf(
            "di",
            "ui",
            "data",
            "domain",
            "model"
        )
        sourceDirs
            .map { File(currentProject.projectDir, it) }
            .filter { it.exists() }
            .forEach { srcDir ->
                srcDir.walkTopDown()
                    .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                    .toList()
                    .map { path ->
                        var relativePath = path.relativeTo(currentProject.projectDir).path
                        val prefix = sourceDirs.find { relativePath.contains(it) }
                        prefix?.let {
                            relativePath = relativePath.removePrefix(it)
                        }
                        relativePath = relativePath
                            .substringBeforeLast("/")
                            .removePrefix("/")
                            .replace("/", ".")
                        while (commonDirs.any { relativePath.endsWith(".$it") }) {
                            val suffix = commonDirs.find { relativePath.endsWith(".$it") }
                            suffix?.let {
                               relativePath = relativePath.removeSuffix(".$suffix")
                            }
                        }
                        relativePath
                    }
                    .sortedBy { it.length }
                    .first()
                    .let {
                        appendResult("\n${currentProject.path} - $it")
                    }

            }
    }
}