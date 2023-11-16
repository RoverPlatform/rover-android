# Rover μReactive Extensions (Deprecated)

A simple implementation of Rx in Kotlin that conforms to 
the Reactive Streams standard Publisher interface
(https://www.reactive-streams.org/) in Android/Java-land.

This was done in order to avoid a big, complicated transitive dependency like RxJava.

## Migration Path

This approach has been made redundant by the emergence of Kotlin
Coroutines and Flow.

Migrating to Flow can be done incrementally thanks to the  
`kotlinx-coroutines-reactive` library, which can bridge Publishers
into Flows and vice versa.
