package io.github.anelkad.dependencygraph.plugin

import org.gradle.api.Project
import java.io.File

internal data class ParsedGraph(
    val projects: LinkedHashSet<ModuleProject>,
    val dependencies: LinkedHashMap<DependencyPair, List<String>>,
    val externalDependencies: LinkedHashMap<ExternalDependencyPair, List<String>> = linkedMapOf(),
    val ignoredExternalDependencies: List<String> = emptyList(),
    val triggerModuleNames: List<String> = emptyList(),
    val androidProjectsEnabledResources: List<ModuleProject> = emptyList(),
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
)

internal fun Project.asModuleProject() = ModuleProject(this.path, this.projectDir)