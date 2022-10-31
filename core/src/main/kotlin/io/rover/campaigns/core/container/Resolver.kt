package io.rover.campaigns.core.container

import org.reactivestreams.Publisher

/**
 * Can resolve live instances of objects for a given type.
 */
interface Resolver {
    /**
     * Retrieve an object for the given type and name.  The appropriate registered factory
     * will yield the object, possibly applying rules like memoization depending on how
     * it was configured at registration time.
     */
    fun <T : Any> resolve(type: Class<T>, name: String? = null): T?

    /**
     * Retrieve an object for the given type and name.  The appropriate registered factory
     * will yield the object, possibly applying rules like memoization depending on how
     * it was configured at registration time.
     *
     * Dot not use this method to resolve objects registered with [Scope.Singleton] scope, because
     * they must not accept arguments.
     */
    fun <T : Any, Arg1> resolve(type: Class<T>, name: String?, arg1: Arg1): T?

    /**
     * Retrieve an object for the given type and name.  The appropriate registered factory
     * will yield the object, possibly applying rules like memoization depending on how
     * it was configured at registration time.
     *
     * Dot not use this method to resolve objects registered with [Scope.Singleton] scope, because
     * they must not accept arguments.
     */
    fun <T : Any, Arg1, Arg2> resolve(type: Class<T>, name: String?, arg1: Arg1, arg2: Arg2): T?

    /**
     * Retrieve an object for the given type and name.  The appropriate registered factory
     * will yield the object, possibly applying rules like memoization depending on how
     * it was configured at registration time.
     *
     * Dot not use this method to resolve objects registered with [Scope.Singleton] scope, because
     * they must not accept arguments.
     */
    fun <T : Any, Arg1, Arg2, Arg3> resolve(type: Class<T>, name: String?, arg1: Arg1, arg2: Arg2, arg3: Arg3): T?

    /**
     * Retrieve an object for the given type and name.  The appropriate registered factory
     * will yield the object, possibly applying rules like memoization depending on how
     * it was configured at registration time.
     *
     * Only suitable for singletons.
     */
    fun <T : Any> resolveSingletonOrFail(type: Class<T>, name: String? = null): T {
        // return resolve(type) ?: throw RuntimeException("Could not resolve item of type ${type.name}.  Ensure that what you are asking for is the Interface of a Plugin, such as DataPluginInterface.")
        val byName = if (name == null) "" else " named '$name'"
        return resolve(type, name) ?: throw RuntimeException("DI container cannot resolve type '${type.name}'$byName.  Make sure you've added all the assemblers you need to Rover.initialize().")
    }

    data class Activation<T>(
        val type: Class<T>,
        val instance: T,
        val name: String?
    )

    /**
     * Be informed when an object for the given type has been instantiated.  Only works
     * with objects that were registered with no arguments.
     */
    val activations: Publisher<Activation<*>>
}
