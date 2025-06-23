package io.github.anelkad.dependencygraph.plugin

import io.github.anelkad.dependencygraph.plugin.core.parseDependencyGraph
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("UndocumentedPublicClass")
abstract class DependencyGraphPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Add the extension object
        val extension = project.extensions.create(
            // name =
            EXTENSION_NAME,
            // type =
            DependencyGraphExtension::class.java,
            // ...constructionArguments =
            project,
        )

        // Add a task that uses configuration from the extension object
        project.tasks.register(GRAPH_TASK_NAME, DependencyGraphTask::class.java) {
            it.graphFileName.set(extension.graphFileName)
            it.mainBranchName.set(extension.mainBranchName)
            it.repoRootUrl.set(extension.repoRootUrl)
            it.graphDirection.set(extension.graphDirection)
            it.showLegend.set(extension.showLegend)
            it.shouldLinkModuleText.set(extension.shouldLinkModuleText)
            it.shouldGroupModules.set(extension.shouldGroupModules)

            it.parsedGraph.set(
                parseDependencyGraph(
                    rootProject = project.rootProject,
                    ignoredModules = extension.ignoreModules.orNull ?: emptyList(),
                ),
            )
        }

        // Add a task that uses configuration from the extension object
        project.tasks.register(METRICS_TASK_NAME, DependencyMetricsTask::class.java) {
            it.graphFileName.set(extension.graphFileName)
            it.mainBranchName.set(extension.mainBranchName)
            it.repoRootUrl.set(extension.repoRootUrl)
            it.shouldLinkModuleText.set(extension.shouldLinkModuleText)
            it.shouldGroupModules.set(extension.shouldGroupModules)

            it.parsedGraph.set(
                parseDependencyGraph(
                    rootProject = project.rootProject,
                    ignoredModules = extension.ignoreModules.orNull ?: emptyList(),
                ),
            )
        }

        project.tasks.register(CHECK_KOTLIN_MODULE, CheckKotlinModuleTask::class.java) {
            it.parsedGraph.set(
                parseDependencyGraph(
                    rootProject = project.rootProject,
                    ignoredModules = extension.ignoreModules.orNull ?: emptyList(),
                    ignoredExternalDependencies = extension.ignoreExternalDependencies.orNull ?: emptyList()
                )
            )
        }

        project.tasks.register(CHECK_UNUSED_RESOURCES, CheckUnusedResourcesTask::class.java) {
            it.parsedGraph.set(
                parseDependencyGraph(
                    rootProject = project.rootProject,
                    ignoredModules = extension.ignoreModules.orNull ?: emptyList(),
                )
            )
        }

        project.tasks.register(CHECK_UNUSED_DEPENDENCIES, CheckUnusedDependenciesTask::class.java) {
            it.parsedGraph.set(
                parseDependencyGraph(
                    rootProject = project.rootProject,
                    ignoredModules = extension.ignoreModules.orNull ?: emptyList(),
                    commonDirs = extension.commonDirs.orNull ?: emptyList(),
                    modulesDependencyToWarning = extension.modulesDependencyToWarning.orNull ?: emptyList(),
                    searchInDepth = extension.searchInDepth.orNull ?: emptyList()
                )
            )
        }

        project.tasks.register(GRAPH_GV_TASK_NAME, DependencyGraphGVTask::class.java) {
            it.graphFileName.set(extension.graphFileName)
            it.mainBranchName.set(extension.mainBranchName)
            it.repoRootUrl.set(extension.repoRootUrl)
            it.graphDirection.set(extension.graphDirection)
            it.showLegend.set(extension.showLegend)
            it.shouldLinkModuleText.set(extension.shouldLinkModuleText)
            it.shouldGroupModules.set(extension.shouldGroupModules)

            it.parsedGraph.set(
                parseDependencyGraph(
                    rootProject = project.rootProject,
                    ignoredModules = extension.ignoreModules.orNull ?: emptyList(),
                    triggerModuleNames = extension.triggerModuleNames.orNull ?: emptyList(),
                    graphModuleGroupNames = extension.graphModuleGroupNames.orNull ?: emptyList(),
                ),
            )
        }
    }

    companion object {
        private const val EXTENSION_NAME = "dependencyGraphConfig"
        private const val GRAPH_TASK_NAME = "dependencyGraphMermaid"
        private const val METRICS_TASK_NAME = "dependencyMetrics"
        private const val CHECK_KOTLIN_MODULE = "checkKotlinModule"
        private const val GRAPH_GV_TASK_NAME = "dependencyGraph"
        private const val CHECK_UNUSED_RESOURCES = "checkUnusedResources"
        private const val CHECK_UNUSED_DEPENDENCIES = "checkUnusedDependencies"
    }
}