package io.github.anelkad.dependencygraph.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File

abstract class CheckUnusedDependenciesTask : DefaultTask() {
    init {
        group = BasePlugin.BUILD_GROUP
        description = "Check for unused dependencies of modules"
    }

    /** The project dependencies graph as [ParsedGraph]`. */
    @get:Input
    @get:Option(
        option = "graphDetails",
        description = "The project dependencies graph as [ParsedGraph]",
    )
    internal abstract val parsedGraph: Property<ParsedGraph>

    @Internal
    internal val matchingPackageToModule: MutableMap<String, ModuleProject> = mutableMapOf()

    @TaskAction
    fun check() {
        val graph = parsedGraph.get()
        val dependencies: LinkedHashMap<DependencyPair, List<String>> =
            graph.dependencies

        val matchingModulesWithPackage: MutableMap<ModuleProject, MutableSet<String>> = mutableMapOf()
        getMatchingModuleToPackage(
            graph = graph,
            result = matchingModulesWithPackage,
        )

        matchingModulesWithPackage.entries.forEach { (module, packages) ->
            packages.forEach { pack ->
                matchingPackageToModule.set(key = pack, value = module)
            }
        }

        val modulesDependencyToWarning = graph.modulesDependencyToWarning

        val useCaseAllowedImportedModules: MutableMap<String, MutableSet<String>> = mutableMapOf()

        val modulesUnusedDependency = mutableMapOf<String, MutableSet<String>>()
        val modulesUnusedDependencyWarning = mutableMapOf<String, MutableSet<String>>()

        graph.projects.forEach { project ->
            val currentProjectDependencies = gatherDependencies(
                currentProject = project,
                currentProjectAndDependencies = mutableListOf(project),
                dependencies = dependencies
            )
            currentProjectDependencies.remove(project)

            currentProjectDependencies.forEach { dependency ->
                if (
                    !usesDependencies(
                        currentProject = project,
                        packages = matchingModulesWithPackage[dependency] ?: emptySet(),
                    )
                    && project.path != ":app"
                ) {

                    val searchInDepth = listOf(":feature", ":plugin")
                    var isAllowed = false
                    if (searchInDepth.any { project.path.startsWith(it) }) {
                        val foundUseCases = findUseCases(dependency)
                        foundUseCases.forEach { useCase ->
                            val useCaseAllowedModule = useCaseAllowedImportedModules[useCase.className]
                            if (useCaseAllowedModule == null) {
                                val fromModule = matchingPackageToModule.entries.find {
                                    it.key.startsWith(useCase.packageName)
                                }
                                fromModule?.let { module ->
                                    val foundUsedModules = findImportsOfUseCaseFile(
                                        currentProject = module.value,
                                        useCaseName = useCase.className,
                                        currentProjectPackage = module.key
                                    ).map { it.packageName }
                                    if (
                                        foundUsedModules.any {
                                            it.startsWith(
                                                matchingModulesWithPackage[dependency]?.first()
                                                    ?: "none"
                                            )
                                        }
                                    ) {
                                        isAllowed = true
                                    }
                                    if (isAllowed) {
                                        if (useCaseAllowedImportedModules[useCase.className] == null) {
                                            useCaseAllowedImportedModules[useCase.className] =
                                                mutableSetOf()
                                        }
                                        useCaseAllowedImportedModules[useCase.className]?.addAll(
                                            foundUsedModules
                                        )
                                    }
                                }
                            } else {
                                if (
                                    useCaseAllowedModule.any {
                                        it.startsWith(
                                            matchingModulesWithPackage[dependency]?.first()
                                                ?: "none",
                                        )
                                    }
                                ) {
                                    isAllowed = true
                                }
                            }
                        }
                    }
                    if (isAllowed) return

                    if (dependency.path in modulesDependencyToWarning) {
                        if (modulesUnusedDependencyWarning[project.path] == null) {
                            modulesUnusedDependencyWarning[project.path] = mutableSetOf()
                        }
                        modulesUnusedDependencyWarning[project.path]?.add(dependency.path)
                    } else {
                        if (modulesUnusedDependency[project.path] == null) {
                            modulesUnusedDependency[project.path] = mutableSetOf()
                        }
                        modulesUnusedDependency[project.path]?.add(dependency.path)
                    }
                }
            }
        }

        if (modulesUnusedDependency.isNotEmpty()) {
            throw GradleException(
                "Modules with unused dependencies: \n${
                    modulesUnusedDependency.entries.joinToString("\n") { "${it.key} -> ${it.value}" }
                }",
            )
        }

        if (modulesUnusedDependencyWarning.isNotEmpty()) {
            modulesUnusedDependencyWarning.entries.forEach { (module, dependencies) ->
                project.logger.warn("⚠️ $module does not use directly dependencies: $dependencies")
            }
        }
    }

    private fun getMatchingModuleToPackage(
        graph: ParsedGraph,
        result: MutableMap<ModuleProject, MutableSet<String>>
    ) {
        val androidProjects = graph.androidProjects
        val androidModulePaths = androidProjects.associate { it.path to it.namespace }

        var matchingModulesWithPackage = ""
        graph.projects.forEach {
            val modulePackagesSet = mutableSetOf<String>()
            if (it.path in androidModulePaths.keys) {
                androidModulePaths[it.path]?.let { namespace ->
                    modulePackagesSet.add(namespace)
                }
            }
            getModulePackageName(
                currentProject = it,
                commonDirs = graph.commonDirs,
                result = modulePackagesSet,
            )
            matchingModulesWithPackage += "\n${it.path} - $modulePackagesSet"
            result[it] = modulePackagesSet
        }

        val file = File(graph.rootProject.projectDir.path + "/build", "matchingModulesWithPackage.txt")
        file.parentFile.mkdirs()
        file.delete()
        file.writeText(matchingModulesWithPackage)
    }

    private fun usesDependencies(
        currentProject: ModuleProject,
        packages: Set<String>
    ): Boolean {
        val srcDirs = listOf(
            "src/main/java",
            "src/main/kotlin",
            "src/gms/kotlin",
            "src/gms/java",
            "src/hms/kotlin",
            "src/hms/java",
            "src/main/res"
        ).map { File(currentProject.projectDir, it) }.filter { it.exists() }

        srcDirs.forEach { srcDir ->
            srcDir.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
                .forEach { file ->
                    var isUsed = false
                    file.forEachLine { line ->
                        if (packages.any { line.contains(it) }) {
                            isUsed = true
                        }
                    }
                    if (isUsed) return true
                }
        }
        return false
    }

    private fun findImportsOfUseCaseFile(
        currentProject: ModuleProject,
        useCaseName: String,
        currentProjectPackage: String
    ): List<FoundClass> {
        val srcDirs = listOf(
            "src/main/java",
            "src/main/kotlin"
        ).map { File(currentProject.projectDir, it) }.filter { it.exists() }

        val findClassesRegex = Regex("""^import\s+([\w.]*\.models?\.[\w.]*)\.([\w]+)$""")
        val findDelegatesRegex = Regex("""^import\s+([\w.]*\.delegate\.[\w.]*)\.([\w]+)$""")

        val result = mutableListOf<FoundClass>()

        srcDirs.forEach { srcDir ->
            srcDir.walkTopDown()
                .filter {
                    it.isFile && (it.extension == "kt") && it.name == useCaseName
                }
                .forEach { file ->
                    file.forEachLine { line ->
                        val matchClasses = findClassesRegex.matchEntire(line)
                        val matchDelegates = findDelegatesRegex.matchEntire(line)
                        if (matchClasses != null) {
                            val packageName = matchClasses.groupValues[1]
                            val className = matchClasses.groupValues[2]
                            if (!packageName.startsWith(currentProjectPackage)) {
                                val module = matchingPackageToModule.entries.find { packageName.startsWith(it.key) }
                                if (module != null) {
                                    val classResult = findImportsOfModelClassFile(
                                        currentProject = module.value,
                                        className = className,
                                        currentProjectPackage = currentProjectPackage
                                    )
                                    result.addAll(classResult)
                                }
                            }
                        }
                        if (matchDelegates != null) {
                            val packageName = matchDelegates.groupValues[1]
                            val className = matchDelegates.groupValues[2]
                            result.add(FoundClass(packageName = packageName, className = className))
                        }
                    }
                }
        }
        return result
    }

    private fun findImportsOfModelClassFile(
        currentProject: ModuleProject,
        className: String,
        currentProjectPackage: String
    ): List<FoundClass> {
        val srcDirs = listOf(
            "src/main/java",
            "src/main/kotlin"
        ).map { File(currentProject.projectDir, it) }.filter { it.exists() }

        val findClassesRegex = Regex("""^import\s+([\w.]*\.models?\.[\w.]*)\.([\w]+)$""")
        val result = mutableListOf<FoundClass>()

        srcDirs.forEach { srcDir ->
            srcDir.walkTopDown()
                .filter {
                    it.isFile && (it.extension == "kt") && it.name == className
                }
                .forEach { file ->
                    file.forEachLine { line ->
                        val match = findClassesRegex.matchEntire(line)
                        if (match != null) {
                            val packageName = match.groupValues[1]
                            val className = match.groupValues[2]
                            if (!packageName.startsWith(currentProjectPackage)) {
                                result.add(
                                    FoundClass(
                                        packageName = packageName,
                                        className = className
                                    )
                                )
                            }
                        }
                    }
                }
        }
        return result
    }

    private fun gatherDependencies(
        currentProject: ModuleProject,
        currentProjectAndDependencies: MutableList<ModuleProject>,
        dependencies: LinkedHashMap<DependencyPair, List<String>>,
    ): MutableList<ModuleProject> {
        dependencies
            .map { it.key }
            .forEach { (currProject, dependencyProject) ->
                if (
                    currentProject == currProject &&
                    !currentProjectAndDependencies.contains(dependencyProject)
                ) {
                    currentProjectAndDependencies.add(dependencyProject)
                }
            }
        return currentProjectAndDependencies
    }

    private fun findUseCases(
        currentProject: ModuleProject,
    ): List<FoundClass> {
        val srcDirs = listOf(
            "src/main/java",
            "src/main/kotlin"
        ).map { File(currentProject.projectDir, it) }.filter { it.exists() }

        val result = mutableListOf<FoundClass>()

        val usedUseCasesRegex = Regex("""^import\s+([\w.]+)\.(\w*UseCase(?:Impl)?)$""")

        srcDirs.forEach { srcDir ->
            srcDir.walkTopDown()
                .filter { it.isFile && (it.extension == "kt") }
                .forEach { file ->
                    file.forEachLine { line ->
                        val match = usedUseCasesRegex.matchEntire(line)
                        if (match != null) {
                            val packageName = match.groupValues[1]
                            val className = match.groupValues[2]
                            println("Path: $packageName")
                            println("Class: $className\n")
                            result.add(FoundClass(className = className, packageName = packageName))
                        }
                    }
                }
        }
        return result
    }

    private data class FoundClass(
        val className: String,
        val packageName: String
    )

    private fun getModulePackageName(
        currentProject: ModuleProject,
        result: MutableSet<String>,
        commonDirs: List<String> = emptyList()
    ) {
        val sourceDirs = listOf(
            "src/main/java",
            "src/main/kotlin",
            "src/gms/kotlin",
            "src/gms/java",
            "src/hms/kotlin",
            "src/hms/java",
        )
        sourceDirs
            .map { File(currentProject.projectDir, it) }
            .filter { it.exists() }
            .forEach { srcDir ->
                srcDir.walkTopDown()
                    .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                    .toList()
                    .map { path ->
                        var relativePath = path.relativeTo(currentProject.projectDir).path
                        val prefix = sourceDirs.find { relativePath.contains(it) }
                        prefix?.let {
                            relativePath = relativePath.removePrefix(it)
                        }
                        relativePath = relativePath
                            .substringBeforeLast("/")
                            .removePrefix("/")
                            .replace("/", ".")
                        while (commonDirs.any { relativePath.endsWith(".$it") }) {
                            val suffix = commonDirs.find { relativePath.endsWith(".$it") }
                            suffix?.let {
                                relativePath = relativePath.removeSuffix(".$suffix")
                            }
                        }
                        relativePath
                    }
                    .sortedBy { it.length }
                    .first()
                    .let {
                        result.add(it)
                    }
            }
    }
}