package io.rover.core.container

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
