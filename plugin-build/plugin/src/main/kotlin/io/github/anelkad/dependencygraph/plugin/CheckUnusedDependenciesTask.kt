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
    val matchingPackageToModule: MutableMap<String, ModuleProject> = mutableMapOf()

    @Internal
    var logs = ""

    @TaskAction
    fun check() {
        val graph = parsedGraph.get()
        val dependencies: LinkedHashMap<DependencyPair, List<String>> =
            graph.dependencies

        val searchInDepth = graph.searchInDepth

        val matchingModulesWithPackage: MutableMap<ModuleProject, MutableSet<String>> =
            mutableMapOf()
        getMatchingModuleToPackage(
            graph = graph,
            result = matchingModulesWithPackage,
        )

        matchingModulesWithPackage.entries.forEach { (module, packages) ->
            packages.forEach { pack ->
                matchingPackageToModule.set(key = pack, value = module)
            }
        }

        val file = File(graph.rootProject.projectDir.path + "/build", "matchingPackageToModule.txt")
        file.parentFile.mkdirs()
        file.delete()
        file.writeText(matchingPackageToModule.entries.joinToString { "${it.key} -> ${it.value}\n" })

        val modulesDependencyToWarning = graph.modulesDependencyToWarning

        val useCaseAllowedImportedPackage: MutableMap<String, MutableSet<String>> = mutableMapOf()
        val moduleAllowedImportedModules: MutableMap<String, MutableSet<String>> = mutableMapOf()

        val modulesUnusedDependency = mutableMapOf<String, MutableSet<String>>()
        val modulesUnusedDependencyWarning = mutableMapOf<String, MutableSet<String>>()

        graph.projects.forEach { project ->
            val currentProjectDependencies = gatherDependencies(
                currentProject = project,
                currentProjectAndDependencies = mutableListOf(project),
                dependencies = dependencies
            )
            currentProjectDependencies.remove(project)
            if (searchInDepth.any { project.path.startsWith(it) }) {
                logs += "\nsearching in ${project.path}"
                val foundUseCases = mutableListOf<FoundClass>()
                val foundBaseClasses = mutableListOf<FoundClass>()
                findUseCasesAndBaseClasses(
                    currentProject = project,
                    useCases = foundUseCases,
                    baseClasses = foundBaseClasses
                )
                foundUseCases.forEach { useCase ->
                    logs += "\n - in usecase $useCase"
                    val fromModule = matchingPackageToModule.entries.maxBy {
                        it.key.plus('.').commonPrefixWith(useCase.packageName).length
                    }
                    logs += "\n - usecase from module ${fromModule?.value?.path}"
                    fromModule?.let { module ->
                        val foundUsedModules = findUsedClassesOfFile(
                            currentProject = module.value,
                            useCaseName = useCase.className,
                            currentProjectPackage = module.key
                        ).map { it.packageName }
                        if (useCaseAllowedImportedPackage[useCase.packageName + useCase.className] == null) {
                            useCaseAllowedImportedPackage[useCase.packageName + useCase.className] =
                                mutableSetOf()
                        }
                        useCaseAllowedImportedPackage[useCase.packageName + useCase.className]?.addAll(
                            foundUsedModules,
                        )
                    } ?: println("qwerty! $useCase not found!!")
                    useCaseAllowedImportedPackage[useCase.packageName + useCase.className]?.forEach { packageName ->
                        if (moduleAllowedImportedModules[project.path] == null) {
                            moduleAllowedImportedModules[project.path] = mutableSetOf()
                        }
                        val modulePath = getModuleByPackage(packageName)
                        modulePath?.let {
                            moduleAllowedImportedModules[project.path]?.add(it.path)
                        }
                    }
                }
                foundBaseClasses.forEach { baseClass ->
                    logs += "\n - in baseClass $baseClass"
                    val fromModule = matchingPackageToModule.entries.maxBy {
                        it.key.plus('.').commonPrefixWith(baseClass.packageName).length
                    }
                    logs += "\n - baseClass from module ${fromModule?.value?.path}"
                    fromModule?.let { module ->
                        val foundUsedModules = findUsedClassesOfFile(
                            currentProject = module.value,
                            useCaseName = baseClass.className,
                            currentProjectPackage = module.key,
                            allowCurrentProject = true
                        ).map { it.packageName }
                        if (useCaseAllowedImportedPackage[baseClass.packageName + baseClass.className] == null) {
                            useCaseAllowedImportedPackage[baseClass.packageName + baseClass.className] =
                                mutableSetOf()
                        }
                        useCaseAllowedImportedPackage[baseClass.packageName + baseClass.className]?.addAll(
                            foundUsedModules,
                        )
                    } ?: println("qwerty! $baseClass not found!!")
                    useCaseAllowedImportedPackage[baseClass.packageName + baseClass.className]?.forEach { packageName ->
                        if (moduleAllowedImportedModules[project.path] == null) {
                            moduleAllowedImportedModules[project.path] = mutableSetOf()
                        }
                        val modulePath = getModuleByPackage(packageName)
                        modulePath?.let {
                            moduleAllowedImportedModules[project.path]?.add(it.path)
                        }
                    }
                }
            }

            currentProjectDependencies.forEach { dependency ->
                if (
                    !usesDependencies(
                        currentProject = project,
                        packages = matchingModulesWithPackage[dependency] ?: emptySet(),
                    )
                    && project.path != ":app"
                ) {
                    val allowedImports = moduleAllowedImportedModules[project.path] ?: emptySet()
                    if (dependency.path !in allowedImports)  {
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
            logs += "\n----end-----"
        }

        if (useCaseAllowedImportedPackage.isNotEmpty()) {
            val file = File(
                graph.rootProject.projectDir.path + "/build",
                "useCaseAllowedImportedPackage.txt",
            )
            file.parentFile.mkdirs()
            file.delete()
            file.writeText(useCaseAllowedImportedPackage.entries.joinToString { "${it.key} -> ${it.value}\n" })
        }

        if (moduleAllowedImportedModules.isNotEmpty()) {
            val file = File(
                graph.rootProject.projectDir.path + "/build",
                "moduleAllowedImportedModules.txt",
            )
            file.parentFile.mkdirs()
            file.delete()
            file.writeText(moduleAllowedImportedModules.entries.joinToString { "${it.key} -> ${it.value}\n" })
        }

        if (logs.isNotEmpty()) {
            val file = File(graph.rootProject.projectDir.path + "/build", "checkUnusedDependenciesLogs.txt",)
            file.parentFile.mkdirs()
            file.delete()
            file.writeText(logs)
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

    private fun getModuleByPackage(
        packageName: String
    ): ModuleProject {
        val module = matchingPackageToModule.entries.maxBy {
            packageName.commonPrefixWith(it.key.plus(".")).length
        }
        return module.value
    }

    private fun getMatchingModuleToPackage(
        graph: ParsedGraph,
        result: MutableMap<ModuleProject, MutableSet<String>>
    ) {
        val androidProjects = graph.androidProjects
        val androidModulePaths = androidProjects.associate { it.path to it.namespace }

        var matchingModulesWithPackage = ""
        graph.projects.forEach { project ->
            val modulePackagesSet = mutableSetOf<String>()
            if (project.path in androidModulePaths.keys) {
                androidModulePaths[project.path]?.let { namespace ->
                    modulePackagesSet.add(namespace)
                }
            }
            getModulePackageName(
                currentProject = project,
                commonDirs = graph.commonDirs,
                result = modulePackagesSet,
            )
            matchingModulesWithPackage += "\n${project.path} - $modulePackagesSet"
            result[project] = modulePackagesSet.sortedBy { it.length }.toMutableSet()
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

    private fun findUsedClassesOfFile(
        currentProject: ModuleProject,
        useCaseName: String,
        currentProjectPackage: String,
        allowCurrentProject: Boolean = false
    ): List<FoundClass> {
        val srcDirs = listOf(
            "src/main/java",
            "src/main/kotlin"
        ).map { File(currentProject.projectDir, it) }.filter { it.exists() }

        val findClassesRegex = Regex("""^import\s+([a-zA-Z0-9_.]+)\.model(s)?\.([A-Z][A-Za-z0-9_]*)$""")
        val findDelegatesRegex = Regex("""^import\s+([a-zA-Z0-9_.]+)\.([A-Z][A-Za-z0-9_]*(Delegate|Updater))$""")

        val result = mutableListOf<FoundClass>()

        srcDirs.forEach { srcDir ->
            srcDir.walkTopDown()
                .filter {
                    it.isFile && (it.extension == "kt") && it.name == "$useCaseName.kt"
                }
                .forEach { file ->
                    logs += "\n --- found file $useCaseName.kt in $srcDir"
                    logs += "\n --- currentProjectPackage $currentProjectPackage"
                    logs += "\n --- allowCurrentProject = $allowCurrentProject"
                    file.forEachLine { line ->
                        val matchClasses = findClassesRegex.find(line)
                        if (matchClasses != null) {
                            val packageName = matchClasses.groupValues[1]
                            val className = matchClasses.value.substringAfterLast(".")

                            if (allowCurrentProject || !packageName.startsWith("$currentProjectPackage.")) {
                                logs += "\n --- found model $className with package $packageName"
                                result.add(FoundClass(className = className, packageName = packageName))
                                val module = getModuleByPackage(packageName)
                                val classResult = findImportsOfModelClassFile(
                                    currentProject = module,
                                    className = className,
                                    currentProjectPackage = packageName,
                                )
                                result.addAll(classResult)
                            }
                        }
                        val matchDelegates = findDelegatesRegex.find(line)
                        if (matchDelegates != null) {
                            val packageName = matchDelegates.groupValues[1]
                            val className = matchDelegates.value.substringAfterLast(".")
                            logs += "\n --- found delegate $className with package $packageName"
                            result.add(
                                FoundClass(
                                    packageName = packageName,
                                    className = className,
                                ),
                            )
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

        val findClassesRegex = Regex("""^import\s+([a-zA-Z0-9_.]+)\.model(s)?\.([A-Z][A-Za-z0-9_]*)$""")
        val result = mutableListOf<FoundClass>()

        srcDirs.forEach { srcDir ->
            srcDir.walkTopDown()
                .filter {
                    it.isFile && (it.extension == "kt") && it.name == "$className.kt"
                }
                .forEach { file ->
                    logs += "\n ---- found file $className.kt in $srcDir"
                    logs += "\n ---- currentProjectPackage $currentProjectPackage"
                    file.forEachLine { line ->
                        val match = findClassesRegex.find(line)
                        if (match != null) {
                            val packageName = match.groupValues[1]
                            val className1 = match.value.substringAfterLast(".")
                            if (!packageName.startsWith("$currentProjectPackage.")) {
                                logs += "\n ---- found model $className1 with package $packageName"
                                result.add(
                                    FoundClass(
                                        packageName = packageName,
                                        className = className1
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

    private fun findUseCasesAndBaseClasses(
        currentProject: ModuleProject,
        useCases: MutableList<FoundClass>,
        baseClasses: MutableList<FoundClass>
    ) {
        val srcDirs = listOf(
            "src/main/java",
            "src/main/kotlin"
        ).map { File(currentProject.projectDir, it) }.filter { it.exists() }

        val usedUseCasesRegex = Regex("""^\s*import\s+([a-zA-Z0-9_.]+)\.(\w*UseCase(?:Impl)?)""")
        val baseClassRegex = Regex("""^import\s+([a-zA-Z0-9_.]*\.base\.[a-zA-Z0-9_.]*)\.([A-Z][A-Za-z0-9_]*)$""")

        srcDirs.forEach { srcDir ->
            srcDir.walkTopDown()
                .filter { it.isFile && (it.extension == "kt") }
                .forEach { file ->
                    file.forEachLine { line ->
                        val matchUseCase = usedUseCasesRegex.find(line)
                        if (matchUseCase != null) {
                            val packageName = matchUseCase.groupValues[1]
                            val className = matchUseCase.groupValues[2]
                            useCases.add(FoundClass(className = className, packageName = packageName))
                        }
                        val matchBaseClass = baseClassRegex.find(line)
                        if (matchBaseClass != null) {
                            val packageName = matchBaseClass.groupValues[1]
                            val className = matchBaseClass.value.substringAfterLast('.')
                            baseClasses.add(FoundClass(className = className, packageName = packageName))
                        }
                    }
                }
        }
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
                    }.minByOrNull { it.length }
                    ?.let {
                        result.add(it)
                    }
            }
    }
}