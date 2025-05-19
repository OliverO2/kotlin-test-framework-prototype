package de.infix.testBalloon.compilerPlugin

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

internal object Options {
    val debug = Option(
        optionName = "debug",
        valueDescription = "boolean",
        description = "Enable debugging",
        defaultValue = false
    ) { stringValue ->
        stringValue.toBooleanStrictOrNull() ?: throwValueError(stringValue)
    }

    val jvmStandalone = Option(
        optionName = "jvmStandalone",
        valueDescription = "boolean",
        description = "Enable standalone invocation without JUnit Platform on the JVM",
        defaultValue = false
    ) { stringValue ->
        stringValue.toBooleanStrictOrNull() ?: throwValueError(stringValue)
    }

    val all = registeredOptions.toList()
}

internal class Option<Type : Any>(
    override val optionName: String,
    override val valueDescription: String,
    override val description: String,
    override val required: Boolean = false,
    override val allowMultipleOccurrences: Boolean = false,
    val defaultValue: Type,
    val valueFromString: Option<Type>.(stringValue: String) -> Type
) : AbstractCliOption {
    private val key = CompilerConfigurationKey<Type>(optionName)

    init {
        registeredOptions.add(this)
    }

    fun value(compilerConfiguration: CompilerConfiguration): Type = compilerConfiguration[key] ?: defaultValue

    fun addToCompilerConfiguration(configuration: CompilerConfiguration, value: String) {
        configuration.put(key, valueFromString(value))
    }

    fun throwValueError(value: String): Nothing =
        throw IllegalArgumentException("Unexpected $valueDescription value '$value' for option '$optionName'")
}

private val registeredOptions = mutableListOf<Option<*>>()
