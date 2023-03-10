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
 * Assemblers define factories (themselves typically just closures) that can then provide
 * objects needed for given types in [Container]s.
 *
 * They are usually used to group together the registrations of multiple factories in a
 * given vertical concern.  Indeed, this could be considered analogous to a module in Dagger.
 */
interface Assembler {
    /**
     * Register any factories this Assembler can provide in the given [Container].
     *
     * This is where you define the bulk of the structure of the "module" this Assembler
     * is responsible for constructing.
     *
     * Your implementation should call [Container.register] with a factory closure for each of the
     * items you wish this Assembler to define.  It will look something like this:
     *
     * ```
     * override fun assemble(container: Container) {
     *     container.register(
     *         Scope.Singleton,
     *         MyThingerInterface::class.java
     *     ) { resolver ->
     *         MyThinger(
     *             resolver.resolveSingletonOrFail(MyDependency::class.java)
     *         )
     *     }
     * ```
     *
     * Note: when registering multiple items, be aware that you should not just store a reference to
     * a local sub-dependency in your [assemble] method's local scope. Instead, within each factory
     * closure, you should resolve it each time you need it within
     */
    fun assemble(container: Container) { }

    /**
     * Allow for any post-wireup side effects.
     */
    fun afterAssembly(resolver: Resolver) { }
}
