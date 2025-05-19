import de.infix.testBalloon.framework.TestCompartment
import de.infix.testBalloon.framework.testSuite

// This is the module's only source file. Thus, the compiler plugin will append entry point code to this file.
// Before the entry point function is invoked, this files 'Kt' class will be instantiated with all the file's
// top-level properties. At this point, there is no valid `TestSession` object to initialize compartments.
// If the compartment was not initialized lazily, the file initialization would fail.

val TestSuiteInFileWithGeneratedEntryPoints by testSuite(compartment = { TestCompartment.RealTime }) {
    test("test1") {
    }
}
