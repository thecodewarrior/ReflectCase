package dev.thecodewarrior.reflectcase

import dev.thecodewarrior.reflectcase.impl.TestSourcesImpl
import org.intellij.lang.annotations.Language
import kotlin.reflect.KProperty

/**
 * Runtime compilation of test cases.
 *
 * ## Basic usage
 * ```kotlin
 * val sources = TestSources.create()
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
public interface TestSources {

    public companion object {
        /**
         * Create a new [TestSources] instance
         */
        @JvmStatic
        public fun create(): TestSources {
            return TestSourcesImpl()
        }
    }

    /**
     * Additional command-line arguments for the Java compiler. Includes `-parameters` by default
     */
    public val compilerOptions: MutableList<String>

    /**
     * Imports to insert at the top of every file. This must be set *before* [add] is called for it to have any effect.
     */
    public val globalImports: MutableList<String>

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
    public fun add(name: String, @Language("java") code: String, trimIndent: Boolean = true): ClassDelegate<*>

    /**
     * Create an [TypeSet] using the provided builder
     */
    public fun types(packageName: String? = null, block: TypeSetBuilder.() -> Unit): TypeSet

    /**
     * Compile the classes
     */
    public fun compile()

    /**
     * Get a class based on its fully-qualified name
     */
    public fun getClass(name: String): Class<*>

    /**
     * Create a delegate for a class based on its fully-qualified name
     */
    public fun <T> getDelegate(name: String): ClassDelegate<T>

    /**
     * A lazy delegate to refer to a runtime-compiled class
     */
    public interface ClassDelegate<T> {
        public fun get(): Class<T>
        public operator fun getValue(thisRef: Any?, property: KProperty<*>): Class<T>
        public fun <T> typed(): ClassDelegate<T>
    }
}