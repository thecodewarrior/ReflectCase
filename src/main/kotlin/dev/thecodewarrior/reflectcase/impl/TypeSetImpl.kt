package dev.thecodewarrior.reflectcase.impl

import dev.thecodewarrior.reflectcase.AnnotatedTypeSet
import dev.thecodewarrior.reflectcase.GenericTypeSet
import dev.thecodewarrior.reflectcase.TestSources
import java.lang.reflect.AnnotatedType
import java.lang.reflect.Type

internal class TypeSetImpl(
    private val sources: TestSources,
    private val packageName: String,
    private val className: String,
    private val root: TypeSetBuilderRoot
) {
    val fullClassName: String = "$packageName.$className"

    val annotated: AnnotatedTypeSet = object: AnnotatedTypeSet {
        private val cache = mutableMapOf<String, AnnotatedType>()

        override val generic: GenericTypeSet
            get() = this@TypeSetImpl.generic

        override fun get(name: String): AnnotatedType =
            cache.getOrPut(name) { findDefinition(name).getAnnotated(holder) }

        override fun getGeneric(name: String): Type = generic[name]
    }
    val generic: GenericTypeSet = object: GenericTypeSet {
        private val cache = mutableMapOf<String, Type>()

        override val annotated: AnnotatedTypeSet
            get() = this@TypeSetImpl.annotated

        override fun get(name: String): Type =
            cache.getOrPut(name) { findDefinition(name).getGeneric(holder) }

        override fun getAnnotated(name: String): AnnotatedType = annotated[name]
    }

    fun findDefinition(name: String): TypeDefinition {
        return root.definitions[name] ?: throw IllegalArgumentException("No such type found: `$name`")
    }

    val holder: Class<*> by lazy {
        sources.getClass(fullClassName)
    }

    fun generateClass(globalImports: List<String>): String {
        var classText = ""
        classText += "package $packageName;\n"
        if (packageName != "gen") {
            classText += "import gen.*;"
        }
        classText += globalImports.joinToString("") { "import $it;" }
        classText += root.imports.joinToString("") { "import $it;" }
        classText += "\n"
        classText += root.generateClass(className)
        return classText
    }
}
