package dev.thecodewarrior.reflectcase

import org.intellij.lang.annotations.Language
import java.lang.reflect.AnnotatedType
import java.lang.reflect.Type

public interface AnnotatedTypeSet {
    public val generic: GenericTypeSet
    public operator fun get(name: String): AnnotatedType
    public fun getGeneric(name: String): Type
}

public interface GenericTypeSet {
    public val annotated: AnnotatedTypeSet
    public operator fun get(name: String): Type
    public fun getAnnotated(name: String): AnnotatedType
}


@DslMarker
private annotation class TypeSetDSL

/**
 * ```kotlin
 * import("some.package.ClassName")
 * import("java.util.List")
 * add("name", "ClassName[]")
 * +"List<ClassName>"
 * block("K", "V") {
 *     +"Generic<K>"
 *     +"Generic<V>"
 * }
 * ```
 */
@TypeSetDSL
public interface TypeSetBuilder {
    public fun import(vararg imports: String)

    // @Language won't work until IDEA-263798 and IDEA-263799 are fixed
    public operator fun @receiver:Language("java", prefix = PFX, suffix = SFX) String.unaryPlus()
    public fun add(name: String, @Language("java", prefix = PFX, suffix = SFX) type: String)

    public fun block(vararg variables: String, config: TypeSetBuilder.() -> Unit)

    private companion object {
        // the prefix and suffix, extracted to constants to avoid clutter
        const val PFX = "class X { __<"
        const val SFX = "> field; }"
    }
}
