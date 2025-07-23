package de.infix.testBalloon.compilerPlugin

import buildConfig.BuildConfig
import buildConfig.BuildConfig.PROJECT_FRAMEWORK_CORE_ARTIFACT_ID
import buildConfig.BuildConfig.PROJECT_GROUP_ID
import buildConfig.BuildConfig.PROJECT_VERSION
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
import org.jetbrains.kotlin.backend.jvm.ir.fileParent
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.incremental.components.Position
import org.jetbrains.kotlin.incremental.components.ScopeKind
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
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrSymbolOwner
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.createBlockBody
import org.jetbrains.kotlin.ir.declarations.nameWithPackage
import org.jetbrains.kotlin.ir.declarations.path
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
        compilerConfiguration.get(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        fun reportDisablingReason(detail: String) {
            if (Options.debug.value(compilerConfiguration)) {
                messageCollector.report(
                    CompilerMessageSeverity.WARNING,
                    "$PLUGIN_DISPLAY_NAME: [DEBUG] Disabling the plugin for module ${moduleFragment.name}: $detail"
                )
            }
        }

        // If we are not compiling a test module: Disable the compiler plugin.
        // Otherwise, we end up defining the discovery result property twice (one for the main module, another one
        // for the test module). If the test module picks up the main module's symbol, no suites will be considered
        // discovered.
        // TODO: Relying on the module name ending in "_test" is probably not stable. An alternative way could be
        //     to generate the test discovery result property only if at least one suite has been discovered.
        if (!moduleFragment.name.asStringStripSpecialMarkers().endsWith("_test")) {
            reportDisablingReason("It is not a test module.")
            return
        }

        // If we are not compiling a module with the framework library dependency: Disable the compiler plugin.
        if (!ModuleProbe(pluginContext).hasFrameworkLibraryDependency()) {
            reportDisablingReason("It has no framework library dependency.")
            return
        }

        val configuration: Configuration = try {
            Configuration(compilerConfiguration, pluginContext)
        } catch (exception: MissingFrameworkSymbol) {
            messageCollector.report(CompilerMessageSeverity.ERROR, "$PLUGIN_DISPLAY_NAME: ${exception.message}")
            return
        }

        moduleFragment.transform(ModuleTransformer(pluginContext, messageCollector, configuration), null)
    }
}

private interface IrPluginContextOwner {
    val pluginContext: IrPluginContext
}

private class ModuleProbe(override val pluginContext: IrPluginContext) : IrPluginContextOwner {
    /** Returns true if the currently compiled module is `TestSuite`-aware. */
    fun hasFrameworkLibraryDependency(): Boolean = irClassSymbolOrNull(AbstractTestSuite::class.qualifiedName!!) != null
}

private class Configuration(compilerConfiguration: CompilerConfiguration, override val pluginContext: IrPluginContext) :
    IrPluginContextOwner {

    val internalPackageName = "de.infix.testBalloon.framework.internal"
    val entryPointPackageName = "de.infix.testBalloon.framework.internal.entryPoint"

    val debugEnabled = Options.debug.value(compilerConfiguration)
    val jvmStandaloneEnabled = Options.jvmStandalone.value(compilerConfiguration)
    val lookupTracker = compilerConfiguration.get(CommonConfigurationKeys.LOOKUP_TRACKER)

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

    class DiscoveredSuite(
        val referencedDeclaration: IrDeclarationWithName,
        val valueExpression: IrBuilderWithScope.() -> IrExpression
    )

    val discoveredSuites = mutableListOf<DiscoveredSuite>()
    var customSessionClass: IrClass? = null

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
                    discoveredSuites.add(DiscoveredSuite(irClass) { irConstructorCall(irClass.symbol) })
                }
            }
        }

        return super.visitClassNew(irClass)
    }

    override fun visitPropertyNew(declaration: IrProperty): IrStatement {
        @Suppress("UnnecessaryVariable", "RedundantSuppression")
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

                discoveredSuites.add(DiscoveredSuite(irProperty) { irCall(irProperty.getter!!.symbol) })
            }
        }

        return super.visitPropertyNew(declaration)
    }

    override fun visitModuleFragment(declaration: IrModuleFragment): IrModuleFragment {
        // Process the entire module fragment first, collecting all test suites.
        val moduleFragment = super.visitModuleFragment(declaration)

        // We have left all source files behind.
        sourceFileForReporting = null

        val entryPointPackageFqName = FqName(configuration.entryPointPackageName)
        val entryPointFile = moduleFragment.files.singleOrNull { it.packageFqName == entryPointPackageFqName }
            ?: return moduleFragment // Do not proceed unless an entry point anchor class is present
        sourceFileForReporting = entryPointFile

        withErrorReporting(
            moduleFragment,
            "Could not generate entry point code in '${entryPointFile.nameWithPackage}'"
        ) {
            if (configuration.debugEnabled) {
                reportDebug(
                    "Generating code in module '${moduleFragment.name}'," +
                        " file '${entryPointFile.nameWithPackage}'," +
                        " for  ${discoveredSuites.size} discovered suites," +
                        " custom session: " +
                        if (customSessionClass == null) "default" else "${customSessionClass?.fqName()}",
                    moduleFragment
                )
            }

            val platform = pluginContext.platform
            val entryPoint = when {
                platform.isJvm() -> {
                    if (configuration.jvmStandaloneEnabled) {
                        irSuspendMainFunction()
                    } else {
                        irTestFrameworkDiscoveryResultProperty(entryPointFile)
                    }
                }

                platform.isJs() || platform.isWasm() -> {
                    irSuspendMainFunction()
                }

                platform.isNative() -> {
                    irTestFrameworkEntryPointProperty(entryPointFile)
                }

                else -> throw UnsupportedOperationException("Cannot generate entry points for platform '$platform'")
            }

            entryPointFile.addChild(entryPoint)
            if (configuration.debugEnabled) {
                reportDebug("Generated:\n${declaration.dump().prependIndent("\t")}")
            }

            // With incremental compilation, the compiler needs to recompile the entry point file if one of its
            // referenced declarations change. To do so, it needs to be told about such references.
            // We register the entry point file referencing
            // - the custom session class (if available), and
            // - top-level suites.
            customSessionClass?.let { entryPointFile.registerReference(it) }
            for (discoveredSuite in discoveredSuites) {
                entryPointFile.registerReference(discoveredSuite.referencedDeclaration)
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

                    @Suppress("UnnecessaryVariable", "RedundantSuppression")
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
                            nameValueArgumentsToAdd.forEach { (index, value) ->
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

                    @Suppress("UnnecessaryVariable", "RedundantSuppression")
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
                            nameValueArgumentsToAdd.forEach { (index, value) ->
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
     * Returns a `main` function declaration for [discoveredSuites] returning s1...sn:
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
            +irSimpleFunctionCall(
                configuration.configureAndExecuteTestsFunctionSymbol,
                irArrayOfRootSuites()
            )
        }
    }

    /**
     * Returns a `testFrameworkEntryPoint` property declaration for [discoveredSuites] returning s1...sn:
     *
     * ```
     * @EagerInitialization
     * private val testFrameworkEntryPoint: Unit = run {
     *     initializeTestFramework(customSessionOrNull)
     *     configureAndExecuteTestsBlocking(arrayOf(s1, ..., sn))
     * }
     * ```
     */
    private fun irTestFrameworkEntryPointProperty(entryPointFile: IrFile): IrProperty {
        val propertyName = Name.identifier("testFrameworkEntryPoint")

        return pluginContext.irFactory.buildProperty {
            name = propertyName
            visibility = DescriptorVisibilities.PRIVATE
        }.apply {
            parent = entryPointFile
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
     * Returns a `testFrameworkDiscoveryResult` property declaration for [discoveredSuites] returning s1...sn:
     *
     * ```
     * internal val testFrameworkDiscoveryResult: TestFrameworkDiscoveryResult = run {
     *     initializeTestFramework(customSessionOrNull)
     *     TestFrameworkDiscoveryResult(arrayOf(s1, ..., sn))
     * }
     * ```
     */
    private fun irTestFrameworkDiscoveryResultProperty(entryPointFile: IrFile): IrProperty {
        val propertyName = configuration.testFrameworkDiscoveryResultPropertyName

        return pluginContext.irFactory.buildProperty {
            name = propertyName
            visibility = DescriptorVisibilities.INTERNAL
        }.apply {
            parent = entryPointFile

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
     * Returns an array expression containing the list of results from [discoveredSuites].
     */
    private fun IrBuilderWithScope.irArrayOfRootSuites(): IrExpression {
        val irElementType = configuration.abstractSuiteSymbol.owner.defaultType
        val irArrayType = pluginContext.irBuiltIns.arrayClass.typeWith(irElementType)

        return irCall(
            callee = pluginContext.irBuiltIns.arrayOf,
            type = irArrayType,
            typeArguments = listOf(irElementType)
        ).apply {
            val irSuitesVararg: List<IrExpression> = discoveredSuites.map { discoveredSuite ->
                discoveredSuite.valueExpression.invoke(this@irArrayOfRootSuites)
            }
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

    /**
     * Registers a reference from [this] file to [referencedDeclaration], potentially residing in another file.
     *
     * This is required for incremental compilation.
     */
    private fun IrFile.registerReference(referencedDeclaration: IrDeclarationWithName) {
        val lookupTracker = configuration.lookupTracker ?: return

        synchronized(lookupTracker) {
            lookupTracker.record(
                filePath = path,
                position = Position.NO_POSITION,
                scopeFqName = referencedDeclaration.fileParent.packageFqName.asString(),
                scopeKind = ScopeKind.CLASSIFIER,
                name = referencedDeclaration.name.asString()
            )
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
    ?: throw MissingFrameworkSymbol("class '$fqName'")

private fun IrPluginContextOwner.irFunctionSymbol(packageName: String, functionName: String): IrSimpleFunctionSymbol =
    pluginContext.referenceFunctions(CallableId(FqName(packageName), Name.identifier(functionName))).singleOrNull()
        ?: throw MissingFrameworkSymbol("function '$packageName.$functionName'")

private class MissingFrameworkSymbol(typeAndName: String) :
    Error(
        "Could not find $typeAndName.\n" +
            "\tPlease add the dependency '$PROJECT_GROUP_ID:$PROJECT_FRAMEWORK_CORE_ARTIFACT_ID:$PROJECT_VERSION'."
    )

private fun IrClass.fqName(): String = "${packageFqName.asQualificationPrefix()}$name"

private fun FqName?.asQualificationPrefix(): String = if (this == null || isRoot) "" else "$this."

private const val PLUGIN_DISPLAY_NAME = "Plugin ${BuildConfig.PROJECT_COMPILER_PLUGIN_ID}"
