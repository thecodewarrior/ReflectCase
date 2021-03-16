package dev.thecodewarrior.reflectcase

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaMethod

/**
 * The base class for reflection tests, which provides access to systems for runtime compilation, as well as extensions
 * for easily accessing Core Reflection objects.
 *
 * Types can be initialized in the class's constructor using [sources], and these will be automatically compiled before
 * any of the tests run. Each test method also receives a fresh value in [sources], so they can configure local types.
 * Note that these types know nothing about the types in the constructor, and that you must explicitly compile them
 * before using them.
 *
 * ## Basic ReflectTest usage
 * ```kotlin
 * internal class SomeTest: ReflectTest() {
 *     val A by sources.add("A", "@rt(TYPE_USE) @interface A {}")
 *     val X by sources.add("X", "class X {}")
 *     val Generic by sources.add("Generic", "class Generic<T> {}")
 *
 *     val types = sources.types {
 *         +"X[]"
 *         +"Generic<X>[]"
 *         +"@A X @A []"
 *         +"Generic<@A X>[]"
 *         block("K", "V") {
 *             +"K[]"
 *             +"@A Generic<V>"
 *         }
 *     }
 *
 *     @Test
 *     fun `methods should not override themselves`() {
 *         val X by sources.add("X", "public class X { public void method() {} }")
 *         sources.compile()
 *         assertFalse(Mirror.reflect(X._m("method")).doesOverride(X._m("method")))
 *     }
 * }
 * ```
 *
 * ## Basic `TestSources` usage
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
@Suppress("PropertyName", "FunctionName", "MemberVisibilityCanBePrivate")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ReflectTest {
    /**
     * Called before each test and after [sources] has been reset
     */
    protected open fun initializeForTest() {}

    /**
     * The value configured in the constructor is compiled, then each test individually gets its own instance.
     */
    protected val sources: TestSources get() = _sources

    // storage is in a separate property to get rid of IntelliJ's irritating underline on something that's effectively
    // constant in each context it's used.
    private var _sources: TestSources = TestSources()

    /** Get a Class instance */
    protected inline fun <reified T> _c(): Class<T> = T::class.java

    protected val _boolean: Class<*> = Boolean::class.javaPrimitiveType!!
    protected val _byte: Class<*> = Byte::class.javaPrimitiveType!!
    protected val _char: Class<*> = Char::class.javaPrimitiveType!!
    protected val _short: Class<*> = Short::class.javaPrimitiveType!!
    protected val _int: Class<*> = Int::class.javaPrimitiveType!!
    protected val _long: Class<*> = Long::class.javaPrimitiveType!!
    protected val _float: Class<*> = Float::class.javaPrimitiveType!!
    protected val _double: Class<*> = Double::class.javaPrimitiveType!!
    protected val _object: Class<*> = Any::class.java

    /**
     * Get the specified method from this class. If no parameters are specified and no zero-parameter method exists, the
     * only one with the passed name is returned. Throws if no matching methods exist or multiple matching methods exist
     */
    protected fun Class<*>._m(name: String, vararg parameters: Class<*>): Method =
        EasyReflect.findMethod(this, name, *parameters)

    /**
     * Get the specified constructor from this class. If no parameters are specified and no zero-parameter constructor
     * exists, the only constructor is returned. Throws if no matching constructors exist or if no parameters were
     * passed and there are multiple constructors.
     */
    protected fun Class<*>._constructor(vararg parameters: Class<*>): Constructor<*> =
        EasyReflect.findConstructor(this, *parameters)

    /**
     * Get the specified field from this class.
     */
    protected fun Class<*>._f(name: String): Field = this.getDeclaredField(name)

    /**
     * Get the specified inner class from this class.
     */
    protected fun Class<*>._class(name: String): Class<*> = EasyReflect.getClass(this, name)

    /**
     * Creates a new instance using the passed arguments. Throws an exception if multiple constructors could be called
     * with the specified argument types.
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <T> Class<*>._new(vararg arguments: Any?): T = EasyReflect.newInstance(this, *arguments)

    /**
     * Invokes this constructor, ensuring it's accessible before doing so.
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <T> Constructor<*>._newInstance(vararg arguments: Any?): T =
        EasyReflect.newInstance(this, *arguments)

    /**
     * Invokes this method, ensuring it's accessible before doing so.
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <T> Method._call(instance: Any?, vararg arguments: Any?): T =
        EasyReflect.call(this, instance, *arguments)

    /**
     * Call the specified method from this object by searching for methods with parameters that are compatible with the
     * passed arguments. Throws if no matching methods exist or multiple matching methods exist.
     */
    protected fun <T> Any._call(name: String, vararg arguments: Any?): T =
        EasyReflect.findAndCall(this, name, *arguments)

    /**
     * Call the specified static method from this class by searching for methods with parameters that are compatible
     * with the passed arguments. Throws if no matching methods exist or multiple matching methods exist.
     */
    protected fun <T> Class<*>._call(name: String, vararg arguments: Any?): T =
        EasyReflect.findAndCallStatic(this, name, *arguments)

    /**
     * Get the value of this field, ensuring it's accessible before doing so.
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <T> Field._get(instance: Any?): T = EasyReflect.get(this, instance)

    /**
     * Get the value of this field, ensuring it's accessible before doing so.
     */
    protected fun Field._set(instance: Any?, value: Any?): Unit = EasyReflect.set(this, instance, value)

    /**
     * Get the value of the specified field from this object.
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <T> Any._get(name: String): T = EasyReflect.findAndGet(this, name)

    /**
     * Get the value of the specified field from this object.
     */
    protected fun Any._set(name: String, value: Any?): Unit = EasyReflect.findAndSet(this, name, value)

    /**
     * Get the value of the specified static field from this object.
     */
    @Suppress("UNCHECKED_CAST")
    protected fun <T> Class<*>._get(name: String): T = EasyReflect.findAndGetStatic(this, name)

    /**
     * Get the value of the specified static field from this object.
     */
    protected fun Class<*>._set(name: String, value: Any?): Unit = EasyReflect.findAndSetStatic(this, name, value)

    /**
     * Shorthand to easily get the backing method for a KFunction that represents a method
     */
    protected val KFunction<*>.m: Method get() = this.javaMethod!!

    /**
     * Shorthand to easily get the backing constructor for a KFunction that represents a constructor
     */
    protected val KFunction<*>.c: Constructor<*> get() = this.javaConstructor!!

    @BeforeAll
    private fun compileSources() {
        sources.compile()
    }

    @BeforeEach
    private fun beforeEachTest() {
        _sources = TestSources()
        this.initializeForTest()
    }
}