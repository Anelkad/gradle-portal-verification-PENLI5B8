package io.github.anelkad.dependencygraph.plugin.core

import io.github.anelkad.dependencygraph.plugin.DependencyPair
import io.github.anelkad.dependencygraph.plugin.ModuleProject
import io.github.anelkad.dependencygraph.plugin.ParsedGraph
import java.io.File

@Suppress(
    "LongMethod",
    "CyclomaticComplexMethod",
    "CognitiveComplexMethod",
    "ktlint:standard:indent",
)
/**
 * Creates a graph of dependencies for the given project and writes it to a file in the project's
 * directory.
 */
internal fun drawDependencyGraphGV(
    currentProject: ModuleProject,
    parsedGraph: ParsedGraph,
    isRootGraph: Boolean,
    config: DrawConfig,
    graphModuleGroupNames: List<String>,
    triggerModuleNames: List<String>
) {
    val projects: LinkedHashSet<ModuleProject> = parsedGraph.projects
    val relevantProjects = projects.toList().map { it.path }

    val dependencies: LinkedHashMap<DependencyPair, List<String>> =
        parsedGraph.dependencies

    val currentProjectDependencies =
        gatherDependencies(mutableListOf(currentProject), dependencies)

    var fileText = """
        strict digraph DependencyGraph {
        ratio=0.6;
        node [shape=box fontsize=30 style=filled fillcolor="#B66FF5"];

        """.trimIndent()

    relevantProjects.forEach {
        val color = stringToHexColor(input = it, triggerModuleNames = triggerModuleNames)
        fileText += "\"${it}\" [style=filled fillcolor=\"$color\" ];\n"
    }

    dependencies
        .filter { (key, _) ->
            val (origin, target) = key
            (isRootGraph || currentProjectDependencies.contains(origin)) &&
                origin.path != target.path
        }
        .forEach { (key) ->
            val (origin, target) = key
            fileText += "\"${origin.path}\" -> \"${target.path}\";\n"
        }

    val graphFile = File(currentProject.projectDir.absolutePath + "/build", config.fileName)
    graphFile.parentFile.mkdirs()
    graphFile.delete()
    graphFile.writeText("$fileText}")

    println("Project module dependency GV graph created at ${graphFile.absolutePath}")
    if (graphModuleGroupNames.isNotEmpty()) {
        graphModuleGroupNames.forEach { graphModuleGroup ->
            drawGroupedModulesGraph(
                parsedGraph = parsedGraph,
                currentProject = currentProject,
                isRootGraph = isRootGraph,
                triggerModuleNames = triggerModuleNames,
                graphModuleGroup = graphModuleGroup
            )
        }
    }
}

private fun drawGroupedModulesGraph(
    parsedGraph: ParsedGraph,
    currentProject: ModuleProject,
    isRootGraph: Boolean,
    triggerModuleNames: List<String>,
    graphModuleGroup: String
) {
    val projects: LinkedHashSet<ModuleProject> = parsedGraph.projects
    val relevantProjects = projects.toList()
        .map { it.path }
        .filter { it.startsWith(graphModuleGroup) }

    val dependencies: LinkedHashMap<DependencyPair, List<String>> =
        parsedGraph.dependencies

    val currentProjectDependencies =
        gatherDependencies(mutableListOf(currentProject), dependencies)

    var fileText = """
        strict digraph DependencyGraph {
        ratio=0.6;
        node [shape=box fontsize=30 style=filled fillcolor="#B66FF5"];

        """.trimIndent()

    relevantProjects.forEach {
        val color = stringToHexColor(input = it, triggerModuleNames = triggerModuleNames)
        fileText += "\"${it}\" [style=filled fillcolor=\"$color\" ];\n"
    }

    dependencies
        .filter { (key, _) ->
            val (origin, target) = key
            (isRootGraph || currentProjectDependencies.contains(origin)) &&
                origin.path != target.path
                && origin.path.startsWith(graphModuleGroup)
                && target.path.startsWith(graphModuleGroup)
        }
        .forEach { (key) ->
            val (origin, target) = key
            fileText += "\"${origin.path}\" -> \"${target.path}\";\n"
        }

    val graphFile = File(currentProject.projectDir.absolutePath + "/build", "graph$graphModuleGroup.txt")
    graphFile.parentFile.mkdirs()
    graphFile.delete()
    graphFile.writeText("$fileText}")
    println("Project module dependency GV graph created at ${graphFile.absolutePath}")
}

private fun stringToHexColor(
    input: String,
    triggerModuleNames: List<String>
): String {
    val triggerName = triggerModuleNames.find { input.contains(it) }
    val moduleGroup = input.removeRange(0, 1).substringBefore(":")
    val hash = (triggerName?.let { moduleGroup + triggerName } ?: moduleGroup).hashCode()
    var r = (hash shr 16 and 0xFF)
    var g = (hash shr 8 and 0xFF)
    var b = (hash and 0xFF)

    val lightenFactor = 0.5f
    r = (r + ((255 - r) * lightenFactor)).toInt().coerceIn(0, 255)
    g = (g + ((255 - g) * lightenFactor)).toInt().coerceIn(0, 255)
    b = (b + ((255 - b) * lightenFactor)).toInt().coerceIn(0, 255)

    return String.format("#%02X%02X%02X", r, g, b)
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

private fun ModuleProject.gatherAllDependents(
    dependencies: LinkedHashMap<DependencyPair, List<String>>,
    visited: MutableSet<ModuleProject> = mutableSetOf(),
): Set<ModuleProject> {
    if (!visited.add(this)) return emptySet() // Avoid infinite loops

    val directDependents = dependencies
        .filter { (key, _) -> key.target == this }
        .map { (key, _) -> key.origin }
        .toSet()

    return directDependents + directDependents.flatMap { it.gatherAllDependents(dependencies, visited) }
}