package testFramework.compilerPlugin

import buildConfig.BuildConfig
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
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrSymbolOwner
import org.jetbrains.kotlin.ir.declarations.createBlockBody
import org.jetbrains.kotlin.ir.declarations.nameWithPackage
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.ir.util.superClass
import org.jetbrains.kotlin.javac.resolve.classId
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.platform.isWasm
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.isNative

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

    private val publicPackageName = "testFramework"
    private val internalPackageName = "testFramework.internal"

    val debugEnabled = Options.debug.value(compilerConfiguration)
    val jvmStandaloneEnabled = Options.jvmStandalone.value(compilerConfiguration)

    val abstractSuiteSymbol = irClassSymbol(publicPackageName, "AbstractTestSuite")
    val abstractSessionSymbol = irClassSymbol(publicPackageName, "AbstractTestSession")
    val testDiscoverableAnnotationSymbol = irClassSymbol(publicPackageName, "TestDiscoverable")

    val initializeTestFrameworkFunctionSymbol = irFunctionSymbol(internalPackageName, "initializeTestFramework")
    val runTestsFunctionSymbol = irFunctionSymbol(internalPackageName, "runTests")
    val runTestsBlockingFunctionSymbol by lazy { irFunctionSymbol(internalPackageName, "runTestsBlocking") }
    val testFrameworkDiscoveryResultSymbol = irClassSymbol(internalPackageName, "TestFrameworkDiscoveryResult")
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
            val initializerCall =
                (irProperty.backingField?.initializer?.expression as? IrCall) ?: return@withErrorReporting

            if (initializerCall.symbol.owner.hasAnnotation(configuration.testDiscoverableAnnotationSymbol)) {
                if (configuration.debugEnabled) {
                    reportDebug("Found test discoverable '${irProperty.fqNameWhenAvailable}'", irProperty)
                }

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
            val entryPointDeclarations: List<IrDeclaration> = when {
                platform.isJvm() -> listOfNotNull(
                    if (configuration.jvmStandaloneEnabled) {
                        irSuspendMainFunction()
                    } else {
                        irTestFrameworkDiscoveryResultProperty(entryPointsFile)
                    }
                )

                platform.isJs() || platform.isWasm() -> listOfNotNull(
                    irSuspendMainFunction(),
                    irWasmStartUnitTestsFunctionOrNull()
                )

                platform.isNative() -> listOfNotNull(
                    irTestFrameworkEntryPointProperty(entryPointsFile)
                )

                else -> throw UnsupportedOperationException("Cannot generate entry points for platform '$platform'")
            }

            entryPointDeclarations.forEach { entryPointDeclaration ->
                entryPointsFile.addChild(entryPointDeclaration)
                if (configuration.debugEnabled) {
                    reportDebug("Generated:\n${entryPointDeclaration.dump().prependIndent("\t")}")
                }
            }
        }

        return moduleFragment
    }

    /**
     * Returns a `main` function declaration for [discoveredSuiteExpressions] returning s1...sn:
     *
     * ```
     * suspend fun main(arguments: Array<String>) {
     *     initializeTestFramework(customSessionOrNull, arguments)
     *     runTests(arrayOf(s1, ..., sn))
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
            +irSimpleFunctionCall(configuration.runTestsFunctionSymbol, irArrayOfRootSuites())
        }
    }

    /**
     * Returns a `testFrameworkEntryPoint` property declaration for [discoveredSuiteExpressions] returning s1...sn:
     *
     * ```
     * @EagerInitialization
     * private val testFrameworkEntryPoint: Unit = run {
     *     initializeTestFramework(customSessionOrNull)
     *     runTestsBlocking(arrayOf(s1, ..., sn))
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
            annotations += irConstructorCall(irClassSymbol("kotlin.native", "EagerInitialization"))

            initializeWith(propertyName, pluginContext.irBuiltIns.unitType) {
                +irSimpleFunctionCall(
                    configuration.initializeTestFrameworkFunctionSymbol,
                    customSessionClass?.let { irConstructorCall(it.symbol) }
                )
                +irSimpleFunctionCall(configuration.runTestsBlockingFunctionSymbol, irArrayOfRootSuites())
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
    private fun irTestFrameworkDiscoveryResultProperty(entryPointsTargetFile: IrFile): IrProperty {
        val propertyName = Name.identifier("testFrameworkDiscoveryResult")

        return pluginContext.irFactory.buildProperty {
            name = propertyName
            visibility = DescriptorVisibilities.INTERNAL
        }.apply {
            parent = entryPointsTargetFile

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

    /**
     * Returns a `startUnitTests` function declaration like this, or `null` if there is no `@WasmExport` symbol:
     *
     * ```
     * @WasmExport
     * fun startUnitTests() {}
     * ```
     *
     * `startUnitTests()` is invoked by Kotlin/Wasm and must be present. It has no effect with this framework.
     */
    private fun irWasmStartUnitTestsFunctionOrNull(): IrSimpleFunction? {
        val wasmExportSymbol = irClassSymbolOrNull("kotlin.wasm", "WasmExport") ?: return null

        return pluginContext.irFactory.buildFun {
            name = Name.identifier("startUnitTests")
            visibility = DescriptorVisibilities.INTERNAL
            returnType = pluginContext.irBuiltIns.unitType
        }.apply {
            annotations += irConstructorCall(wasmExportSymbol)
            body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {}
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

private fun IrPluginContextOwner.irClassSymbolOrNull(packageName: String, className: String): IrClassSymbol? =
    pluginContext.referenceClass(classId(packageName, className))

private fun IrPluginContextOwner.irClassSymbol(packageName: String, className: String): IrClassSymbol =
    irClassSymbolOrNull(packageName, className)
        ?: throw IllegalStateException("Class '$packageName.$className' $MISSING_CLASSPATH_INFO")

private fun IrPluginContextOwner.irFunctionSymbol(packageName: String, functionName: String): IrSimpleFunctionSymbol =
    pluginContext.referenceFunctions(CallableId(FqName(packageName), Name.identifier(functionName))).singleOrNull()
        ?: throw IllegalStateException("Function '$packageName.$functionName' $MISSING_CLASSPATH_INFO")

private fun IrClass.fqName(): String = "${packageFqName.asQualificationPrefix()}$name"

private fun FqName?.asQualificationPrefix(): String = if (this == null || isRoot) "" else "$this."

private const val PLUGIN_DISPLAY_NAME = "Plugin ${BuildConfig.TEST_FRAMEWORK_PLUGIN_ID}"

private const val MISSING_CLASSPATH_INFO =
    "was not found on the classpath. Please add the corresponding library dependency."
