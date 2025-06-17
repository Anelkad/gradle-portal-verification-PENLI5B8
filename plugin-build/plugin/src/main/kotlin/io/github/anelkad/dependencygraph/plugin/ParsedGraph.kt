package io.github.anelkad.dependencygraph.plugin

import org.gradle.api.Project
import java.io.File

internal data class ParsedGraph(
    val projects: LinkedHashSet<ModuleProject>,
    val dependencies: LinkedHashMap<DependencyPair, List<String>>,
    val externalDependencies: LinkedHashMap<ExternalDependencyPair, List<String>> = linkedMapOf(),
    val ignoredExternalDependencies: List<String> = emptyList(),
    val triggerModuleNames: List<String> = emptyList(),
    val graphModuleGroupNames: List<String> = emptyList(),
    val androidProjectsEnabledResources: List<ModuleProject> = emptyList(),
    val commonDirs: List<String> = emptyList(),
    val modulesDependencyToWarning: List<String> = emptyList(),
    val multiplatformProjects: List<ModuleProject>,
    val androidProjects: List<ModuleProject>,
    val javaProjects: List<ModuleProject>,
    val rootProjects: List<ModuleProject>,
    val rootProject: ModuleProject,
)

internal data class DependencyPair(
    val origin: ModuleProject,
    val target: ModuleProject,
)

internal data class ExternalDependencyPair(
    val origin: ModuleProject,
    val target: String,
)

internal data class ModuleProject(
    val path: String,
    val projectDir: File,
    val namespace: String? = null
)

internal fun Project.asModuleProject() = ModuleProject(
    path = this.path,
    projectDir = this.projectDir
)

internal fun Project.asModuleProject(namespace: String) = ModuleProject(
    path = this.path,
    projectDir = this.projectDir,
    namespace = namespace
)