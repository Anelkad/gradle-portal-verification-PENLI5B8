# Android Gradle Graph Plugin
creator is @anel-kadyrova

### Многофункциональный плагин [io.github.anelkad.dependencygraph](https://plugins.gradle.org/plugin/io.github.anelkad.dependencygraph) создан чтобы мониторить зависимости проекта

все проверки происходят в stage `linter` CI/CD

### Основные таски:
1. `./gradlew checkUnusedDependencies --no-configuration-cache` - проверка на неиспользуемые зависимости в модулях
2. `./gradlew checkKotlinModule --no-configuration-cache` - проверка на отсутвие андроид зависимостей модуля и возможности переделать на котлин модуль
3. `./gradlew checkUnusedResources --no-configuration-cache` - проверка на неиспользуемые ресурсы в андроид модуле чтобы отключить генерацию ресурсов
4. `./gradlew dependencyMetrics --no-configuration-cache` - генерит json файл в graph_metrics с метриками связности (dependencies/dependents)
5. `./gradlew dependencyGraph --no-configuration-cache` - генерит .gv формат графа в build папке

### Настройка работы плагина

```kotlin
dependencyGraphConfig {
    ignoreModules.set(listOf(":libs:onefit-ktlint-rules"))
    ignoreExternalDependencies.set(listOf("androidx.annotation", "com.google.dagger", "androidx.compose"))
    triggerModuleNames.set(listOf(":models", ":components", ":common", ":api", ":impl"))
    commonDirs.set(
        listOf(
            "di",
            "ui",
            "data",
            "domain",
            "model",
            "base",
        ),
    )
    modulesDependencyToWarning.set(
        listOf(":libs:analytics") // ksp inject in BaseFragment
    )
    searchInDepth.set(listOf(":feature", ":plugin"))
    graphModuleGroupNames.set(listOf(":services"))
}
```

Описание экстеншенов:
- `ignoreModules` - модули которые игнорируются плагином
- `ignoreExternalDependencies` - юзается в checkKotlinModule, внешние зависимости которые не считаются как андроид зависимость
- `triggerModuleNames` - в dependencyGraph, в графе закрашиваются в разные цвета модули по группам из triggerModuleNames
- `commonDirs` - в checkUnusedDependencies чтобы определить package модуля по директориям
- `modulesDependencyToWarning` - в checkUnusedDependencies не фейлит таску зависимостей из списка
- `searchInDepth` - в checkUnusedDependencies смотрит в глубину зависимостей по делегатам/model/base классам из группы модулей в списке
- `graphModuleGroupNames` - в dependencyGraph генерит граф только выделенной группе

Генерируемые логи:
- `checkKotlinModule`
    - root/build/resultModulesStatus.txt - список модулей с их статусами (StrictUseAndroid, HasAndroidDependency, CanBeKotlinModule, CanBeKotlinModuleWithNoParcelize)
    - root/build/checkKotlinModuleLogs.txt - логи
- `checkUnusedDependencies`
    - root/build/matchingModulesWithPackage.txt - список соответствия модуля с package + namespace
    - root/build/matchingPackageToModule.txt - список соответствия package c модулем
    - root/build/checkUnusedDependenciesLogs.txt - логи
    - root/build/useCaseAllowedImportedPackage.txt - список юз кейсов и разрешаемых ими импортов (которые будут игнорироваться как неиспользуемые)
    - root/build/moduleAllowedImportedModules.txt - список модулей и разрешаемые зависимости модулей (которые игнорируются скриптом)
- `checkUnusedResources`
    - module/build/usingResourcesInFiles.txt - выводятся строки где юзается ресурс
- `dependencyMetrics`
    - root/build/sorted_dependents_in_depths.json - отсортированный список dependents_in_depths
    - root/build/sorted_dependencies_in_depths.json - отсортированный список dependencies_in_depths
    - root/build/sorted_projects_centrality.txt - отсортированный список у которых обе метрики высокие (dependents/dependencies)
- `dependencyGraph`
    - root/build/dependency_graph.gv - файл графа зависимостей проекта
