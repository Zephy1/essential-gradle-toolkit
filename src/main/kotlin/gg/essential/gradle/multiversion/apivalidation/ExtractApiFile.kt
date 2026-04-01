package gg.essential.gradle.multiversion.apivalidation

import gg.essential.gradle.util.multiversionChildProjects
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class ExtractApiFile : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val input: RegularFileProperty

    @get:Input
    abstract val selector: Property<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun merge() {
        val parser = Parser(project.parent!!.multiversionChildProjects.keys)
        val input = parser.parseFile(input.get().asFile.readText())
        val outputStr = Writer(emptySet()).write(input.filtered(selector.get()))
        this.output.get().asFile.writeText(outputStr)
    }
}
