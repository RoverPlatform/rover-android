/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.core.container

/**
 * Contains a mapping of factories to object types.
 */
interface Container {
    /**
     * Initialize the container.
     */
    fun initializeContainer()

    /**
     * Register a [factory] with this Container that will instantiate objects for the given [type].
     * The Factory is a simple closure that, given only a [Resolver] (an interface by which it can
     * acquire its dependencies), will instantiate the item of the supplied type.
     */
    fun <T : Any> register(scope: Scope, type: Class<T>, name: String? = null, factory: (Resolver) -> T)

    /**
     * Register a [factory] with this Container that will instantiate objects for the given [type].
     * The Factory is a simple closure that, given only a [Resolver] (an interface by which it can
     * acquire its dependencies), will instantiate the item of the supplied type.
     */
    fun <T : Any, Arg1> register(scope: Scope, type: Class<T>, name: String? = null, factory: (Resolver, Arg1) -> T)

    /**
     * Register a [factory] with this Container that will instantiate objects for the given [type].
     * The Factory is a simple closure that, given only a [Resolver] (an interface by which it can
     * acquire its dependencies), will instantiate the item of the supplied type.
     */
    fun <T : Any, Arg1, Arg2> register(scope: Scope, type: Class<T>, name: String? = null, factory: (Resolver, Arg1, Arg2) -> T)

    /**
     * Register a [factory] with this Container that will instantiate objects for the given [type].
     * The Factory is a simple closure that, given only a [Resolver] (an interface by which it can
     * acquire its dependencies), will instantiate the item of the supplied type.
     */
    fun <T : Any, Arg1, Arg2, Arg3> register(scope: Scope, type: Class<T>, name: String? = null, factory: (Resolver, Arg1, Arg2, Arg3) -> T)
}

enum class Scope {
    /**
     * The registered factory will instantiate the item once and then memoize it, then continuing to
     * yield the same instance.
     *
     * May not support any factory arguments (which are only intended for
     */
    Singleton,

    /**
     * The registered factory will instantiate the item on every evaluation.
     *
     * Supports arguments, in order to allow for dynamic creation
     */
    Transient

    // TODO: perhaps add what Kodein refers to as a "multiton", which basically means singletons
    // that are memoized on the basis of the triple of their name, type, AND args rather than just
    // their name and type.  This may be useful for things like view models which contain some
    // state and need to be bound in more than one place.
}
