package io.github.anelkad.dependencygraph.plugin

import io.github.anelkad.dependencygraph.plugin.core.getExternalDependencies
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File

abstract class CheckKotlinModuleTask : DefaultTask() {
    init {
        group = BasePlugin.BUILD_GROUP
        description = "Generates dependency metrics files for all local modules in the project."
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

        val androidProjects = graph.androidProjects

        graph.projects.forEach {
            if (it in androidProjects) {
                checkIfDependenciesContainsAndroidModules(
                    graph = graph,
                    currentProject = it
                )
            }
        }
    }

    private fun checkIfDependenciesContainsAndroidModules(
        graph: ParsedGraph,
        currentProject: ModuleProject
    ) {
        val dependencies: LinkedHashMap<DependencyPair, List<String>> =
            graph.dependencies

        val androidProjects = graph.androidProjects
        val currentProjectDependencies =
            gatherDependencies(mutableListOf(currentProject), dependencies)

        currentProjectDependencies.remove(currentProject)
        if (
            currentProjectDependencies.all {
                it !in androidProjects
            }
        ) {
            if (
                getExternalDependencies(
                    currentProject = currentProject,
                    parsedGraph = graph,
                ).all {
                    !it.contains("android")
                }
            ) {
                val resDir = File(currentProject.projectDir, "src/main/res")
                if (!resDir.exists() || resDir.listFiles()?.isEmpty() != false) {
                    if (!isContainsAndroidImports(currentProject)) {
                        throw GradleException("${currentProject.path} not contains android dependencies, make it kotlin module!")
                    }
                }
            }
        }
    }

    private fun isContainsAndroidImports(
        currentProject: ModuleProject
    ): Boolean {
        val srcDirs = listOf(
            "src/main/java",
            "src/main/kotlin",
            "src/gms/kotlin",
            "src/gms/java",
            "src/hms/kotlin",
            "src/hms/java"
        ).map { File(currentProject.projectDir, it) }.filter { it.exists() }

        val importRegex = Regex("""^\s*import\s+([a-zA-Z0-9_.]+)""")
        val androidImports = mutableSetOf<String>()

        srcDirs.forEach { srcDir ->
            srcDir.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                .forEach { file ->
                    file.forEachLine { line ->
                        val match = importRegex.find(line)
                        val import = match?.groupValues?.get(1)
                        if (import != null && (import.contains("android") || import.contains("androidx"))) {
                            androidImports.add(import)
                        }
                    }
                }
        }
        return androidImports.isNotEmpty()
    }

    private fun gatherDependencies(
        currentProjectAndDependencies: MutableList<ModuleProject>,
        dependencies: LinkedHashMap<DependencyPair, List<String>>,
    ): MutableList<ModuleProject> {
        var addedNew = false
        dependencies
            .map { it.key }
            .forEach { (currProject, dependencyProject) ->
                if (
                    currentProjectAndDependencies.contains(currProject) &&
                    !currentProjectAndDependencies.contains(dependencyProject)
                ) {
                    currentProjectAndDependencies.add(dependencyProject)
                    addedNew = true
                }
            }
        return if (addedNew) {
            gatherDependencies(
                currentProjectAndDependencies = currentProjectAndDependencies,
                dependencies = dependencies,
            )
        } else {
            currentProjectAndDependencies
        }
    }
}