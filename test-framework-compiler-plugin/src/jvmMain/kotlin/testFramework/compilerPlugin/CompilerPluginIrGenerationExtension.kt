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
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrSymbolOwner
import org.jetbrains.kotlin.ir.declarations.nameWithPackage
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.packageFqName
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
    private val annotationsPackageName = "testFramework.annotations"
    private val internalPackageName = "testFramework.internal"

    val debugEnabled = Options.debug.value(compilerConfiguration)
    val jvmStandaloneEnabled = Options.jvmStandalone.value(compilerConfiguration)

    val suiteClassSymbol = irClassSymbol(publicPackageName, "TestSuite")
    val sessionClassSymbol = irClassSymbol(publicPackageName, "TestSession")

    val suiteAnnotationSymbol = irClassSymbol(annotationsPackageName, "TestSuiteDeclaration")
    val sessionAnnotationSymbol = irClassSymbol(annotationsPackageName, "TestSessionDeclaration")
    val initializeTestFrameworkFunctionSymbol = irFunctionSymbol(internalPackageName, "initializeTestFramework")
    val runTestsFunctionSymbol = irFunctionSymbol(internalPackageName, "runTests")
    val runTestsBlockingFunctionSymbol by lazy { irFunctionSymbol(internalPackageName, "runTestsBlocking") }
}

private class ModuleTransformer(
    override val pluginContext: IrPluginContext,
    val messageCollector: MessageCollector,
    val configuration: Configuration
) : IrElementTransformerVoidWithContext(),
    IrPluginContextOwner {

    val rootSuiteClasses = mutableListOf<IrClass>()
    var rootSessionClass: IrClass? = null

    var sourceFileForReporting: IrFile? = null

    override fun visitFileNew(declaration: IrFile): IrFile {
        @Suppress("UnnecessaryVariable", "RedundantSuppression")
        val irFile = declaration

        sourceFileForReporting = irFile
        return super.visitFileNew(irFile)
    }

    override fun visitClassNew(declaration: IrClass): IrStatement {
        @Suppress("UnnecessaryVariable", "RedundantSuppression")
        val irClass = declaration

        withErrorReporting(irClass, "Could not analyze class '${irClass.name}'") {
            when {
                irClass.hasAnnotation(configuration.suiteAnnotationSymbol) -> {
                    if (configuration.debugEnabled) {
                        reportDebug("Found test suite '${irClass.fqName()}'", irClass)
                    }

                    if (irClass.isSameOrSubTypeOfWithReporting(configuration.suiteClassSymbol)) {
                        rootSuiteClasses.add(irClass)
                    }
                }

                irClass.hasAnnotation(configuration.sessionAnnotationSymbol) -> {
                    if (configuration.debugEnabled) {
                        reportDebug("Found test session '${irClass.fqName()}'", irClass)
                    }

                    if (irClass.isSameOrSubTypeOfWithReporting(configuration.sessionClassSymbol)) {
                        if (rootSessionClass == null) {
                            rootSessionClass = irClass
                        } else {
                            @OptIn(UnsafeDuringIrConstructionAPI::class) // safe: annotation exists
                            reportError(
                                "Found multiple annotations @${configuration.sessionAnnotationSymbol.owner.name}," +
                                    " but expected at most one.",
                                irClass
                            )
                        }
                    }
                }
            }
        }

        return super.visitClassNew(irClass)
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
                        " for ${rootSuiteClasses.size} root suite(s)" +
                        " and ${if (rootSessionClass == null) "no" else "one"} root configuration.",
                    moduleFragment
                )
            }

            val platform = pluginContext.platform
            val entryPointDeclarations: List<IrDeclaration> = when {
                platform.isJvm() -> listOfNotNull(
                    if (configuration.jvmStandaloneEnabled) irSuspendMainFunction() else null
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
     * Returns a `main` function declaration for [rootSuiteClasses] TS1...TSn like this:
     *
     * ```
     * suspend fun main(arguments: Array<String>) {
     *     initializeTestFramework(rootSessionOrNull, arguments)
     *     runTests(TS1(), ..., TSn())
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
                rootSessionClass?.let { irConstructorCall(it.symbol) },
                irGet(irArgumentsValueParameter)
            )
            +irSimpleFunctionCall(configuration.runTestsFunctionSymbol, irArrayOfRootSuites())
        }
    }

    /**
     * Returns a `testFrameworkEntryPoint` property declaration for [rootSuiteClasses] TS1...TSn like this:
     *
     * ```
     * @EagerInitialization
     * private val testFrameworkEntryPoint: Unit = run {
     *     initializeTestFramework(rootSessionOrNull)
     *     runTestsBlocking(TS1(), ..., TSn())
     * }
     * ```
     */
    private fun irTestFrameworkEntryPointProperty(entryPointsTargetFile: IrFile): IrProperty {
        val propertyName = Name.identifier("testFrameworkEntryPoint")

        return pluginContext.irFactory.buildProperty {
            name = propertyName
            visibility = DescriptorVisibilities.PRIVATE
        }.apply property@{
            parent = entryPointsTargetFile
            annotations +=
                irConstructorCall(irClassSymbol("kotlin.native", "EagerInitialization"))

            backingField = pluginContext.irFactory.buildField {
                name = propertyName
                type = pluginContext.irBuiltIns.unitType
                isFinal = true
                isExternal = false
                isStatic = true // a top-level val must be static
            }.apply {
                correspondingPropertySymbol = this@property.symbol
                initializer = pluginContext.irFactory.createExpressionBody(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    DeclarationIrBuilder(pluginContext, symbol).irBlock {
                        +irSimpleFunctionCall(
                            configuration.initializeTestFrameworkFunctionSymbol,
                            rootSessionClass?.let { irConstructorCall(it.symbol) }
                        )
                        +irSimpleFunctionCall(configuration.runTestsBlockingFunctionSymbol, irArrayOfRootSuites())
                    }
                )
            }

            addGetter {
                returnType = pluginContext.irBuiltIns.unitType
            }.apply {
                body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {}
            }
        }
    }

    /**
     * Returns an array expression containing the list of instantiated [rootSuiteClasses].
     */
    private fun IrBuilderWithScope.irArrayOfRootSuites(): IrExpression {
        @OptIn(UnsafeDuringIrConstructionAPI::class) // safe: suite class exists
        val irElementType = configuration.suiteClassSymbol.owner.defaultType
        val irArrayType = pluginContext.irBuiltIns.arrayClass.typeWith(irElementType)
        val irSuitesVararg = rootSuiteClasses.map { irClass ->
            irConstructorCall(irClass.symbol)
        }

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

    private fun IrSymbolOwner.irConstructorCall(irClassSymbol: IrClassSymbol) =
        IrSingleStatementBuilder(pluginContext, Scope(symbol), UNDEFINED_OFFSET, UNDEFINED_OFFSET).build {
            irConstructorCall(irClassSymbol)
        }

    private fun IrBuilderWithScope.irConstructorCall(irClassSymbol: IrClassSymbol): IrConstructorCall {
        @OptIn(UnsafeDuringIrConstructionAPI::class) // safe: module fragment has been completely processed
        val irConstructor = irClassSymbol.constructors.singleOrNull()
            ?: throw IllegalArgumentException("$irClassSymbol must have a single constructor")

        return irCall(irConstructor)
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class) // safe: module fragment has been completely processed
    fun IrClass.isSameOrSubTypeOfWithReporting(irSupertypeClassSymbol: IrClassSymbol): Boolean {
        if (symbol.owner.defaultType.isSubtypeOfClass(irSupertypeClassSymbol)) return true
        reportError(
            "'$name' does not conform to the expected type of '${irSupertypeClassSymbol.owner.fqName()}'",
            this
        )
        return false
    }

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

@Suppress("SameParameterValue")
private fun IrPluginContextOwner.irFunctionSymbol(packageName: String, functionName: String): IrSimpleFunctionSymbol =
    pluginContext.referenceFunctions(CallableId(FqName(packageName), Name.identifier(functionName))).singleOrNull()
        ?: throw IllegalStateException("Function '$packageName.$functionName' $MISSING_CLASSPATH_INFO")

private fun IrClass.fqName(): String = "${packageFqName.asQualificationPrefix()}$name"

private fun FqName?.asQualificationPrefix(): String = if (this == null || isRoot) "" else "$this."

private const val PLUGIN_DISPLAY_NAME = "Plugin ${BuildConfig.TEST_FRAMEWORK_PLUGIN_ID}"

private const val MISSING_CLASSPATH_INFO =
    "was not found on the classpath. Please add the corresponding library dependency."
