package io.github.anelkad.dependencygraph.plugin.core

import io.github.anelkad.dependencygraph.plugin.DependencyPair
import io.github.anelkad.dependencygraph.plugin.ModuleProject
import io.github.anelkad.dependencygraph.plugin.ParsedGraph

internal fun getDependenciesInDepth(
    currentProject: ModuleProject,
    parsedGraph: ParsedGraph,
    resultOfModulesDependencies: MutableMap<String, Int> = mutableMapOf()
) {

    val dependencies: LinkedHashMap<DependencyPair, List<String>> =
        parsedGraph.dependencies

    val currentProjectDependencies =
        gatherDependencies(mutableListOf(currentProject), dependencies)

    val dependenciesModulesSet: MutableSet<String> = mutableSetOf()

    dependencies
        .filter { (key, _) ->
            val (origin, target) = key
            (currentProjectDependencies.contains(origin)) &&
                origin.path != target.path
        }
        .forEach { (key, _) ->
            val (_, target) = key
            dependenciesModulesSet.add(target.path)
        }

    resultOfModulesDependencies[currentProject.path] = dependenciesModulesSet.size
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


private fun ModuleProject.gatherDependents(
    dependencies: LinkedHashMap<DependencyPair, List<String>>,
) = dependencies
    .filter { (key, _) -> key.target == this }
    .map { (key, _) -> key.origin }
