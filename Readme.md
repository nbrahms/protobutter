# Protoswole

A Scala library for automatic mapping between Scala objects and Google protobufs.

## Why?

Unlike, e.g., `scala-pb`, this is a mapper, not a code generator. It is useful when
your Scala code does not necessarily match your protobuf. For example, one might have
a protobuf with tens of optional fields, of which a single handler function might only
use one or two. Or, legacy protobufs might need to define no-longer-used deprecated
fields for backwards compatibility.

A mapper also cleans up code when optional fields are always expected to be present.
In this case a mapper can map fields to primitive types, avoiding `Option` manipulation.

## Usage

```scala
import org.nbrahms.protoswole._

case class Foo(fieldA: Int, fieldB: String)
val foo = protobuf.as[Foo]
val pb = foo.toPb[FooBuf]
```

Protobuf classes can be generated using the Java protoc compiler, or the sbt-protobuf
plugin.

## Compatibility

Currently, the following field types are supported:
- All primitives
- Inner messages
- Repeated types
- Map types

The following are not currently supported:
- Enum types
- Oneof types

## Requirements

Requires Scala macros. Add this dependency to your `build.sbt`:
```
addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
```
