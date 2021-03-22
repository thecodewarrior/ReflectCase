package dev.thecodewarrior.reflectcase.impl

import dev.thecodewarrior.reflectcase.TypeSet
import dev.thecodewarrior.reflectcase.TestSources
import dev.thecodewarrior.reflectcase.TypeSetBuilder
import dev.thecodewarrior.reflectcase.joor.Compile
import dev.thecodewarrior.reflectcase.joor.CompileOptions
import java.lang.reflect.AnnotatedType
import java.lang.reflect.Type
import kotlin.reflect.KProperty

internal class TestSourcesImpl: TestSources {
    private val javaSources = mutableMapOf<String, String>()
    private var nextTypeSet = 0
    private var classLoader: ClassLoader? = null

    override val compilerOptions: MutableList<String> = mutableListOf("-parameters")

    override val globalImports: MutableList<String> = mutableListOf(
        "java.util.*",
        "java.lang.annotation.ElementType",
        "java.lang.annotation.Target",
        "java.lang.annotation.Retention",
        "java.lang.annotation.RetentionPolicy",
        "dev.thecodewarrior.reflectcase.impl.TestSourceNopException"
    )

    override fun add(name: String, code: String, trimIndent: Boolean): TestSources.ClassDelegate<*> {
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

        return getDelegate<Any>("gen.$name")
    }

    override fun types(packageName: String?, block: TypeSetBuilder.() -> Unit): TypeSet {
        requireNotCompiled()
        val fullPackage = packageName?.let { "gen.$it" } ?: "gen"
        val className = "__Types_${nextTypeSet++}"
        val fullClassName = "$fullPackage.$className"

        val builder = TypeSetBuilderRoot()
        builder.rootBlock.block()

        var classText = ""
        classText += "package $packageName;\n"
        if (packageName != "gen") {
            classText += "import gen.*;"
        }
        classText += globalImports.joinToString("") { "import $it;" }
        classText += builder.imports.joinToString("") { "import $it;" }
        classText += "\n"
        classText += builder.generateClass(className)

        javaSources[fullClassName] = classText

        return TypeSetImpl(fullClassName, builder.definitions)
    }

    override fun compile() {
        requireNotCompiled()
        this.classLoader = Compile.compile(javaSources, CompileOptions().options(compilerOptions))
    }

    override fun getClass(name: String): Class<*> {
        requireCompiled()
        return Class.forName(name, true, classLoader!!)
    }

    override fun <T> getDelegate(name: String): TestSources.ClassDelegate<T> {
        return ClassDelegateImpl(name)
    }

    private fun requireNotCompiled() {
        if (classLoader != null)
            throw IllegalStateException("The sources have already been compiled")
    }

    private fun requireCompiled() {
        if(classLoader == null)
            throw IllegalStateException("The sources have not been compiled")
    }

    private inner class ClassDelegateImpl<T>(private val name: String): TestSources.ClassDelegate<T> {
        private var cache: Class<T>? = null

        @Suppress("UNCHECKED_CAST")
        override fun get(): Class<T> {
            cache?.also { return it }
            return (getClass(name) as Class<T>).also { cache = it }
        }

        override operator fun getValue(thisRef: Any?, property: KProperty<*>): Class<T> {
            return get()
        }

        /**
         * Gets a version of this class with the specified class type.
         */
        @Suppress("UNCHECKED_CAST")
        override fun <T> typed(): TestSources.ClassDelegate<T> = this as TestSources.ClassDelegate<T>
    }

    private inner class TypeSetImpl(
        fullClassName: String,
        val definitions: MutableMap<String, TypeDefinition>
    ): TypeSet {
        val holder: Class<*> by lazy { this@TestSourcesImpl.getClass(fullClassName) }

        private val cache = mutableMapOf<String, AnnotatedType>()

        override fun get(name: String): AnnotatedType =
            cache.getOrPut(name) { findDefinition(name).getAnnotated(holder) }

        fun findDefinition(name: String): TypeDefinition {
            return definitions[name] ?: throw IllegalArgumentException("No such type found: `$name`")
        }

        override val genericTypes: TypeSet.GenericTypeSet = object: TypeSet.GenericTypeSet {
            private val cache = mutableMapOf<String, Type>()

            override val fullTypes: TypeSet
                get() = this@TypeSetImpl

            override fun get(name: String): Type =
                cache.getOrPut(name) { findDefinition(name).getGeneric(holder) }
        }
    }
}
