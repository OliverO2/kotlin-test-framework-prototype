package testFramework.compilerPlugin

import buildConfig.BuildConfig
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

@OptIn(ExperimentalCompilerApi::class)
class CompilerPluginCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = BuildConfig.TEST_FRAMEWORK_PLUGIN_ID

    override val pluginOptions: Collection<AbstractCliOption> = Options.all

    override fun processOption(option: AbstractCliOption, value: String, configuration: CompilerConfiguration) {
        val specificOption = optionsByName[option.optionName]
        if (specificOption != null) {
            specificOption.addToCompilerConfiguration(configuration, value)
        } else {
            throw IllegalArgumentException("Unexpected plugin option ${option.optionName}")
        }
    }

    companion object {
        private val optionsByName = Options.all.associateBy { it.optionName }
    }
}
