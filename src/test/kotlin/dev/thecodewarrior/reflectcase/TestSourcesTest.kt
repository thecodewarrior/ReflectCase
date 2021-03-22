package dev.thecodewarrior.reflectcase

import dev.thecodewarrior.reflectcase.joor.CompileException
import dev.thecodewarrior.reflectcase.testsupport.assertCause
import dev.thecodewarrior.reflectcase.testsupport.assertInstanceOf
import dev.thecodewarrior.reflectcase.testsupport.assertMessage
import dev.thecodewarrior.reflectcase.testsupport.swallowSyntax
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TestSourcesTest {
    @Test
    fun gettingClass_withoutCompiling_shouldThrowIllegalStateException() {
        val sources = TestSources.create()
        val X by sources.add("X", "class X {}")
        assertThrows<IllegalStateException> {
            val resolved = X
        }
    }

    @Test
    fun gettingClass_afterCompiling_shouldReturnClass() {
        val sources = TestSources.create()
        val X by sources.add("X", "class X {}")
        sources.compile()
        assertInstanceOf<Class<*>>(X)
    }


    @Test
    fun compiling_withSyntaxError_shouldThrow_withBuildOutput() {
        val sources = TestSources.create()
        val X by sources.add("X", swallowSyntax("class X { whoops! }"))
        assertThrows<CompileException> {
            sources.compile()
        }.assertCause<CompileException>().assertMessage("""
            Compilation error:
            /gen/X.java:2: error: <identifier> expected
            class X { whoops! }
                            ^
            1 error
            
        """.trimIndent())
    }

    @Test
    fun compiling_withMissingType_shouldThrow() {
        val sources = TestSources.create()
        val X by sources.add("X", "class X extends Missing { }")
        assertThrows<CompileException> {
            sources.compile()
        }
    }

    @Test
    fun compiling_withMultipleTypes_shouldNotThrow() {
        val sources = TestSources.create()
        val X by sources.add("X", "class X { }")
        val Y by sources.add("Y", "class Y { }")
        sources.compile()
    }

    @Test
    fun compiling_withMultipleDependingTypes_shouldNotThrow() {
        val sources = TestSources.create()
        val X by sources.add("X", "class X { Y other; }")
        val Y by sources.add("Y", "class Y { X other; }")
        sources.compile()
    }

    @Test
    fun compiling_withTypesInPackages_shouldPutTypesInRelativePackage_andImportRootGenPackage() {
        val sources = TestSources.create()
        val X by sources.add("relative.X", "public class X { Y other; }")
        val Y by sources.add("Y", "import gen.relative.X; public class Y { X other; }")
        sources.compile()
    }

    @Test
    fun compiling_withGlobalImports_shouldAddImports() {
        val sources = TestSources.create()
        sources.globalImports.add("java.io.File")
        val X by sources.add("X", "class X { void method() { Class foo = File.class; } }")
        sources.compile()
    }

    @Test
    fun compiling_shouldIncludeParameterNames() {
        val sources = TestSources.create()
        val X by sources.add("X", "class X { void method(int named) {} }")
        sources.compile()
        assertEquals("named", X.getDeclaredMethod("method", Int::class.javaPrimitiveType).parameters[0].name)
    }

    @Test
    fun compiling_shouldIncludeTypeAnnotations() {
        val sources = TestSources.create()
        val A by sources.add("A", "@rt(TYPE_USE) @interface A {}").typed<Annotation>()
        val X by sources.add("X", "class X { @A Object method() { return null; } }")
        sources.compile()
        assertTrue(X.getDeclaredMethod("method").annotatedReturnType.isAnnotationPresent(A))
    }
}
