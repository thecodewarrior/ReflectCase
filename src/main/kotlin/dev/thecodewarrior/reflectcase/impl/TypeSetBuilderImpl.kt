package dev.thecodewarrior.reflectcase.impl

import dev.thecodewarrior.reflectcase.TypeSetBuilder
import java.lang.reflect.AnnotatedParameterizedType
import java.lang.reflect.AnnotatedType
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

internal class TypeSetBuilderRoot {
    val definitions: MutableMap<String, TypeDefinition> = mutableMapOf()
    val imports: MutableSet<String> = mutableSetOf()

    var nextBlockIndex: Int = 0
    val rootBlock = TypeSetBuilderImpl(this, null, nextBlockName())

    fun nextBlockName(): String {
        return "block_${nextBlockIndex++}"
    }

    fun generateClass(className: String): String {
        var bodyText = ""
        bodyText += rootBlock.createClassText()
        bodyText += "\nfinal class __<T> {}"

        return "class $className {\n${bodyText.prependIndent("    ")}\n}"
    }
}

internal class TypeSetBuilderImpl(
    private val root: TypeSetBuilderRoot,
    private val parent: TypeSetBuilderImpl?,
    private val blockName: String,
    private vararg val variables: String
): TypeSetBuilder {
    private val definitions: MutableList<TypeDefinition> = mutableListOf()
    private val children: MutableList<TypeSetBuilderImpl> = mutableListOf()

    override fun import(vararg imports: String) {
        root.imports.addAll(imports)
    }

    override fun String.unaryPlus(): Unit = add(this, this)

    override fun add(name: String, type: String) {
        if (name in root.definitions)
            throw IllegalArgumentException("A type named `$name` already exists")
        val def = TypeDefinition(this, definitions.size, name, type)
        definitions.add(def)
        root.definitions[name] = def
    }

    override fun block(vararg variables: String, config: TypeSetBuilder.() -> Unit) {
        val block = TypeSetBuilderImpl(root, this, root.nextBlockName(), *variables)
        block.config()
        children.add(block)
    }

    fun createClassText(): String {
        var classText = ""

        classText += "class $blockName"
        if (variables.isNotEmpty())
            classText += "<${variables.joinToString(", ")}>"
        classText += " {\n"

        var bodyText = ""
        bodyText += definitions.joinToString("\n") { def ->
            def.createClassText()
        }

        if (definitions.isNotEmpty() && children.isNotEmpty()) {
            bodyText += "\n"
        }

        bodyText += children.joinToString("\n") { block ->
            block.createClassText()
        }

        classText += bodyText.prependIndent("    ")
        classText += "\n}"

        return classText
    }

    fun getClass(rootClass: Class<*>): Class<*> {
        val parentClass = this.parent?.getClass(rootClass) ?: rootClass
        return parentClass.declaredClasses.find { it.simpleName == blockName }
            ?: throw IllegalStateException("Unable to find block `$blockName`")
    }
}

internal data class TypeDefinition(val block: TypeSetBuilderImpl, val index: Int, val name: String, val type: String) {
    private val fieldName = "type_${index}"

    fun getAnnotated(rootClass: Class<*>): AnnotatedType {
        return (getField(rootClass).annotatedType as AnnotatedParameterizedType).annotatedActualTypeArguments[0]
    }

    fun getGeneric(rootClass: Class<*>): Type {
        return (getField(rootClass).genericType as ParameterizedType).actualTypeArguments[0]
    }

    fun getField(rootClass: Class<*>): Field {
        val blockClass = block.getClass(rootClass)

        return blockClass.getDeclaredField(fieldName)
            ?: throw IllegalStateException("Unable to find field $fieldName in type block")
    }

    fun createClassText(): String {
        val fieldText = "__<$type> $fieldName;"
        val commentContinuation = "\n" + " ".repeat(fieldText.length) + " // "
        return "$fieldText // ${name.replace("\n", commentContinuation)}"
    }
}
