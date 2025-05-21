package io.github.anelkad.dependencygraph.plugin.core

import io.github.anelkad.dependencygraph.plugin.DependencyPair
import io.github.anelkad.dependencygraph.plugin.ModuleProject
import io.github.anelkad.dependencygraph.plugin.ParsedGraph

internal fun getDependentsInDepth(
    currentProject: ModuleProject,
    parsedGraph: ParsedGraph,
    resultOfModulesDependents: MutableMap<String, Int> = mutableMapOf()
) {
    val dependencies: LinkedHashMap<DependencyPair, List<String>> =
        parsedGraph.dependencies

    val dependents = currentProject.gatherAllDependents(dependencies)

    val dependentModulesSet: MutableSet<String> = mutableSetOf()

    dependencies
        .filter { (key, _) ->
            val (origin, target) = key
            dependents.contains(origin) &&
                origin.path != target.path
        }
        .forEach { (key, _ ) ->
            val (origin, _ ) = key
            dependentModulesSet.add(origin.path)
        }

    if (dependentModulesSet.isNotEmpty()) {
        resultOfModulesDependents[currentProject.path] = dependentModulesSet.size
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