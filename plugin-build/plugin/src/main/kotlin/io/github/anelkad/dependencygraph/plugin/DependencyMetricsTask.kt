package io.github.anelkad.dependencygraph.plugin

import io.github.anelkad.dependencygraph.plugin.core.getDependenciesInDepth
import io.github.anelkad.dependencygraph.plugin.core.getDependentsInDepth
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File

abstract class DependencyMetricsTask : DefaultTask() {
    init {
        group = BasePlugin.BUILD_GROUP
        description = "Generates dependency metrics files for all local modules in the project."
    }

    /** Github URL for the repository. */
    @get:Input
    @get:Option(option = "repoRootUrl", description = "Github URL for the repository")
    @get:Optional
    abstract val repoRootUrl: Property<String>

    /** Name of the main branch. `main` is used if not provided. */
    @get:Input
    @get:Option(
        option = "mainBranchName",
        description = "Name of the main branch. `main` is used if not provided.",
    )
    @get:Optional
    abstract val mainBranchName: Property<String>

    /** Name for the file where graph is saved. Default is dependency-graph.md`. */
    @get:Input
    @get:Option(
        option = "graphFileName",
        description = "Name for the file where graph is saved. Default is dependency-graph.md`",
    )
    @get:Optional
    abstract val graphFileName: Property<String>

    /**
     * Whether module name text should link to graphs for that module.
     *
     * Github doesn't support click navigation from mermaid graphs at the moment. Linking the text
     * instead provides a work around for allowing navigating between subgraphs.
     */
    @get:Input
    @get:Option(
        option = "shouldLinkModuleText",
        description = "Whether module name text should link to graphs for that module",
    )
    @get:Optional
    abstract val shouldLinkModuleText: Property<Boolean>


    /** Whether and where a legend should be displayed. */
    @get:Input
    @get:Option(
        option = "shouldGroupModules",
        description = "Whether submodules should be grouped together",
    )
    @get:Optional
    abstract val shouldGroupModules: Property<Boolean>

    /** The project dependencies graph as [ParsedGraph]`. */
    @get:Input
    @get:Option(
        option = "graphDetails",
        description = "The project dependencies graph as [ParsedGraph]",
    )
    internal abstract val parsedGraph: Property<ParsedGraph>


    @TaskAction
    fun createDependencyMetrics() {
        val graph = parsedGraph.get()

        val resultOfModulesDependents: MutableMap<String, Int> = mutableMapOf()
        val resultOfModulesDependencies: MutableMap<String, Int> = mutableMapOf()

        printDependentsInDepth(
            graph = graph,
            resultOfModulesDependents = resultOfModulesDependents
        )
        printDependenciesInDepth(
            graph = graph,
            resultOfModulesDependencies = resultOfModulesDependencies
        )
//        printModuleCentrality(
//            graph = graph,
//            resultOfModulesDependents = resultOfModulesDependents,
//            resultOfModulesDependencies = resultOfModulesDependencies
//        )
    }

    private fun printModuleCentrality(
        graph: ParsedGraph,
        resultOfModulesDependents: MutableMap<String, Int>,
        resultOfModulesDependencies: MutableMap<String, Int>
    ) {
        val resultOfModulesCentrality: MutableList<Triple<String, Int, Int>> = mutableListOf()

        val modules = mutableSetOf<String>()
        modules.addAll(
            resultOfModulesDependencies.entries
                .sortedByDescending { it.value }
                .take(150)
                .map {
                    it.key
                }
        )
        modules.addAll(
            resultOfModulesDependents.entries
                .sortedByDescending { it.value }
                .take(150)
                .map {
                    it.key
                }
        )

        var resultCentralityText = ""
        modules.forEach {
            resultOfModulesCentrality.add(
                Triple(
                    it,
                    resultOfModulesDependencies.getOrDefault(it, 0),
                    resultOfModulesDependents.getOrDefault(it, 0)
                )
            )
        }
        resultOfModulesCentrality.sortedByDescending { it.third }.forEach {
            resultCentralityText += "${it.first} | dependencies - ${it.second} | dependents - ${it.third}\n"
        }
        val file = File(graph.rootProject.projectDir.path +"/build", "sorted_projects_centrality.txt")
        file.parentFile.mkdirs()
        file.delete()
        file.writeText(resultCentralityText)
    }

    private fun printDependentsInDepth(
        graph: ParsedGraph,
        resultOfModulesDependents: MutableMap<String, Int>
    ) {
        var resultDependentsInDepthText = ""
        graph.projects.forEach {
            getDependentsInDepth(
                currentProject = it,
                parsedGraph = graph,
                resultOfModulesDependents = resultOfModulesDependents
            )
        }
        resultOfModulesDependents.entries.sortedByDescending { it.value }.forEach {
            resultDependentsInDepthText += "${it.key} - ${it.value}\n"
        }
        val sortedFile = File(graph.rootProject.projectDir.absolutePath + "/build", "sorted_dependents_in_depths.json")
        sortedFile.parentFile.mkdirs()
        sortedFile.delete()
        sortedFile.writeText(resultDependentsInDepthText)

        val json = Json { prettyPrint = true }
            .encodeToString(value = resultOfModulesDependents.toSortedMap().toMap())
        val file = File(graph.rootProject.projectDir.absolutePath + "/graph_metrics", "alphabetic_dependents_in_depths.json")
        file.parentFile.mkdirs()
        file.delete()
        file.writeText(json)
    }

    private fun printDependenciesInDepth(
        graph: ParsedGraph,
        resultOfModulesDependencies: MutableMap<String, Int>
    ) {
        var resultDependenciesInDepthText = ""
        graph.projects.forEach {
            getDependenciesInDepth(
                currentProject = it,
                parsedGraph = graph,
                resultOfModulesDependencies = resultOfModulesDependencies
            )
        }
        resultOfModulesDependencies.entries.sortedByDescending { it.value }.forEach {
            resultDependenciesInDepthText += "${it.key} - ${it.value}\n"
        }
        val sortedFile = File(graph.rootProject.projectDir.absolutePath + "/build", "sorted_dependencies_in_depths.json")
        sortedFile.parentFile.mkdirs()
        sortedFile.delete()
        sortedFile.writeText(resultDependenciesInDepthText)

        val json = Json { prettyPrint = true }
            .encodeToString(value = resultOfModulesDependencies.toSortedMap().toMap())
        val file = File(graph.rootProject.projectDir.absolutePath + "/graph_metrics", "alphabetic_dependencies_in_depths.json")
        file.parentFile.mkdirs()
        file.delete()
        file.writeText(json)
    }
}