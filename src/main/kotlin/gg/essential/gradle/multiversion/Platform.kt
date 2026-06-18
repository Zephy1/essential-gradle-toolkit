package gg.essential.gradle.multiversion

import gg.essential.Versions
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project

data class Platform(
    val mcVersion: Int,
    val mcVersionStr: String,
    val loader: Loader,
    val yarnVersion: String?,
    val fabricApiVersion: String?,
    val fabricKotlinVersion: String?,
) {
    val loaderStr = loader.toString().lowercase()

    val isFabric = loader == Loader.Fabric
	val isLegacyFabric = loader == Loader.LegacyFabric
    val isForge = loader == Loader.Forge
    val isNeoForge = loader == Loader.NeoForge
    val isForgeLike = isForge || isNeoForge
    val isModLauncher = isForgeLike && mcVersion >= 11400
    val isLegacyForge = loader == Loader.Forge && mcVersion < 11400
    val isUnobfuscated = mcVersion >= 26_00_00

    val javaVersion = when {
        mcVersion >= 26_00_00 -> JavaVersion.VERSION_25
        mcVersion >= 12005 -> JavaVersion.VERSION_21
        mcVersion >= 11800 -> JavaVersion.VERSION_17
        mcVersion >= 11700 -> JavaVersion.VERSION_16
        else -> JavaVersion.VERSION_1_8
    }

    override fun toString(): String {
        return "$mcVersionStr-$loaderStr"
    }

    enum class Loader {
        Fabric,
		LegacyFabric,
        Forge,
        NeoForge,
    }

    companion object {
        fun of(project: Project): Platform {
            val loader = guessLoader(project)
            val mcVersionStr = guessMcVersion(project)
            val (major, minor, patch) = mcVersionStr.split('.').map { it.toInt() } + listOf(0)
            val mcVersion = major * 10000 + minor * 100 + patch
            val yarnVersionStr = guessYarnVersion(project, loader, mcVersion)
            val fabricApiVersionStr = guessFabricApiVersion(project, loader, mcVersion)
            val fabricKotlinVersionStr = guessFabricKotlinVersion(project, loader)
            return Platform(mcVersion, mcVersionStr, loader, yarnVersionStr, fabricApiVersionStr, fabricKotlinVersionStr)
        }

        private fun guessMcVersion(project: Project): String {
            // Try configured minecraft.version value first
            project.findProperty("minecraft.version")
                ?.let { return it.toString() }

            // If that's not set, try to infer it from the project name
            Regex("""(\d+)\.(\d+)(\.(\d+))?""").find(project.name)
                ?.let { return it.value }

            throw GradleException(
                "Failed to infer Minecraft version for project \"${project.path}\".\n" +
                        "Either set \"minecraft.version\" in its \"gradle.properties\"," +
                        "or change the project name to include the version."
            )
        }

        private fun guessLoader(project: Project): Loader {
            // Try configured loom.platform value first
            val loomPlatform = project.findProperty("loom.platform")?.toString()
            when (loomPlatform?.lowercase()) {
                "fabric" -> return Loader.Fabric
				"legacyfabric" -> return Loader.LegacyFabric
                "forge" -> return Loader.Forge
                "neoforge" -> return Loader.NeoForge
                null -> { }
                else -> throw GradleException("Unknown loom.platform value: \"$loomPlatform\"")
            }

            // If that's not set, try to infer it from the project name
            when {
                "fabric" in project.name.lowercase() -> return Loader.Fabric
				"legacyfabric" in project.name.lowercase() -> return Loader.LegacyFabric
                "neoforge" in project.name.lowercase() -> return Loader.NeoForge
                "forge" in project.name.lowercase() -> return Loader.Forge
                else -> { }
            }

            throw GradleException("Failed to infer mod loader for project \"${project.path}\".\n" +
                    "Either set \"loom.platform\" in its \"gradle.properties\"," +
                    "or change the project name to include the platform.\n" +
                    "Valid values: ${Loader.entries.joinToString { it.name.lowercase() }}")
        }

        private fun guessYarnVersion(project: Project, loader: Loader, mcVersion: Int): String? {
            if (loader != Loader.Fabric && loader != Loader.LegacyFabric) return null
            project.findProperty("essential.defaults.loom.yarn")?.toString()?.let {
                if (it == "true") {
                    return Versions.YARN_VERSIONS[mcVersion]
                }
                return it
            }
            return null
        }

        private fun guessFabricApiVersion(project: Project, loader: Loader, mcVersion: Int): String? {
            if (loader != Loader.Fabric) return null
            project.findProperty("essential.defaults.loom.fabric-api")?.toString()?.let {
                if (it == "true") {
                    return Versions.FABRIC_API_VERSIONS[mcVersion]
                }
                return it
            }
            return null
        }

        private fun guessFabricKotlinVersion(project: Project, loader: Loader): String? {
            if (loader != Loader.Fabric) return null
            project.findProperty("essential.defaults.loom.fabric-kotlin")?.toString()?.let {
                if (it == "true") {
                    return Versions.FABRIC_LANGUAGE_KOTLIN_VERSION
                }
                return it
            }
            return null
        }
    }
}
