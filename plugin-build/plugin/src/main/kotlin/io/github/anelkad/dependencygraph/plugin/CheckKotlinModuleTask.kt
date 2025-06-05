package io.github.anelkad.dependencygraph.plugin

import io.github.anelkad.dependencygraph.plugin.core.getExternalDependencies
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
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

    sealed interface AndroidModuleStatus {
        data object StrictUseAndroid : AndroidModuleStatus // uses Context, resources, datastore
        data class HasAndroidDependency(val androidDependencies: List<Pair<String,AndroidModuleStatus>>): AndroidModuleStatus
        data object CanBeKotlinModule : AndroidModuleStatus
        data object CanBeKotlinModuleWithNoParcelize : AndroidModuleStatus
    }

    @Internal
    val modulesListCanBeKotlin = mutableMapOf<String, Boolean>()

    @TaskAction
    fun check() {
        val graph = parsedGraph.get()

        val androidProjects = graph.androidProjects

        val resultModulesStatus: MutableList<Pair<String, AndroidModuleStatus>> = mutableListOf()

        graph.projects.forEach {
            if (it in androidProjects) {
                resultModulesStatus.add(
                    checkIfDependenciesContainsAndroidModules(
                        graph = graph,
                        currentProject = it,
                    )
                )
            }
        }

        val file = File(graph.rootProject.projectDir.path +"/build", "resultModulesStatus.txt")
        file.parentFile.mkdirs()
        file.delete()
        file.writeText(resultModulesStatus.joinToString("\n"))

        resultModulesStatus.forEach { module ->
            when (val status = module.second) {
                AndroidModuleStatus.CanBeKotlinModule -> {
                    throw GradleException("${module.first} not contains android dependencies, make it kotlin module!")
                }
                AndroidModuleStatus.CanBeKotlinModuleWithNoParcelize -> {
                    project.logger.warn("⚠️ ${module.first} could be kotlin module if no parcelize use")
                }
                is AndroidModuleStatus.HasAndroidDependency -> {
                    status.androidDependencies.forEach {
                        canBeKotlinModule(it)
                    }
                }
                AndroidModuleStatus.StrictUseAndroid -> Unit
            }
        }

        val modulesKotlinWithNoParcelize = resultModulesStatus
            .filter { it.second == AndroidModuleStatus.CanBeKotlinModuleWithNoParcelize }
            .map { it.first }

        modulesListCanBeKotlin
            .filter {
                it.key !in modulesKotlinWithNoParcelize
            }
            .forEach {
                if (it.value) {
                    project.logger.warn("⚠️ ${it.key} has android dependencies can be kotlin if change its dependencies")
                }
            }
    }

    fun canBeKotlinModule(pair: Pair<String, AndroidModuleStatus>): Boolean {
        val moduleName = pair.first
        val currentStatus = pair.second

        val containsInCached = modulesListCanBeKotlin.get(moduleName)
        containsInCached?.let { return it }

        val result = when (currentStatus) {
            is AndroidModuleStatus.CanBeKotlinModule -> {
                project.logger.warn("⚠️ ${moduleName} not contains android dependencies, make it kotlin module!")
                true
            }
            is AndroidModuleStatus.HasAndroidDependency -> currentStatus.androidDependencies.all { (name, status) ->
                canBeKotlinModule(name to status)
            }
            is AndroidModuleStatus.StrictUseAndroid -> false
            is AndroidModuleStatus.CanBeKotlinModuleWithNoParcelize -> {
                project.logger.warn("⚠️ ${moduleName} could be kotlin module if no parcelize use")
                true
            }
        }
        modulesListCanBeKotlin[moduleName] = result
        return result
    }


    private fun checkIfDependenciesContainsAndroidModules(
        graph: ParsedGraph,
        currentProject: ModuleProject
    ): Pair<String, AndroidModuleStatus> {
        val dependencies: LinkedHashMap<DependencyPair, List<String>> =
            graph.dependencies

        val androidProjects = graph.androidProjects
        val currentProjectDependencies =
            gatherDependencies(mutableListOf(currentProject), dependencies)

        currentProjectDependencies.remove(currentProject)

        val importedAndroidProjects = currentProjectDependencies.filter { it in androidProjects }
        val ignoredExternalDependencies = graph.ignoredExternalDependencies
        val androidExternalDependencies = getExternalDependencies(
            currentProject = currentProject,
            parsedGraph = graph
        ).filter { dependency -> dependency.contains("android")
            && !ignoredExternalDependencies
            .map { it.split(".").lastOrNull() ?: "qwerty" }
            .any { dependency.contains(it) }
        }
        val androidImports = usedAndroidImports(
            currentProject = currentProject,
            ignoredExternalDependencies = ignoredExternalDependencies
        )

        val containsOnlyParcelableImports = androidImports.all { it.contains("android.os.Parcel") }

        val resDir = File(currentProject.projectDir, "src/main/res")
        val hasAndroidResources = resDir.exists() && resDir.walkTopDown().any { it.isFile }

        val status: AndroidModuleStatus = when {
            hasAndroidResources || androidExternalDependencies.isNotEmpty() -> {
//                println("currentProject: ${currentProject.path} is strict use android")
//                println(currentProject.path +" androidExternalDependencies: " + androidExternalDependencies)
                AndroidModuleStatus.StrictUseAndroid
            }
            importedAndroidProjects.isEmpty() && androidImports.isEmpty() -> {
//                project.logger.warn("⚠️ ${currentProject.path} not contains android dependencies, make it kotlin module!")
                AndroidModuleStatus.CanBeKotlinModule
            }
            importedAndroidProjects.isEmpty() && containsOnlyParcelableImports && androidImports.isNotEmpty() -> {
//                project.logger.warn("⚠️ ${currentProject.path} could be kotlin module if no parcelize use")
                AndroidModuleStatus.CanBeKotlinModuleWithNoParcelize
            }
            androidImports.isNotEmpty() && !containsOnlyParcelableImports  -> {
//                println(currentProject.path +" androidImports: " + androidImports)
                AndroidModuleStatus.StrictUseAndroid
            }
            else -> {
//                project.logger.warn("⚠️ ${currentProject.path} has android dependencies can be kotlin if change its dependencies")
                AndroidModuleStatus.HasAndroidDependency(
                    importedAndroidProjects.map {
                        checkIfDependenciesContainsAndroidModules(
                            graph = graph,
                            currentProject = it,
                        )
                    }
                )
            }
        }

        return currentProject.path to status
    }
}

private fun usedAndroidImports(
    currentProject: ModuleProject,
    ignoredExternalDependencies: List<String>
): Set<String> {
    val srcDirs = listOf(
        "src/main/java",
        "src/main/kotlin",
        "src/gms/kotlin",
        "src/gms/java",
        "src/hms/kotlin",
        "src/hms/java",
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
                    if (import != null && !ignoredExternalDependencies
                            .map { it.split(".").lastOrNull() ?: "qwerty" }
                            .any { import.contains(it) }
                    ) {
                        if (import.contains("android"))
                         {
                            androidImports.add(import)
                        }
                    }
                }
            }
    }
    return androidImports
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