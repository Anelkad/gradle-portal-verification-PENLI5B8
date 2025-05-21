package io.github.anelkad.dependencygraph.plugin

import io.github.anelkad.dependencygraph.plugin.core.getDependentsInDepth
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
        var resultText = ""
        graph.projects.forEach {
            getDependentsInDepth(
                currentProject = it,
                parsedGraph = graph,
                resultOfModulesDependents = resultOfModulesDependents
            )
        }

        resultOfModulesDependents.entries.sortedByDescending { it.value }.forEach {
            resultText += "${it.key} - ${it.value}\n"
        }
        val graphFile = File(graph.rootProject.projectDir, "sorted_projects_dependents_in_depths.txt")
        graphFile.parentFile.mkdirs()
        graphFile.delete()
        graphFile.writeText(resultText)
    }
}