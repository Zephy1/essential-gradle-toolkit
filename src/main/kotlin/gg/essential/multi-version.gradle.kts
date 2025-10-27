package gg.essential

import com.replaymod.gradle.preprocess.PreprocessExtension
import com.replaymod.gradle.preprocess.PreprocessPlugin
import gg.essential.gradle.multiversion.Platform
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RunGameTask
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
}

val platform = Platform.of(project)

extensions.add("platform", platform)

setupLoomPlugin()
setupPreprocessPlugin()
configureJavaVersion()
afterEvaluate { configureResources() } // delayed because it needs project.version
parent?.let(::inheritConfigurationFrom)

fun setupLoomPlugin() {
    extra.set("loom.platform", platform.loaderStr)

    if (platform.loaderStr.lowercase() == "legacyfabric") {
        extra.set("loom.platform", "fabric")
        apply(plugin = "gg.essential.loom")
        extra.set("loom.platform", platform.loaderStr)
    } else {
        apply(plugin = "gg.essential.loom")
    }

    extensions.configure<LoomGradleExtensionAPI> {
        runConfigs.all {
            isIdeConfigGenerated = true
        }
    }

    afterEvaluate {
        extensions.configure<LoomGradleExtensionAPI> {
            try {
                val property = this::class.members.find { it.name == "remapSourcesJar" }
                if (property != null) {
                    this.javaClass.getMethod("setRemapSourcesJar", Boolean::class.java).invoke(this, false)
                }
            } catch (e: Exception) {
                logger.warn("Could not set remapSourcesJar: ${e.message}")
            }
        }
    }
}

fun setupPreprocessPlugin() {
    apply<PreprocessPlugin>()

    extensions.configure<PreprocessExtension> {
        vars.put("MC", mcVersion)
        vars.put("FABRIC", if (platform.isFabric) 1 else 0)
		vars.put("LEGACYFABRIC", if (platform.isLegacyFabric) 1 else 0)
        vars.put("FORGE", if (platform.isForge) 1 else 0)
        vars.put("NEOFORGE", if (platform.isNeoForge) 1 else 0)
        vars.put("FORGELIKE", if (platform.isForgeLike) 1 else 0)
    }
}

fun configureJavaVersion() {
    configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(platform.javaVersion.majorVersion))
    }

    pluginManager.withPlugin("kotlin") {
        configure<KotlinJvmProjectExtension> {
            jvmToolchain {
                languageVersion.set(JavaLanguageVersion.of(platform.javaVersion.majorVersion))
            }
        }

        tasks.withType<KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget.set(
                    when (platform.javaVersion) {
                        JavaVersion.VERSION_1_8 -> JvmTarget.JVM_1_8
                        JavaVersion.VERSION_11 -> JvmTarget.JVM_11
                        JavaVersion.VERSION_17 -> JvmTarget.JVM_17
                        JavaVersion.VERSION_21 -> JvmTarget.JVM_21
                        else -> JvmTarget.fromTarget(platform.javaVersion.toString())
                    }
                )
            }
        }
    }

    tasks.withType<RunGameTask>().configureEach {
        javaLauncher.set(javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(platform.javaVersion.majorVersion))
        })
    }
}

fun configureResources() {
    tasks.processResources {
        val expansions = mapOf(
            "version" to project.version,
            "mcVersionStr" to platform.mcVersionStr,
            "file" to mapOf("jarVersion" to project.version.toString().let {
                if (it[0].isDigit()) it else "0.$it"
            }),
        )

        inputs.property("mod_version_expansions", expansions)

        filesMatching(listOf("mcmod.info", "META-INF/mods.toml", "META-INF/neoforge.mods.toml", "fabric.mod.json")) {
            expand(expansions)
        }

        if (!platform.isFabric && !platform.isLegacyFabric) exclude("fabric.mod.json")
        if (!platform.isModLauncher) exclude("META-INF/mods.toml")
        if (!platform.isNeoForge) exclude("META-INF/neoforge.mods.toml")
        if (!platform.isLegacyForge) exclude("mcmod.info")
    }
}

fun inheritConfigurationFrom(parent: Project) {
    if (version == Project.DEFAULT_VERSION) {
        version = parent.version
    }

    val parentBase = parent.extensions.findByType<BasePluginExtension>()
    if (parentBase != null) {
        base.archivesName.convention(parentBase.archivesName.map { "$it ${project.name}" })
    } else {
        base.archivesName.convention("${parent.name} ${project.name}")
    }

    afterEvaluate {
        pluginManager.withPlugin("kotlin") {
            tasks.withType<KotlinCompile>().configureEach {
                compilerOptions {
                    if (moduleName.orNull == null && !freeCompilerArgs.get().contains("-module-name")) {
                        moduleName.set(
                            project.findProperty("baseArtifactId")?.toString()
                                ?: parentBase?.archivesName?.orNull
                                ?: parent.name.lowercase()
                        )
                    }
                }
            }
        }
    }
}
