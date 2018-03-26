package org.nbrahms.protoswole

import scala.reflect.macros.whitebox.Context

class Macros(val c: Context) {
  import c.universe._

  /**Contains all the necessary reflective info for a message field
   */
  private case class Field(v: c.Symbol) {
    /**Type of the POJO field */
    val tpe = v.typeSignature
    /**Name of the POJO field */
    val name = v.name.toString
    /**Upper-cased name of the POJO field */
    val upperName = (name take 1).toUpperCase + (name drop 1)
    /**How this field is get and set on a message */
    def extractor(message: c.Type) = ExtractType(this, message)
  }

  private object ExtractType {
    def apply(f: Field, message: c.Type): ExtractType = {
      if (f.tpe <:< weakTypeOf[Seq[_]] && !(f.tpe.typeArgs(0) <:< weakTypeOf[Byte])) {
        SeqType(f, message)
      } else if (f.tpe <:< weakTypeOf[Map[_, _]]) {
        MapType(f, message)
      } else if (f.tpe <:< weakTypeOf[Option[_]]) {
        OptionType(f, message)
      } else {
        RequiredType(f, message)
      }
    }
  }
  /**Defines POJO-to-protobuf mapping for a type
   */
  private sealed trait ExtractType {
    /**Constructs the AST to get the POJO field from a Protobuf */
    def getterTree(in: c.Tree): c.Tree
    /**Augments the builder AST by setting the POJO field on the builder */
    def setterTree(in: c.Tree, value: c.Tree): c.Tree
  }

  /**Maps fields whose presence is expected in the protobuf
   *
   * Can correspond to either the "required" or "optional" keyword. The getter
   * AST will throw a runtime NoSuchElementException if the protobuf field is
   * optional and not present.
   */
  private case class RequiredType(f: Field, message: c.Type) extends ExtractType {
    val getter = declFor(message, s"get${f.upperName}")
    val resType = getter.info.resultType
    def getterTree(in: c.Tree) = {
      q"""
        if ($in.${declFor(message, s"has${f.upperName}")}) {
          ${convertAs(f.tpe, resType, q"$in.$getter")}
        } else throw new NoSuchElementException(${f.name})
      """
    }
    def setterTree(in: c.Tree, value: c.Tree): c.Tree = {
      q"""
        $in.${declFor(message, s"set${f.upperName}")}(
          ${convertToPb(f.tpe, resType, value)}
        )
      """
    }
  }

  /**Maps fields that can repeat in the protobuf
   *
   * Corresponds to the "repeated" keyword.
   */
  private case class SeqType(f: Field, message: c.Type) extends ExtractType {
    val getter = declFor(message, s"get${f.upperName}List")
    val resType = getter.info.resultType.typeArgs(0)
    val inner = f.tpe.typeArgs(0)
    def getterTree(in: c.Tree) = {
      val convo = convertAs(inner, resType, q"x")
      q"""
        scala.collection.JavaConverters.asScalaBufferConverter($in.$getter).asScala.map { x => $convo }.toSeq
      """
    }
    def setterTree(in: c.Tree, value: c.Tree): c.Tree = {
      val inner = f.tpe.typeArgs(0)
      q"""
        $in.${declFor(message, s"addAll${f.upperName}")}(
          scala.collection.JavaConverters.asJavaIterableConverter(
            $value.map { v => ${convertToPb(inner, resType, q"v")} }
          ).asJava
        )
      """
    }
  }

  /**Maps "Map" fields in the protobof
   *
   * Each "Map" is a repeated message, which itself has a "key" and a
   * "value" field.
   */
  private case class MapType(f: Field, message: c.Type) extends ExtractType {
    val getter = declFor(message, s"get${f.upperName}List")
    val resType = getter.info.resultType.typeArgs(0)
    val keyType = f.tpe.typeArgs(0)
    val valueType = f.tpe.typeArgs(1)
    val keyResType = declFor(resType, s"getKey").info.resultType
    val valueResType = declFor(resType, s"getValue").info.resultType
    def getterTree(in: c.Tree) = {
      val keyConvo = convertAs(keyType, keyResType, q"kv.getKey")
      val valueConvo = convertAs(valueType, valueResType, q"kv.getValue")
      q"""
        scala.collection.JavaConverters.asScalaBufferConverter($in.$getter).asScala.map {
          kv => ($keyConvo, $valueConvo)
        }.toMap
      """
    }
    def setterTree(in: c.Tree, value: c.Tree): c.Tree = {
      val companion = resType.companion
      val keyConvo = convertToPb(keyType, keyResType, q"kv._1")
      val valueConvo = convertToPb(valueType, valueResType, q"kv._2")
      q"""
        $in.${declFor(message, s"addAll${f.upperName}")}(
          scala.collection.JavaConverters.asJavaIterableConverter(
            $value.map { kv =>
              $companion.newBuilder().setKey($keyConvo).setValue($valueConvo).build()
            }
          ).asJava
        )
      """
    }
  }

  /**Maps fields whose presence is optional in the POJO
   *
   * Can be used with either the "required" or "optional" keyword;
   * if "required", the POJO field will always be defined.
   */
  private case class OptionType(f: Field, message: c.Type) extends ExtractType {
    val getter = declFor(message, s"get${f.upperName}")
    val resType = getter.info.resultType
    val inner = f.tpe.typeArgs(0)
    def getterTree(in: c.Tree) = {
      q"""
        if ($in.${declFor(message, s"has${f.upperName}")}) {
          Some(${convertAs(inner, resType, q"$in.$getter")})
        } else None
      """
    }
    def setterTree(in: c.Tree, value: c.Tree): c.Tree = {
      q"""
        {
          val res = $in
          if ($value.isDefined) {
            res.${declFor(message, s"set${f.upperName}")}(${convertToPb(inner, resType, q"$value.get")})
          }
          res
        }
      """
    }
  }

  /**Defines type deserialization */
  private def convertAs(fTpe: c.Type, resTpe: c.Type, in: c.Tree): c.Tree = {
    if (fTpe <:< weakTypeOf[Seq[Byte]]) byteConvertAs(in)
    else if (fTpe <:< weakTypeOf[AnyVal] || fTpe <:< weakTypeOf[String]) in
    else deserializePojo(resTpe, fTpe, in)
  }
  private def byteConvertAs(in: c.Tree) = q"$in.toByteArray.toSeq"

  /**Defines type serialization */
  private def convertToPb(fTpe: c.Type, resTpe: c.Type, in: c.Tree): c.Tree = {
    if (fTpe <:< weakTypeOf[Seq[Byte]]) byteConvertToPb(in)
    else if (fTpe <:< weakTypeOf[AnyVal] || fTpe <:< weakTypeOf[String]) in
    else serializePojo(resTpe, fTpe, in)
  }
  private def byteConvertToPb(in: c.Tree) =
    q"com.google.protobuf.ByteString.copyFrom($in.toArray)"

  private def abort(message: String): Nothing = c.abort(c.enclosingPosition, message)
  private def constructor(t: c.Type): c.Type = t.decl(TermName("<init>")).typeSignatureIn(t)
  private def paramsFor(t: c.Type): Seq[c.Symbol] = {
    constructor(t).paramLists match {
      case Seq(p) => p
      case _ => abort(s"Found multiple matching constructors for $t")
    }
  }
  private def declFor(message: c.Type, method: String): c.Symbol = {
    val symbol = message.decl(TermName(method))
    if (symbol == NoSymbol) {
      abort(s"Could not find method '$method' in class ${message.typeSymbol.fullName}")
    }
    symbol
  }

  /**Deserializes an object based on its constructor arguments
   *
   * Each constructor argument is assumed to correspond to a snake-case
   * field of the same name and a valid corresponding type.
   */
  private def deserializePojo(message: c.Type, t: c.Type, in: c.Tree): c.Tree = {
    val fields = paramsFor(t) map Field.apply
    if (fields.isEmpty) abort(s"${t.termSymbol.name} has no constructor fields")
    val memo = TermName(c.freshName("in"))
    q"""
      {
        val $memo = $in
        new ${t}(
          ..${fields map (f => f.extractor(message).getterTree(q"$memo"))}
        )
      }
    """
  }
  /**Serializes an object based on its public constructor arguments
   *
   * Each _public_ constructor argument is assumed to correspond to a
   * snake-case field of the same name and a valid corresponding type.
   */
  private def serializePojo(message: c.Type, t: c.Type, in: c.Tree): c.Tree = {
    val fields = paramsFor(t) filter (_.isPublic) map Field.apply
    if (fields.isEmpty) abort(s"${t.termSymbol.name} does not have any public constructor fields")
    val companion = message.companion
    val builder = companion.decl(TypeName("Builder")).asClass.toType
    val memo = TermName(c.freshName("in"))
    val setter = (fields foldLeft q"$companion.newBuilder()") {
      case (tree, f) =>
        val decl = t.decl(TermName(f.name))
        f.extractor(builder).setterTree(tree, q"$memo.$decl")
    }
    q"""
      val $memo = $in
      $setter.build()
    """
  }

  def materializeAs[M: WeakTypeTag, T: WeakTypeTag]: c.Expr[T] = {
    val mType = weakTypeOf[M]
    val tType = weakTypeOf[T]
    val tree = deserializePojo(mType, tType, q"${c.prefix}.pb")
    c.Expr[T](tree)
  }

  def materializeToPb[T: WeakTypeTag, M: WeakTypeTag]: c.Expr[M] = {
    val mType = weakTypeOf[M]
    val tType = weakTypeOf[T]
    val tree = serializePojo(mType, tType, q"${c.prefix}.value")
    c.Expr[M](tree)
  }
}
