import io.github.anelkad.dependencygraph.plugin.Direction
import io.github.anelkad.dependencygraph.plugin.ShowLegend

plugins {
    id("io.github.anelkad.dependencygraph")
}

dependencyGraphConfig {
    // Optional
    repoRootUrl.set("https://github.com/anelkad/Gradle-dependency-graphs/")

    // Optional
    mainBranchName.set("main")

    // Optional
    graphFileName.set("dependencyGraph.md")

    // Optional
    graphDirection.set(io.github.anelkad.dependencygraph.plugin.Direction.LeftToRight)

    // Optional
    showLegend.set(io.github.anelkad.dependencygraph.plugin.ShowLegend.OnlyInRootGraph)

    // Optional
    ignoreModules.set(listOf(":example:system-test"))

//    ignoreExternalDependencies.set(listOf("androidx.appcompat"))

    // Optional
    shouldLinkModuleText.set(true)

    // Optional (default false)
    shouldGroupModules.set(true)
}