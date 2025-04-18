package de.infix.testBalloon.compilerPlugin

import buildConfig.BuildConfig
import de.infix.testBalloon.framework.AbstractTestSession
import de.infix.testBalloon.framework.AbstractTestSuite
import de.infix.testBalloon.framework.TestDiscoverable
import de.infix.testBalloon.framework.TestDisplayName
import de.infix.testBalloon.framework.TestElementName
import de.infix.testBalloon.framework.internal.TestFrameworkDiscoveryResult
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.IrBlockBuilder
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.IrSingleStatementBuilder
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.declarations.addGetter
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildProperty
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrSymbolOwner
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.createBlockBody
import org.jetbrains.kotlin.ir.declarations.nameWithPackage
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.copyTypeAndValueArgumentsFrom
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.util.superClass
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.platform.isWasm
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.isNative
import kotlin.reflect.KClass

class CompilerPluginIrGenerationExtension(private val compilerConfiguration: CompilerConfiguration) :
    IrGenerationExtension {

    private val messageCollector =
        compilerConfiguration.get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        val configuration: Configuration = try {
            Configuration(compilerConfiguration, pluginContext)
        } catch (exception: IllegalStateException) {
            messageCollector.report(CompilerMessageSeverity.ERROR, "$PLUGIN_DISPLAY_NAME: ${exception.message}")
            return
        }

        moduleFragment.transform(ModuleTransformer(pluginContext, messageCollector, configuration), null)
    }
}

private interface IrPluginContextOwner {
    val pluginContext: IrPluginContext
}

private class Configuration(compilerConfiguration: CompilerConfiguration, override val pluginContext: IrPluginContext) :
    IrPluginContextOwner {

    private val internalPackageName = "de.infix.testBalloon.framework.internal"

    val debugEnabled = Options.debug.value(compilerConfiguration)
    val jvmStandaloneEnabled = Options.jvmStandalone.value(compilerConfiguration)

    val abstractSuiteSymbol = irClassSymbol(AbstractTestSuite::class)
    val abstractSessionSymbol = irClassSymbol(AbstractTestSession::class)
    val testDiscoverableAnnotationSymbol = irClassSymbol(TestDiscoverable::class)
    val testElementNameAnnotationSymbol = irClassSymbol(TestElementName::class)
    val testDisplayNameAnnotationSymbol = irClassSymbol(TestDisplayName::class)
    val testFrameworkDiscoveryResultSymbol = irClassSymbol(TestFrameworkDiscoveryResult::class)

    val initializeTestFrameworkFunctionSymbol = irFunctionSymbol(internalPackageName, "initializeTestFramework")
    val configureAndExecuteTestsFunctionSymbol = irFunctionSymbol(internalPackageName, "configureAndExecuteTests")
    val configureAndExecuteTestsBlockingFunctionSymbol by lazy {
        irFunctionSymbol(internalPackageName, "configureAndExecuteTestsBlocking")
    }

    val testFrameworkDiscoveryResultPropertyName =
        Name.identifier("testFrameworkDiscoveryResult") // getter: getTestFrameworkDiscoveryResult
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private class ModuleTransformer(
    override val pluginContext: IrPluginContext,
    val messageCollector: MessageCollector,
    val configuration: Configuration
) : IrElementTransformerVoidWithContext(),
    IrPluginContextOwner {

    val discoveredSuiteExpressions = mutableListOf<IrBuilderWithScope.() -> IrExpression>()
    var customSessionClass: IrClass? = null

    var sourceFileForReporting: IrFile? = null

    override fun visitFileNew(declaration: IrFile): IrFile {
        val irFile = declaration

        sourceFileForReporting = irFile
        return super.visitFileNew(irFile)
    }

    override fun visitClassNew(declaration: IrClass): IrStatement {
        val irClass = declaration

        withErrorReporting(irClass, "Could not analyze class '${irClass.fqName()}'") {
            // Fast path: Ignore non-suite classes.
            if (!irClass.isSameOrSubTypeOf(configuration.abstractSuiteSymbol)) return@withErrorReporting

            // Consider classes with a @TestDiscoverable superclass.
            if (irClass.superClass?.hasAnnotation(configuration.testDiscoverableAnnotationSymbol) == true) {
                if (configuration.debugEnabled) {
                    reportDebug("Found test discoverable '${irClass.fqName()}'", irClass)
                }

                if (irClass.isSameOrSubTypeOf(configuration.abstractSessionSymbol)) {
                    if (customSessionClass == null) {
                        customSessionClass = irClass
                    } else {
                        reportError(
                            "Found multiple test sessions annotated with" +
                                " @${configuration.testDiscoverableAnnotationSymbol.owner.name}," +
                                " but expected at most one.",
                            irClass
                        )
                    }
                } else {
                    irClass.addNameValueArgumentsToConstructorIfApplicable()
                    discoveredSuiteExpressions.add { irConstructorCall(irClass.symbol) }
                }
            }
        }

        return super.visitClassNew(irClass)
    }

    override fun visitPropertyNew(declaration: IrProperty): IrStatement {
        val irProperty = declaration

        withErrorReporting(irProperty, "Could not analyze property '${irProperty.fqNameWhenAvailable}'") {
            // Fast path: Top-level delegating properties only.
            if (!(irProperty.isDelegated && irProperty.parent is IrFile)) return@withErrorReporting

            // Look for an initialization via a function call.
            val initializer = irProperty.backingField?.initializer ?: return@withErrorReporting
            val initializerCall = initializer.expression as? IrCall ?: return@withErrorReporting
            val initializerCallFunction = initializerCall.symbol.owner

            if (initializerCallFunction.hasAnnotation(configuration.testDiscoverableAnnotationSymbol)) {
                if (configuration.debugEnabled) {
                    reportDebug("Found test discoverable '${irProperty.fqNameWhenAvailable}'", irProperty)
                }

                irProperty.addNameValueArgumentsToInitializerCallIfApplicable(
                    initializer,
                    initializerCall,
                    initializerCallFunction
                )

                discoveredSuiteExpressions.add { irCall(irProperty.getter!!.symbol) }
            }
        }

        return super.visitPropertyNew(declaration)
    }

    override fun visitModuleFragment(declaration: IrModuleFragment): IrModuleFragment {
        // Process the entire module fragment first, collecting all test suites.
        val moduleFragment = super.visitModuleFragment(declaration)

        // We have left all source files behind.
        sourceFileForReporting = null

        // Add the generated entry points to the first IR source file (return if none exists).
        val entryPointsFile = moduleFragment.files.firstOrNull() ?: return moduleFragment

        fun addEntryPoint(declaration: IrDeclaration) {
            entryPointsFile.addChild(declaration)
            if (configuration.debugEnabled) {
                reportDebug("Generated:\n${declaration.dump().prependIndent("\t")}")
            }
        }

        withErrorReporting(
            moduleFragment,
            "Could not generate entry point code in '${entryPointsFile.nameWithPackage}'"
        ) {
            if (configuration.debugEnabled) {
                reportDebug(
                    "Generating code in module '${moduleFragment.name}'," +
                        " file '${entryPointsFile.nameWithPackage}'," +
                        " for  ${discoveredSuiteExpressions.size} discovered suites," +
                        " custom session: " +
                        if (customSessionClass == null) "default" else "${customSessionClass?.fqName()}",
                    moduleFragment
                )
            }

            val platform = pluginContext.platform
            when {
                platform.isJvm() -> {
                    if (configuration.jvmStandaloneEnabled) {
                        addEntryPoint(irSuspendMainFunction())
                    } else {
                        addEntryPoint(irTestFrameworkDiscoveryResultProperty(entryPointsFile))
                    }
                }

                platform.isJs() || platform.isWasm() -> {
                    addEntryPoint(irSuspendMainFunction())
                }

                platform.isNative() -> {
                    addEntryPoint(irTestFrameworkEntryPointProperty(entryPointsFile))
                }

                else -> throw UnsupportedOperationException("Cannot generate entry points for platform '$platform'")
            }
        }

        return moduleFragment
    }

    /**
     * Adds value arguments for element and display name to [this] class's primary constructor, if applicable.
     *
     * Parameters are added according to `@[TestElementName]` and `@[TestDisplayName]` annotations.
     */
    private fun IrClass.addNameValueArgumentsToConstructorIfApplicable() {
        val irClass = this
        val primaryConstructor = irClass.primaryConstructor ?: return
        val superClassConstructorCall =
            primaryConstructor.body?.statements?.firstOrNull() as IrDelegatingConstructorCall? ?: return
        val valueParameters = superClassConstructorCall.symbol.owner.valueParameters
        val valueArguments = superClassConstructorCall.valueArguments

        val nameValueArgumentsToAdd = nameValueArgumentsToAdd(
            mapOf(
                configuration.testElementNameAnnotationSymbol to { irClass.fqName() },
                configuration.testDisplayNameAnnotationSymbol to { "${irClass.name}" }
            ),
            valueParameters,
            valueArguments
        )

        if (nameValueArgumentsToAdd.isEmpty()) return

        primaryConstructor.transformChildren(
            object : IrElementTransformerVoid() {
                var constructorCallProcessed = false

                override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall): IrExpression {
                    // Fast path: Skip all constructor calls after the first one.
                    if (constructorCallProcessed) super.visitDelegatingConstructorCall(expression)
                    constructorCallProcessed = true

                    val originalCall = expression
                    return DeclarationIrBuilder(
                        pluginContext,
                        currentScope!!.scope.scopeOwnerSymbol,
                        originalCall.startOffset,
                        originalCall.endOffset
                    ).run {
                        @Suppress("DuplicatedCode")
                        IrDelegatingConstructorCallImpl.fromSymbolOwner(
                            originalCall.startOffset,
                            originalCall.endOffset,
                            originalCall.type,
                            originalCall.symbol,
                            originalCall.typeArgumentsCount
                        ).apply {
                            copyTypeAndValueArgumentsFrom(originalCall)
                            nameValueArgumentsToAdd.forEach { index, value ->
                                putValueArgument(index, irString(value))
                                if (configuration.debugEnabled) {
                                    reportDebug(
                                        "${irClass.fqName()}: Setting parameter '${valueParameters[index].name}'" +
                                            " to '$value'"
                                    )
                                }
                            }
                        }
                    }
                }
            },
            null
        )
    }

    /**
     * Adds value arguments for element and display name to [this] property's initializer function call, if applicable.
     *
     * Parameters are added according to `@[TestElementName]` and `@[TestDisplayName]` annotations.
     */
    private fun IrProperty.addNameValueArgumentsToInitializerCallIfApplicable(
        initializer: IrExpressionBody,
        initializerCall: IrCall,
        initializerCallFunction: IrSimpleFunction
    ) {
        val irProperty = this
        val valueParameters = initializerCallFunction.valueParameters
        val valueArguments = initializerCall.valueArguments

        val nameValueArgumentsToAdd = nameValueArgumentsToAdd(
            mapOf(
                configuration.testElementNameAnnotationSymbol to { irProperty.fqName() },
                configuration.testDisplayNameAnnotationSymbol to { "${irProperty.name}" }
            ),
            valueParameters,
            valueArguments
        )

        if (nameValueArgumentsToAdd.isEmpty()) return

        initializer.transformChildren(
            object : IrElementTransformerVoid() {
                var callProcessed = false

                override fun visitCall(expression: IrCall): IrExpression {
                    // Fast path: Skip all calls after the first one.
                    if (callProcessed) return super.visitCall(expression)
                    callProcessed = true

                    val originalCall = expression
                    return DeclarationIrBuilder(
                        pluginContext,
                        currentScope!!.scope.scopeOwnerSymbol,
                        originalCall.startOffset,
                        originalCall.endOffset
                    ).run {
                        @Suppress("DuplicatedCode")
                        irCall(originalCall.symbol).apply {
                            copyTypeAndValueArgumentsFrom(originalCall)
                            nameValueArgumentsToAdd.forEach { index, value ->
                                putValueArgument(index, irString(value))
                                if (configuration.debugEnabled) {
                                    reportDebug(
                                        "${irProperty.fqName()}: Setting parameter '${valueParameters[index].name}'" +
                                            " to '$value'"
                                    )
                                }
                            }
                        }
                    }
                }
            },
            null
        )
    }

    /**
     * Returns an (index -> value) map of value arguments to add for element and display name.
     *
     * [generatedValuesByAnnotation] specifies which annotation translates to which generated argument value.
     * Annotated candidates in a call's [valueParameters], which are missing a [valueArguments], generate
     * a value argument in the result map.
     */
    private fun nameValueArgumentsToAdd(
        generatedValuesByAnnotation: Map<IrClassSymbol, () -> String>,
        valueParameters: List<IrValueParameter>,
        valueArguments: List<IrExpression?>
    ): Map<Int, String> = valueParameters.mapNotNull { valueParameter ->
        generatedValuesByAnnotation.firstNotNullOfOrNull { (annotationSymbol, value) ->
            // Annotated parameters with a missing value argument only.
            if (valueParameter.hasAnnotation(annotationSymbol) && valueArguments[valueParameter.index] == null) {
                Pair(valueParameter.index, value())
            } else {
                null
            }
        }
    }.toMap()

    private fun IrProperty.fqName(): String = fqNameWhenAvailable.toString()

    /**
     * Returns a `main` function declaration for [discoveredSuiteExpressions] returning s1...sn:
     *
     * ```
     * suspend fun main(arguments: Array<String>) {
     *     initializeTestFramework(customSessionOrNull, arguments)
     *     configureAndExecuteTests(arrayOf(s1, ..., sn))
     * }
     * ```
     */
    private fun irSuspendMainFunction(): IrSimpleFunction = pluginContext.irFactory.buildFun {
        name = Name.identifier("main")
        isSuspend = true
        returnType = pluginContext.irBuiltIns.unitType
    }.apply {
        val irArgumentsValueParameter = addValueParameter(
            "arguments",
            pluginContext.irBuiltIns.arrayClass.typeWith(pluginContext.irBuiltIns.stringType),
            origin
        )
        body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
            +irSimpleFunctionCall(
                configuration.initializeTestFrameworkFunctionSymbol,
                customSessionClass?.let { irConstructorCall(it.symbol) },
                irGet(irArgumentsValueParameter)
            )
            +irSimpleFunctionCall(configuration.configureAndExecuteTestsFunctionSymbol, irArrayOfRootSuites())
        }
    }

    /**
     * Returns a `testFrameworkEntryPoint` property declaration for [discoveredSuiteExpressions] returning s1...sn:
     *
     * ```
     * @EagerInitialization
     * private val testFrameworkEntryPoint: Unit = run {
     *     initializeTestFramework(customSessionOrNull)
     *     configureAndExecuteTestsBlocking(arrayOf(s1, ..., sn))
     * }
     * ```
     */
    private fun irTestFrameworkEntryPointProperty(entryPointsTargetFile: IrFile): IrProperty {
        val propertyName = Name.identifier("testFrameworkEntryPoint")

        return pluginContext.irFactory.buildProperty {
            name = propertyName
            visibility = DescriptorVisibilities.PRIVATE
        }.apply {
            parent = entryPointsTargetFile
            annotations += irConstructorCall(irClassSymbol("kotlin.native.EagerInitialization"))

            initializeWith(propertyName, pluginContext.irBuiltIns.unitType) {
                +irSimpleFunctionCall(
                    configuration.initializeTestFrameworkFunctionSymbol,
                    customSessionClass?.let { irConstructorCall(it.symbol) }
                )
                +irSimpleFunctionCall(
                    configuration.configureAndExecuteTestsBlockingFunctionSymbol,
                    irArrayOfRootSuites()
                )
            }
        }
    }

    /**
     * Returns a `testFrameworkDiscoveryResult` property declaration for [discoveredSuiteExpressions] returning s1...sn:
     *
     * ```
     * internal val testFrameworkDiscoveryResult: TestFrameworkDiscoveryResult = run {
     *     initializeTestFramework(customSessionOrNull)
     *     TestFrameworkDiscoveryResult(arrayOf(s1, ..., sn))
     * }
     * ```
     */
    private fun irTestFrameworkDiscoveryResultProperty(entryPointsFile: IrFile): IrProperty {
        val propertyName = configuration.testFrameworkDiscoveryResultPropertyName

        return pluginContext.irFactory.buildProperty {
            name = propertyName
            visibility = DescriptorVisibilities.INTERNAL
        }.apply {
            parent = entryPointsFile

            initializeWith(propertyName, configuration.testFrameworkDiscoveryResultSymbol.owner.defaultType) {
                +irSimpleFunctionCall(
                    configuration.initializeTestFrameworkFunctionSymbol,
                    customSessionClass?.let { irConstructorCall(it.symbol) }
                )
                +irConstructorCall(
                    configuration.testFrameworkDiscoveryResultSymbol,
                    irArrayOfRootSuites()
                )
            }
        }
    }

    /**
     * Initializes a top-level val property returning [resultType] with a backing field and [initialization] statements.
     */
    private fun IrProperty.initializeWith(
        propertyName: Name,
        resultType: IrType,
        initialization: IrBlockBuilder.() -> Unit
    ) {
        val property = this

        val field = pluginContext.irFactory.buildField {
            name = propertyName
            type = resultType
            visibility = DescriptorVisibilities.PRIVATE
            isFinal = true
            isExternal = false
            isStatic = true // a top-level val must be static
        }.apply {
            correspondingPropertySymbol = property.symbol
            initializer = pluginContext.irFactory.createExpressionBody(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                DeclarationIrBuilder(pluginContext, symbol).irBlock {
                    initialization()
                }
            )
        }

        backingField = field

        addGetter {
            origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
            returnType = field.type
        }.apply {
            body = factory.createBlockBody(
                UNDEFINED_OFFSET,
                UNDEFINED_OFFSET,
                listOf(
                    IrReturnImpl(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        pluginContext.irBuiltIns.nothingType,
                        symbol,
                        IrGetFieldImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, field.symbol, field.type)
                    )
                )
            )
        }
    }

    /**
     * Returns an array expression containing the list of results from [discoveredSuiteExpressions].
     */
    private fun IrBuilderWithScope.irArrayOfRootSuites(): IrExpression {
        val irElementType = configuration.abstractSuiteSymbol.owner.defaultType
        val irArrayType = pluginContext.irBuiltIns.arrayClass.typeWith(irElementType)
        val irSuitesVararg: List<IrExpression> = discoveredSuiteExpressions.map { it.invoke(this) }

        return irCall(
            callee = pluginContext.irBuiltIns.arrayOf,
            type = irArrayType,
            typeArguments = listOf(irElementType)
        ).apply {
            putValueArgument(0, irVararg(irElementType, irSuitesVararg))
        }
    }

    private fun IrSymbolOwner.irConstructorCall(irClassSymbol: IrClassSymbol, vararg irValues: IrExpression?) =
        IrSingleStatementBuilder(pluginContext, Scope(symbol), UNDEFINED_OFFSET, UNDEFINED_OFFSET).build {
            irConstructorCall(irClassSymbol, *irValues)
        }

    private fun IrBuilderWithScope.irConstructorCall(
        irClassSymbol: IrClassSymbol,
        vararg irValues: IrExpression?
    ): IrConstructorCall {
        val irConstructor = irClassSymbol.constructors.singleOrNull()
            ?: throw IllegalArgumentException("$irClassSymbol must have a single constructor")

        return irCall(irConstructor).apply {
            irValues.forEachIndexed { index, irValue ->
                putValueArgument(index, irValue ?: irNull())
            }
        }
    }

    fun IrClass.isSameOrSubTypeOf(irSupertypeClassSymbol: IrClassSymbol): Boolean =
        symbol.owner.defaultType.isSubtypeOfClass(irSupertypeClassSymbol)

    fun <Result> withErrorReporting(declaration: IrElement, failureDescription: String, block: () -> Result): Result =
        try {
            block()
        } catch (throwable: Throwable) {
            report(CompilerMessageSeverity.EXCEPTION, "$failureDescription: $throwable", declaration)
            throw throwable
        }

    fun reportDebug(message: String, declaration: IrElement? = null) =
        report(CompilerMessageSeverity.WARNING, "[DEBUG] $message", declaration)

    fun reportError(message: String, declaration: IrElement? = null) =
        report(CompilerMessageSeverity.ERROR, message, declaration)

    fun report(severity: CompilerMessageSeverity, message: String, declaration: IrElement? = null) {
        fun IrFile.locationOrNull(offset: Int?): CompilerMessageLocation? {
            if (offset == null) return null
            val lineNumber = fileEntry.getLineNumber(offset) + 1
            val columnNumber = fileEntry.getColumnNumber(offset) + 1
            return CompilerMessageLocation.create(fileEntry.name, lineNumber, columnNumber, null)
        }

        messageCollector.report(
            severity,
            "$PLUGIN_DISPLAY_NAME: $message",
            sourceFileForReporting?.locationOrNull(declaration?.startOffset)
        )
    }
}

private fun IrBuilderWithScope.irSimpleFunctionCall(
    irFunctionSymbol: IrSimpleFunctionSymbol,
    vararg irValues: IrExpression?
) = irCall(irFunctionSymbol).apply {
    irValues.forEachIndexed { index, irValue ->
        putValueArgument(index, irValue ?: irNull())
    }
}

private fun IrPluginContextOwner.irClassSymbol(kClass: KClass<*>): IrClassSymbol = irClassSymbol(kClass.qualifiedName!!)

private fun IrPluginContextOwner.irClassSymbolOrNull(fqName: String): IrClassSymbol? =
    pluginContext.referenceClass(ClassId.topLevel(FqName(fqName)))

private fun IrPluginContextOwner.irClassSymbol(fqName: String): IrClassSymbol = irClassSymbolOrNull(fqName)
    ?: throw IllegalStateException("Class '$fqName' $MISSING_CLASSPATH_INFO")

private fun IrPluginContextOwner.irFunctionSymbol(packageName: String, functionName: String): IrSimpleFunctionSymbol =
    pluginContext.referenceFunctions(CallableId(FqName(packageName), Name.identifier(functionName))).singleOrNull()
        ?: throw IllegalStateException("Function '$packageName.$functionName' $MISSING_CLASSPATH_INFO")

private fun IrClass.fqName(): String = "${packageFqName.asQualificationPrefix()}$name"

private fun FqName?.asQualificationPrefix(): String = if (this == null || isRoot) "" else "$this."

private const val PLUGIN_DISPLAY_NAME = "Plugin ${BuildConfig.TEST_FRAMEWORK_PLUGIN_ID}"

private const val MISSING_CLASSPATH_INFO =
    "was not found on the classpath. Please add the corresponding library dependency."
