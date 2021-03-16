package dev.thecodewarrior.reflectcase

import dev.thecodewarrior.reflectcase.impl.TypeSetBuilderRoot
import dev.thecodewarrior.reflectcase.impl.TypeSetImpl
import dev.thecodewarrior.reflectcase.joor.Compile
import dev.thecodewarrior.reflectcase.joor.CompileOptions
import org.intellij.lang.annotations.Language
import java.lang.reflect.AnnotatedParameterizedType
import java.lang.reflect.AnnotatedType
import java.lang.IllegalStateException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KProperty

/**
 * Runtime compilation of test cases.
 *
 * ## Basic usage
 * ```kotlin
 * val sources = TestSources()
 * val X: Class<*> by sources.add("X", "class X {}")
 * val A: Class<Annotation> by sources.add("A", "@interface A {}")
 * val types = sources.types {
 *     +"? extends X"
 *     block("T") {
 *         +"T"
 *     }
 * }
 * sources.compile()
 *
 * types["? extends X"]
 * types["T"]
 * ```
 */
public class TestSources {
    private val javaSources = mutableMapOf<String, String>()
    private val typeSets = mutableListOf<TypeSetImpl>()
    public var options: MutableList<String> = mutableListOf()
    public var classLoader: ClassLoader? = null
        private set

    init {
        options.add("-parameters")
    }

    public var globalImports: MutableList<String> = mutableListOf(
        "java.util.*",
        "java.lang.annotation.ElementType",
        "java.lang.annotation.Target",
        "java.lang.annotation.Retention",
        "java.lang.annotation.RetentionPolicy",
        "dev.thecodewarrior.reflectcase.TestSourceNopException"
    )

    /**
     * Adds the passed class to this compiler. This method automatically prepends the necessary `package` declaration
     * to the passed string and adds a wildcard import for the "root" package, `gen`, if needed.
     *
     * ### Expansions
     * This method will perform these expansions in the source code:
     * - Any occurrences of `@rt(targets)` in the text will be replaced with the annotations for runtime annotation
     *   retention and the passed element targets. e.g. `@rt(TYPE_USE)`
     * - Any occurrences of `NOP;` in the text will be replaced with a throw statement
     *
     * ### Useful reference
     * - Annotation: `@Retention(RetentionPolicy.RUNTIME) @Target(ElementType...) @interface A { int value(); }`
     *   - ElementTypes: `TYPE`, `FIELD`, `METHOD`, `PARAMETER`, `CONSTRUCTOR`, `LOCAL_VARIABLE`, `ANNOTATION_TYPE`,
     *     `PACKAGE`, `TYPE_PARAMETER`, `TYPE_USE`
     *
     * ### Common examples
     * ```kotlin
     * val A by sources.add("A", "@rt(TYPE_USE) @interface A {}")
     * val X by sources.add("X", "class X {}")
     * val G by sources.add("G", "class G<T> {}")
     * ```
     *
     * @param name The qualified name relative to the "root" package (`gen`)
     * @param code The code to compile into that class
     * @return A property delegate to access the test class once [compile] has been called
     */
    public fun add(name: String, @Language("java") code: String, trimIndent: Boolean = true): TestClass<*> {
        requireNotCompiled()
        if ("gen.$name" in javaSources)
            throw IllegalArgumentException("Class name $name already exists")

        var fullSource = ""
        if (name.contains('.'))
            fullSource += "package gen.${name.substringBeforeLast('.')};import gen.*;"
        else
            fullSource += "package gen;"

        fullSource += globalImports.joinToString("") { "import $it;" }
        fullSource += "\n"
        var processedCode = if (trimIndent) code.trimIndent() else code
        processedCode = processedCode.replace("""@rt\((\w+(?:,\s*\w+)*)\)""".toRegex()) { match ->
            val types = match.groupValues[1].split(",").joinToString(", ") { "ElementType.${it.trim()}" }
            "@Retention(RetentionPolicy.RUNTIME) @Target({ $types })"
        }
        processedCode = processedCode.replace("NOP;", "throw new TestSourceNopException();")
        fullSource += processedCode

        javaSources["gen.$name"] = fullSource

        return TestClass<Any>("gen.$name")
    }

    public fun types(packageName: String? = null, block: TypeSetBuilder.() -> Unit): AnnotatedTypeSet {
        requireNotCompiled()
        val builder = TypeSetBuilderRoot()
        builder.rootBlock.block()
        val set = TypeSetImpl(this, packageName?.let { "gen.$it" } ?: "gen", "__Types_${typeSets.size}", builder)
        typeSets.add(set)
        return set.annotated
    }

    public fun compile() {
        requireNotCompiled()
        for (set in typeSets) {
            javaSources[set.fullClassName] = set.generateClass(globalImports)
        }
        this.classLoader = Compile.compile(javaSources, CompileOptions().options(options))
    }

    public fun getClass(name: String): Class<*> {
        return Class.forName(name, true, requireCompiled())
    }

    private fun requireNotCompiled() {
        if (classLoader != null)
            throw IllegalStateException("The sources have already been compiled")
    }

    private fun requireCompiled(): ClassLoader {
        return classLoader
            ?: throw IllegalStateException("The sources have not been compiled")
    }

    /**
     * A delegate for a class declaration
     */
    public inner class TestClass<T>(private val name: String) {
        private var cache: Class<T>? = null

        @Suppress("UNCHECKED_CAST")
        public operator fun getValue(thisRef: Any?, property: KProperty<*>): Class<T> {
            cache?.also { return it }
            return (getClass(name) as Class<T>).also { cache = it }
        }

        /**
         * Gets a version of this class with the specified class type.
         */
        @Suppress("UNCHECKED_CAST")
        public fun <T> typed(): TestClass<T> = this as TestClass<T>
    }
}
