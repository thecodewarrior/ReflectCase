package dev.thecodewarrior.reflectcase

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

public object EasyReflect {
    /**
     * Get the specified method from this class. If no parameters are specified (and that didn't match a zero-parameter
     * method), the only one with the passed name is returned. Throws if no matching methods exist or multiple matching
     * methods exist.
     */
    public fun findMethod(target: Class<*>, name: String, vararg parameters: Class<*>): Method {
        if (parameters.isEmpty()) {
            val methods = target.declaredMethods.filter { it.name == name }
            methods.find { it.parameterCount == 0 }?.let { return it }
            if (methods.size != 1) {
                throw IllegalArgumentException("Found ${methods.size} candidates for method named `$name`")
            }
            return methods.first()
        } else {
            return target.getDeclaredMethod(name, *parameters)
        }
    }

    /**
     * Get the specified constructor from this class. If no parameters are specified (and that didn't match a
     * zero-parameter constructor), the only constructor is returned. Throws if no matching constructors exist or if no
     * parameters were passed and there are multiple constructors.
     */
    public fun findConstructor(target: Class<*>, vararg parameters: Class<*>): Constructor<*> {
        if (parameters.isEmpty()) {
            target.declaredConstructors.find { it.parameterCount == 0 }?.let { return it }
            if (target.declaredConstructors.size != 1) {
                throw IllegalArgumentException("Found ${target.declaredConstructors.size} constructors when looking for the " +
                    "only constructor")
            }
            return target.declaredConstructors.first()
        } else {
            return target.getDeclaredConstructor(*parameters)
        }
    }

    /**
     * Get the specified inner class from this class.
     */
    public fun getClass(target: Class<*>, name: String): Class<*> = target.declaredClasses.find { it.simpleName == name }
        ?: throw IllegalArgumentException("Couldn't find declared class $name in $target")

    /**
     * Creates a new instance using the passed arguments. Throws an exception if multiple constructors could be called
     * with the specified argument types.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T> newInstance(target: Class<*>, vararg arguments: Any?): T {
        val constructors = target.declaredConstructors.filter {
            it.name == target.name && !Modifier.isStatic(it.modifiers) &&
                it.parameterCount == arguments.size && it.parameterTypes.zip(arguments).all { (p, a) ->
                p.isAssignableFrom(a?.javaClass ?: Any::class.java)
            }
        }
        if (constructors.size != 1) {
            throw IllegalArgumentException("Found ${constructors.size} candidates for constructor with parameter " +
                "types `${arguments.joinToString(", ") { it?.javaClass?.simpleName ?: "null" }}")
        }
        return newInstance(constructors.single(), *arguments)
    }

    /**
     * Invokes this constructor, ensuring it's accessible before doing so.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T> newInstance(constructor: Constructor<*>, vararg arguments: Any?): T {
        return constructor.also { it.isAccessible = true }.newInstance(*arguments) as T
    }

    /**
     * Invokes this method, ensuring it's accessible before doing so.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T> call(method: Method, instance: Any?, vararg arguments: Any?): T {
        return method.also { it.isAccessible = true }.invoke(instance, *arguments) as T
    }

    /**
     * Call the specified method from this object by searching for methods with parameters that are compatible with the
     * passed arguments. Throws if no matching methods exist or multiple matching methods exist.
     */
    public fun <T> findAndCall(instance: Any, name: String, vararg arguments: Any?): T {
        val methods = instance.javaClass.declaredMethods.filter {
            it.name == name && !Modifier.isStatic(it.modifiers) &&
                it.parameterCount == arguments.size && it.parameterTypes.zip(arguments).all { (p, a) ->
                p.isAssignableFrom(a?.javaClass ?: Any::class.java)
            }
        }
        if (methods.size != 1) {
            throw IllegalArgumentException("Found ${methods.size} candidates for method named `$name` with parameter " +
                "types `${arguments.joinToString(", ") { it?.javaClass?.simpleName ?: "null" }}")
        }
        return call(methods.single(), this, *arguments)
    }

    /**
     * Call the specified static method from this class by searching for methods with parameters that are compatible
     * with the passed arguments. Throws if no matching methods exist or multiple matching methods exist.
     */
    public fun <T> findAndCallStatic(target: Class<*>, name: String, vararg arguments: Any?): T {
        val methods = target.declaredMethods.filter {
            it.name == name && Modifier.isStatic(it.modifiers)
            it.parameterCount == arguments.size && it.parameterTypes.zip(arguments).all { (p, a) ->
                p.isAssignableFrom(a?.javaClass ?: Any::class.java)
            }
        }
        if (methods.size != 1) {
            throw IllegalArgumentException("Found ${methods.size} candidates for method named `$name` with parameter " +
                "types `${arguments.joinToString(", ") { it?.javaClass?.simpleName ?: "null" }}")
        }
        return call(methods.single(), null, arguments)
    }

    /**
     * Get the value of this field, ensuring it's accessible before doing so.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T> get(field: Field, instance: Any?): T = field.also { it.isAccessible = true }.get(instance) as T

    /**
     * Get the value of this field, ensuring it's accessible before doing so.
     */
    public fun set(field: Field, instance: Any?, value: Any?): Unit = field.also { it.isAccessible = true }.set(instance, value)

    /**
     * Get the value of the specified field from this object.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T> findAndGet(instance: Any, name: String): T = get(instance.javaClass.getDeclaredField(name), this)

    /**
     * Get the value of the specified field from this object.
     */
    public fun findAndSet(instance: Any, name: String, value: Any?): Unit = set(instance.javaClass.getDeclaredField(name), this, value)

    /**
     * Get the value of the specified static field from this object.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T> findAndGetStatic(target: Class<*>, name: String): T = get(target.getDeclaredField(name), null)

    /**
     * Get the value of the specified static field from this object.
     */
    public fun findAndSetStatic(target: Class<*>, name: String, value: Any?): Unit = set(target.getDeclaredField(name), null, value)
}