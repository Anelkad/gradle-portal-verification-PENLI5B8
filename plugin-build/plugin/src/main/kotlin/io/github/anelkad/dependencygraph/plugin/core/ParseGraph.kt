package io.github.anelkad.dependencygraph.plugin.core

import com.android.build.gradle.LibraryExtension
import io.github.anelkad.dependencygraph.plugin.DependencyPair
import io.github.anelkad.dependencygraph.plugin.ExternalDependencyPair
import io.github.anelkad.dependencygraph.plugin.ParsedGraph
import io.github.anelkad.dependencygraph.plugin.asModuleProject
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ProjectDependency
import java.util.*

@Suppress("LongMethod", "CyclomaticComplexMethod", "CognitiveComplexMethod")
/**
 * Create a graph of all project modules, their types, dependencies and root projects.
 * @return An object of type GraphDetails containing all details
 */
internal fun parseDependencyGraph(
    rootProject: Project,
    ignoredModules: List<String>,
    ignoredExternalDependencies: List<String> = emptyList(),
    triggerModuleNames: List<String> = emptyList(),
): ParsedGraph {
    val rootProjects = mutableListOf<Project>()
    var queue = mutableListOf(rootProject)

    // Traverse the list of all sub-folders starting with root project and add them to
    // rootProjects
    while (queue.isNotEmpty()) {
        val project = queue.removeAt(0)
        if (project.path !in ignoredModules) {
            rootProjects.add(project)
        }
        queue.addAll(project.childProjects.values)
    }

    val projects = LinkedHashSet<Project>()
    val dependencies = LinkedHashMap<DependencyPair, List<String>>()
    val externalDependencies = LinkedHashMap<ExternalDependencyPair, List<String>>()
    val multiplatformProjects = mutableListOf<Project>()
    val androidProjects = mutableListOf<Project>()
    val androidProjectsEnabledResources = mutableListOf<Project>()
    val javaProjects = mutableListOf<Project>()

    // Again traverse the list of all sub-folders starting with the current project
    // * Add projects to project-type lists
    // * Add project dependencies to dependency hashmap with record for api/impl
    // * Add projects & their dependencies to projects list
    // * Remove any dependencies from rootProjects list
    queue = mutableListOf(rootProject)
    while (queue.isNotEmpty()) {
        val project = queue.removeAt(0)
        if (project.path in ignoredModules) {
            continue
        }
        queue.addAll(project.childProjects.values)

        if (project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
            multiplatformProjects.add(project)
        }
        if (
            project.plugins.hasPlugin("com.android.library") ||
            project.plugins.hasPlugin("com.android.application")
        ) {
            androidProjects.add(project)
            project.plugins.withId("com.android.library") {
                val androidExtension = project.extensions.findByType(LibraryExtension::class.java)
                val androidResourcesEnabled = androidExtension?.buildFeatures?.androidResources != false
                if (androidResourcesEnabled) {
                    androidProjectsEnabledResources.add(project)
                }
            }
        }
        if (
            project.plugins.hasPlugin("java-library") ||
            project.plugins.hasPlugin("java") ||
            project.plugins.hasPlugin("org.jetbrains.kotlin.jvm")
        ) {
            javaProjects.add(project)
        }

        project.configurations.configureEach { config ->
            config.dependencies
                .filterIsInstance<ProjectDependency>()
                .map { it.dependencyProject }
                .filter { it.path !in ignoredModules }
                .forEach { dependency ->
                    projects.add(project)
                    projects.add(dependency)
                    if (
                        project.path !in ignoredModules &&
                        project.path != dependency.path
                    ) {
                        rootProjects.remove(dependency)
                    }

                    val graphKey =
                        DependencyPair(project.asModuleProject(), dependency.asModuleProject())
                    val traits = dependencies
                        .getOrPut(graphKey) { mutableListOf() } as MutableList

                    if (config.name.lowercase(Locale.getDefault()).endsWith("api")) {
                        traits.add("api")
                    } else {
                        traits.add("impl")
                    }
                }
        }

        projects.forEach { project ->
            project.configurations
                .filter {
                    it.name == "implementation" ||
                        it.name == "api"
                }
                .forEach {
                    it.dependencies
                        .filterIsInstance<ExternalDependency>()
                        .filter {
                            it.group !in ignoredExternalDependencies
                        }
                        .forEach { dependency ->
                            val graphKey = ExternalDependencyPair(
                                project.asModuleProject(),
                                dependency.group + ":" + dependency.name,
                            )
                            val traits = externalDependencies
                                .getOrPut(graphKey) { mutableListOf() } as MutableList
                            traits.add(dependency.group + ":" + dependency.name)
                        }
                }
        }
    }

    // Collect leaf projects which may be denoted with a different shape or rank
    val leafProjects = mutableListOf<Project>()
    projects.forEach {
        val allDependencies = it.configurations
            .map { config ->
                config.dependencies
                    .filterIsInstance<ProjectDependency>()
                    .filter { dependency ->
                        dependency.dependencyProject.path != it.path
                    }
            }

        if (allDependencies.isEmpty()) {
            leafProjects.add(it)
        } else {
            leafProjects.remove(it)
        }
    }

    return ParsedGraph(
        projects = LinkedHashSet(projects.map { it.asModuleProject() }.sortedBy { it.path }),
        dependencies = dependencies,
        externalDependencies = externalDependencies,
        ignoredExternalDependencies = ignoredExternalDependencies,
        triggerModuleNames = triggerModuleNames,
        multiplatformProjects = multiplatformProjects.map { it.asModuleProject() },
        androidProjects = androidProjects.map { it.asModuleProject() },
        androidProjectsEnabledResources = androidProjectsEnabledResources.map { it.asModuleProject() },
        javaProjects = javaProjects.map { it.asModuleProject() },
        rootProjects = rootProjects.map { it.asModuleProject() },
        rootProject = rootProject.asModuleProject(),
    )
}