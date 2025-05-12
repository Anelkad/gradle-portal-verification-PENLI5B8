# Gradle dependency graph visualisation plugin

A **Gradle Plugin** that generates dependency graphs showing the relationship between modules in your project.

The plugin generates a graph visualising the dependencies across the whole project. It also generates sub-graphs for each module within the project. For projects with a large number of modules, I find the sub-graphs tend to be a lot more useful.

The graphs are generated in the [`mermaid.js`](https://mermaid.js.org/syntax/flowchart.html#direction-in-subgraphs) format so they are automatically displayed by Github.

### Using the plugin

The plugin adds a new Gradle task - `dependencyGraph`. Running the task will generate the dependency graphs for all modules in the project.

```bash
./gradlew dependencyGraph
```

### Configuring the plugin

Optionally **configure the plugin** in the same `build.gradle.kts` if you want to change the defaults
```kotlin
dependencyGraphConfig {
    graphDirection.set(Direction.LeftToRight)

    showLegend.set(ShowLegend.OnlyInRootGraph)

    ignoreModules.set(listOf(":example:system-test", ":example:test-fixtures"))

    repoRootUrl.set("https://github.com/anelkad/Gradle-dependency-graphs")

    mainBranchName.set("main")

    graphFileName.set("dependencyGraph.md")
}
```