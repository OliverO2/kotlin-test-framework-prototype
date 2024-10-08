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
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrSymbolOwner
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.nameWithPackage
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.javac.resolve.classId
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.platform.isWasm
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.isNative
import java.util.concurrent.atomic.AtomicReference

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

    val debugEnabled = Options.debug.value(compilerConfiguration)
    val jvmStandaloneEnabled = Options.jvmStandalone.value(compilerConfiguration)

    private val frameworkAnnotationsPackageName = "testFramework.annotations"
    private val suiteAnnotationClassName = "TestSuiteDeclaration"
    private val sessionAnnotationClassName = "TestSessionDeclaration"

    private val initializationPackageName = "testFramework.internal"
    private val initializeTestFrameworkFunctionName = "initializeTestFramework"
    private val runTestsFunctionName = "runTests"
    private val runTestsBlockingFunctionName = "runTestsBlocking"

    val suiteAnnotationSymbol = irClassSymbol(frameworkAnnotationsPackageName, suiteAnnotationClassName)

    val sessionAnnotationSymbol =
        irClassSymbol(frameworkAnnotationsPackageName, sessionAnnotationClassName)

    val initializeTestFrameworkFunctionSymbol =
        irFunctionSymbol(initializationPackageName, initializeTestFrameworkFunctionName)

    val runTestsFunctionSymbol =
        irFunctionSymbol(initializationPackageName, runTestsFunctionName)

    val runTestsBlockingFunctionSymbol by lazy {
        irFunctionSymbol(initializationPackageName, runTestsBlockingFunctionName)
    }
}

private class ModuleTransformer(
    override val pluginContext: IrPluginContext,
    val messageCollector: MessageCollector,
    val configuration: Configuration
) : IrElementTransformerVoidWithContext(),
    IrPluginContextOwner {

    class EntryPointCollection {
        private val elementList = mutableListOf<IrClass>()
        val elements: List<IrClass> get() = synchronized(this) { elementList }

        fun add(irClass: IrClass) = synchronized(this) { elementList.add(irClass) }
    }

    val rootSuites = EntryPointCollection()
    var rootSession = AtomicReference<IrClass?>(null)

    var sourceFileForReporting: IrFile? = null

    override fun visitFileNew(declaration: IrFile): IrFile {
        sourceFileForReporting = declaration
        return super.visitFileNew(declaration)
    }

    override fun visitClassNew(declaration: IrClass): IrStatement {
        withErrorReporting<Unit>(declaration, "Could not analyze class '${declaration.name}'") {
            when {
                declaration.hasAnnotation(configuration.suiteAnnotationSymbol) -> {
                    if (configuration.debugEnabled) {
                        reportDebug("Found test suite '${declaration.name}'", declaration)
                    }

                    rootSuites.add(declaration)
                }

                declaration.hasAnnotation(configuration.sessionAnnotationSymbol) -> {
                    if (configuration.debugEnabled) {
                        reportDebug("Found test session '${declaration.name}'", declaration)
                    }

                    if (!rootSession.compareAndSet(null, declaration)) {
                        @OptIn(UnsafeDuringIrConstructionAPI::class) // safe: annotation exists
                        reportError(
                            "Found multiple annotations @${configuration.sessionAnnotationSymbol.owner.name}," +
                                " but expected at most one.",
                            declaration
                        )
                    }
                }
            }
        }

        return super.visitClassNew(declaration)
    }

    override fun visitModuleFragment(declaration: IrModuleFragment): IrModuleFragment {
        // Process the entire module fragment first, collecting all test suites.
        val moduleFragment = super.visitModuleFragment(declaration)

        // Add the generated entry points to the first IR source file (return if none exists).
        val entryPointsTargetFile = moduleFragment.files.firstOrNull() ?: return moduleFragment

        withErrorReporting(
            declaration,
            "Could not generate entry point code in '${entryPointsTargetFile.nameWithPackage}'"
        ) {
            // We have left all source files behind.
            sourceFileForReporting = null

            if (configuration.debugEnabled) {
                reportDebug(
                    "Generating code in module '${declaration.name}'," +
                        " file '${entryPointsTargetFile.nameWithPackage}'," +
                        " for ${rootSuites.elements.size} root suite(s)" +
                        " and ${if (rootSession.get() == null) "no" else "one"} root configuration.",
                    declaration
                )
            }

            val platform = pluginContext.platform
            val entryPointDeclarations = when {
                platform.isJvm() -> listOfNotNull(
                    if (configuration.jvmStandaloneEnabled) suspendMainFunctionDeclaration() else null
                )

                platform.isJs() || platform.isWasm() -> listOfNotNull(
                    suspendMainFunctionDeclaration(),
                    wasmStartUnitTestsFunctionDeclarationOrNull()
                )

                platform.isNative() -> listOfNotNull(
                    testFrameworkEntryPointPropertyDeclaration(entryPointsTargetFile)
                )

                else -> throw UnsupportedOperationException("cannot generate entry points for platform '$platform'")
            }

            entryPointDeclarations.forEach {
                entryPointsTargetFile.addChild(it)
                if (configuration.debugEnabled) {
                    reportDebug("Generated:\n${it.dump().prependIndent("\t")}")
                }
            }
        }

        return moduleFragment
    }

    /**
     * Returns an entry point function declaration for test suite classes TS1...TSn like this:
     *
     * ```
     * suspend fun main(arguments: Array<String>) {
     *     initializeTestFramework(rootSessionOrNull, arguments)
     *     runTests(TS1(), ..., TSn())
     * }
     * ```
     */
    private fun suspendMainFunctionDeclaration(): IrSimpleFunction = pluginContext.irFactory.buildFun {
        name = Name.identifier("main")
        isSuspend = true
        returnType = pluginContext.irBuiltIns.unitType
    }.apply {
        val arguments = addValueParameter(
            "arguments",
            pluginContext.irBuiltIns.arrayClass.typeWith(pluginContext.irBuiltIns.stringType),
            origin
        )
        body = DeclarationIrBuilder(pluginContext, symbol).irBlockBody {
            +initializeTestFrameworkFunctionCall(arguments)
            +runTestsFunctionCall()
        }
    }

    /**
     * Returns an entry point property declaration for test suite classes TS1...TSn like this:
     *
     * ```
     * @EagerInitialization
     * private val testFrameworkEntryPoint: Unit = run {
     *     initializeTestFramework(rootSessionOrNull)
     *     runTestsBlocking(TS1(), ..., TSn())
     * }
     * ```
     */
    private fun testFrameworkEntryPointPropertyDeclaration(entryPointsTargetFile: IrFile): IrProperty {
        val propertyName = Name.identifier("testFrameworkEntryPoint")

        return pluginContext.irFactory.buildProperty {
            name = propertyName
            visibility = DescriptorVisibilities.PRIVATE
        }.apply property@{
            parent = entryPointsTargetFile
            annotations +=
                irConstructorCall(irClassSymbol("kotlin.native", "EagerInitialization"))

            backingField = pluginContext.irFactory.buildField {
                type = pluginContext.irBuiltIns.unitType
                isFinal = true
                isExternal = false
                isStatic = true // top level vals must be static
                name = propertyName
            }.apply {
                correspondingPropertySymbol = this@property.symbol
                initializer = pluginContext.irFactory.createExpressionBody(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    DeclarationIrBuilder(pluginContext, symbol).irBlock {
                        +initializeTestFrameworkFunctionCall()
                        +runTestsFunctionCall(configuration.runTestsBlockingFunctionSymbol)
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
     * Returns a function call to initialize the test framework.
     *
     * For a declared test session class [rootSession] and [arguments]:
     * ```
     * initializeTestFrameworkFunctionCall(rootSession(), arguments)
     * ```
     *
     * Without a declared test session class and [arguments]:
     * ```
     * initializeTestFrameworkFunctionCall(null, arguments)
     * ```
     */
    private fun IrBuilderWithScope.initializeTestFrameworkFunctionCall(arguments: IrValueParameter? = null): IrCall =
        irCall(configuration.initializeTestFrameworkFunctionSymbol).apply {
            val irTestSessionOrNull = rootSession.get()?.let { irConstructorCall(it.symbol) } ?: irNull()
            putValueArgument(0, irTestSessionOrNull)
            putValueArgument(1, if (arguments != null) irGet(arguments) else irNull())
        }

    /**
     * Returns a function call for test suite classes TS1...TSn like this:
     *
     * ```
     * runTests(TS1(), ..., TSn())
     * ```
     *
     * [runTestsSymbol] specifies the called function's name.
     */
    private fun IrBuilderWithScope.runTestsFunctionCall(
        runTestsSymbol: IrSimpleFunctionSymbol = configuration.runTestsFunctionSymbol
    ) = irCall(runTestsSymbol).apply {
        val suites = rootSuites.elements.map { irClass ->
            irConstructorCall(irClass.symbol)
        }

        putValueArgument(0, irVararg(pluginContext.irBuiltIns.anyType, suites))
    }

    /**
     * Returns a Kotlin/Wasm entry point function declaration like this, or `null` if there is no `@WasmExport` symbol:
     *
     * ```
     * @WasmExport
     * fun startUnitTests() {}
     * ```
     *
     * `startUnitTests()` is invoked by Kotlin/Wasm and must be present. It has no effect with this framework.
     */
    private fun wasmStartUnitTestsFunctionDeclarationOrNull(): IrSimpleFunction? {
        val wasmExportSymbol = irClassSymbolOrNull("kotlin.wasm", "WasmExport") ?: return null

        return pluginContext.irFactory.buildFun {
            name = Name.identifier("startUnitTests")
            returnType = pluginContext.irBuiltIns.unitType
            visibility = DescriptorVisibilities.INTERNAL
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

private fun IrPluginContextOwner.irClassSymbolOrNull(packageName: String, className: String): IrClassSymbol? =
    pluginContext.referenceClass(classId(packageName, className))

private fun IrPluginContextOwner.irClassSymbol(packageName: String, className: String): IrClassSymbol =
    irClassSymbolOrNull(packageName, className)
        ?: throw IllegalStateException("Class '$packageName.$className' $MISSING_CLASSPATH_INFO")

@Suppress("SameParameterValue")
private fun IrPluginContextOwner.irFunctionSymbol(packageName: String, functionName: String): IrSimpleFunctionSymbol =
    pluginContext.referenceFunctions(CallableId(FqName(packageName), Name.identifier(functionName))).singleOrNull()
        ?: throw IllegalStateException("Function '$packageName.$functionName' $MISSING_CLASSPATH_INFO")

private const val PLUGIN_DISPLAY_NAME = "Plugin ${BuildConfig.TEST_FRAMEWORK_PLUGIN_ID}"

private const val MISSING_CLASSPATH_INFO =
    "was not found on the classpath. Please add the corresponding library dependency."
