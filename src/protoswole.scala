package org.nbrahms

package object protoswole {
  import scala.language.experimental.macros
  import scala.language.implicitConversions

  /**Provides deserialization operations on protobuf messages
   *
   * @param pb The protobuf to deserialize
   * @tparam M The protobuf message type
   */
  implicit class FromPbOps[M](val pb: M) {
    /**Deserializes a protobuf message into an object
     *
     * Deserialization is governed by the constructor arguments of {{{T}}}.
     * Each constructor argument must meet two criteria:
     * 1. Its name should correspond to a field in the protobuf
     * 2. Its type must correspond to that field, and be one of:
     *   - A deserializable primitive:
     *     - Boolean
     *     - Int
     *     - Long
     *     - Float
     *     - Double
     *     - String
     *     - Seq[Byte]
     *   - Another object whose constructor obeys these same criteria
     *     (corresponds to an inner message)
     *   - An [[Option]] of either of the above
     *   - A [[Seq]] of either of the first two (corresponds to a
     *     repeated field)
     *   - A [[Map]] of deserializable primitives or objects (see below)
     *
     * Note that the object is not required to have a constructor argument for
     * every field in the protobuf, and can have only one argument, if desired.
     *
     * Fails to compile if the protobuf does not have a corresponding field to a
     * passed type argument, or if the field's type does not match.
     *
     * Note that, while {{{Seq}}} arguments correspond to "repeated" protobuf
     * fields, no such alignment is required between {{{Option}}} arguments (or lack
     * thereof), and "required" vs. "optional" protobuf fields. The following
     * behavior instead occurs:
     *   - Non-{{{Option}}} arguments cause a [[NoSuchElementException]] to be
     *     thrown at runtime if an optional field is absent from the protobuf
     *   - {{{Option}}} arguments will always be deserialized as a [[Some]] for
     *     required fields
     *
     * To use with {{{Map}}} arguments, the protobuf must:
     *   - Have a repeated field with the same name as the argument
     *   - That repeated field must be a message type with {{{key}}} and {{{value}}}
     *     fields, with types corresponding to the {{{Map}}}'s types
     *
     * Fails to compile if the constructor argument type is a nested combination of
     * {{{Option}}}, {{{Seq}}}, or {{{Map}}}. Use nested messages to achieve such
     * additional complexity.
     *
     * Will only work with tuple arguments if you name your protobuf fields {{{_1}}},
     * etc.
     *
     * @tparam T The type of the deserialized object
     * @throws java.util.NoSuchElementException If a non-{{{Option}}} field is missing
     */
    def as[T]: T = macro protoswole.Macros.materializeAs[M, T]
  }

  /**Provides serialization operations on objects
   *
   * @param value The object to serialize
   * @tparam T The object type
   */
  implicit class ToPbOps[T](val value: T) {
    /**Serializes an object into a protobuf message
     *
     * This method uses the public constructor arguments of {{{T}}} to determine
     * the fields to serialize on {{{M}}}. Note that this differs from
     * [[FromPbOps.as]], which also uses private constructor arguments in
     * deserialization.
     *
     * Aside from the necessity of using public constructor arguments, the
     * mapping is determined in exactly the same manner as for [[FromPbOps.as]].
     *
     * @tparam M The type of protobuf to serialize
     */
    def toPb[M]: M = macro protoswole.Macros.materializeToPb[T, M]
  }
}