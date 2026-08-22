rootProject.name = "essential-gradle-toolkit"

includeBuild("../preprocessor") {
    dependencySubstitution {
        substitute(module("com.github.replaymod:preprocessor")).using(project(":"))
    }
}
