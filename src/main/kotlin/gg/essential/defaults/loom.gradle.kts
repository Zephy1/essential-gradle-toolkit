package gg.essential.defaults

import dev.architectury.pack200.java.Pack200Adapter
import gg.essential.Versions
import gg.essential.gradle.multiversion.Platform

plugins {
    id("gg.essential.loom")
}

val platform = Platform.of(project)
if (platform.isUnobfuscated) {
    // Unobfuscated versions of Loom no longer need to remap mod dependencies, and so no longer provide the `mod*`
    // configurations. We'll re-create them so they can be used across all versions.
    configurations.api.get().extendsFrom(configurations.create("modApi"))
    configurations.implementation.get().extendsFrom(configurations.create("modImplementation"))
    configurations.compileOnly.get().extendsFrom(configurations.create("modCompileOnly"))
    configurations.runtimeOnly.get().extendsFrom(configurations.create("modRuntimeOnly"))
    configurations.localRuntime.get().extendsFrom(configurations.create("modLocalRuntime"))
}

fun prop(property: String, default: String?) =
    findProperty("essential.defaults.loom.$property")?.toString()
        ?: default
        ?: throw GradleException("No default $property for ${platform.mcVersionStr} ${platform.loaderStr}. Set `essential.defaults.loom.$property` in the project's `gradle.properties` or PR a new default.")

dependencies {
    minecraft(prop("minecraft", "com.mojang:minecraft:${platform.mcVersionStr}"))
    val mappingsStr = prop("mappings", when {
        platform.isUnobfuscated -> ""
        platform.isFabric ->
            Versions.YARN_VERSIONS[platform.mcVersion]?.let { "net.fabricmc:yarn:$it" }
		platform.isLegacyFabric ->
            Versions.YARN_VERSIONS[platform.mcVersion]?.let { "net.legacyfabric:yarn:$it" }
        platform.isForge && platform.mcVersion < 11700 ->
            Versions.MCP_VERSIONS[platform.mcVersion]?.let { "de.oceanlabs.mcp:mcp_$it" }
        else -> "official"
    })
    if (mappingsStr in listOf("official", "mojang", "mojmap")) {
        mappings(loom.officialMojangMappings())
    } else if (mappingsStr.isNotBlank()) {
        mappings(mappingsStr)
    }

    if (platform.isFabric) {
		modImplementation(prop("fabric-loader", "net.fabricmc:fabric-loader:${Versions.FABRIC_LOADER_VERSION}"))
        platform.fabricApiVersion?.let {
            modImplementation("net.fabricmc.fabric-api:fabric-api:${it}")
        }
        platform.fabricKotlinVersion?.let {
            modImplementation("net.fabricmc:fabric-language-kotlin:${it}")
        }
	} else if (platform.isLegacyFabric) {
        modImplementation(prop("fabric-loader", "net.legacyfabric.legacy-fabric-api:legacy-fabric-api:${Versions.LEGACY_FABRIC_LOADER_VERSION}+${platform.mcVersionStr}"))
    } else if (platform.isForge) {
        "forge"(prop("forge", Versions.FORGE_VERSIONS[platform.mcVersion]?.let { "net.minecraftforge:forge:$it" }))
        loom.forge.pack200Provider.set(Pack200Adapter())
    } else if (platform.isNeoForge) {
        "neoForge"(prop("neoForge", Versions.NEOFORGE_VERSIONS[platform.mcVersion]?.let { "net.neoforged:neoforge:$it" }))
    }
}

// https://github.com/architectury/architectury-loom/pull/10
if (platform.isModLauncher) {
    val forgeRepo = repositories.find { it.name == "Forge" } as? MavenArtifactRepository
    forgeRepo?.metadataSources {
        mavenPom()
        artifact()
        ignoreGradleMetadataRedirection()
    }
}

repositories {
    maven("https://maven.neoforged.net/releases/")
    maven("https://repo.legacyfabric.net/repository/legacyfabric/")
}
