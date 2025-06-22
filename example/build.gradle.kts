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

    graphModuleGroupNames.set(listOf(":example:theNewThing"))

    commonDirs.set(
        listOf(
            "di",
            "ui",
            "data",
            "domain",
            "model",
        ),
    )

    modulesDependencyToWarning.set(
        listOf(
//            ":example:models",
        )
    )

    // Optional
    graphDirection.set(io.github.anelkad.dependencygraph.plugin.Direction.LeftToRight)

    // Optional
    showLegend.set(io.github.anelkad.dependencygraph.plugin.ShowLegend.OnlyInRootGraph)

    // Optional
    ignoreModules.set(listOf(":example:system-test"))

    ignoreExternalDependencies.set(listOf())

    triggerModuleNames.set(listOf("ui"))

    // Optional
    shouldLinkModuleText.set(true)

    // Optional (default false)
    shouldGroupModules.set(true)
}