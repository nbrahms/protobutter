package org.nbrahms.protoswole

import org.scalatest._

class Spec extends WordSpec with Matchers {
  "as" should {
    "deserialize partially" in {
      val pb = Test.Inner.newBuilder.setFirst(1).build()
      case class Pair(first: Int)
      pb.as[Pair] should equal (Pair(1))
    }
    "deserialize primitives" when {
      "ints" in {
        val pb = Test.Outer.newBuilder.setInt(1234).build()
        case class Outer(int: Int)
        pb.as[Outer] should equal (Outer(1234))
      }
      "longs" in {
        val pb = Test.Outer.newBuilder.setLong(123456789L).build()
        case class Outer(long: Long)
        pb.as[Outer] should equal (Outer(123456789L))
      }
      "doubles" in {
        val pb = Test.Outer.newBuilder.setDouble(1.2).build()
        case class Outer(double: Double)
        pb.as[Outer] should equal (Outer(1.2))
      }
      "strings" in {
        val pb = Test.Outer.newBuilder.setText("hi").build()
        case class Outer(text: String)
        pb.as[Outer] should equal (Outer("hi"))
      }
      "byte arrays" in {
        val pb = Test.Outer.newBuilder.setBytes(com.google.protobuf.ByteString.copyFromUtf8("bye")).build()
        case class Outer(bytes: Seq[Byte])
        pb.as[Outer].bytes should equal ("bye".getBytes)
      }
    }
    "deserialize sequences" in {
      val pb = Test.Outer.newBuilder.addTextSeq("a").addTextSeq("b").build()
      case class Outer(textSeq: Seq[String])
      pb.as[Outer] should equal (Outer("a" :: "b" :: Nil))
    }
    "deserialize options" when {
      case class Outer(text: Option[String])
      "present" in {
        val pb = Test.Outer.newBuilder.setText("hi").build()
        pb.as[Outer] should equal (Outer(Some("hi")))
      }
      "absent" in {
        val pb = Test.Outer.newBuilder.build()
        pb.as[Outer] should equal (Outer(None))
      }
    }
    "deserialize maps" in {
      val pb = Test.Outer.newBuilder.addIntToStringMap(
        Test.Pair.newBuilder.setKey(8).setValue("nine").build()
      ).build()
      case class Outer(intToStringMap: Map[Int, String])
      pb.as[Outer] should equal (Outer(Map(8 -> "nine")))
    }
    "deserialize nested classes" when {
      val pb = Test.Outer.newBuilder.setInner(Test.Inner.newBuilder.setFirst(1).setSecond(2L).build()).build()
      case class Inner(first: Int, second: Long)
      "required" in {
        case class Outer(inner: Inner)
        pb.as[Outer] should equal (Outer(Inner(1, 2L)))
      }
      "options" in {
        case class Outer(inner: Option[Inner])
        pb.as[Outer] should equal (Outer(Some(Inner(1, 2L))))
      }
      "sequences" in {
        val pb = Test.Outer.newBuilder.addIntToStringMap(
          Test.Pair.newBuilder.setKey(1).setValue("two").build()
        ).build()
        case class Pair(key: Int, value: String)
        case class Outer(intToStringMap: Seq[Pair])
        pb.as[Outer] should equal (Outer(Pair(1, "two") :: Nil))
      }
    }
    "deserialize mixed" in {
      val pb = Test.Outer.newBuilder
        .setInt(1)
        .setLong(2L)
        .addTextSeq("three").addTextSeq("four")
        .setInner(Test.Inner.newBuilder.setFirst(5).setSecond(6L).build())
        .build()
      case class Inner(first: Int, second: Long)
      case class Outer(int: Int, long: Option[Long], textSeq: Seq[String], inner: Inner)
      val res = pb.as[Outer]
      res.int should equal (1)
      res.long should equal (Some(2L))
      res.textSeq should equal ("three" :: "four" :: Nil)
      res.inner should equal (Inner(5, 6))
    }
    "throw a NoSuchElementException for missing fields" in {
      val pb = Test.Inner.newBuilder().setFirst(1).build()
      case class Inner(second: Long)
      a [NoSuchElementException] should be thrownBy {
        pb.as[Inner]
      }
    }
  }
  "toPb" should {
    "serialize primitives" when {
      "ints" in {
        case class Outer(int: Int)
        Outer(1).toPb[Test.Outer].getInt should equal (1)
      }
      "longs" in {
        case class Outer(long: Long)
        Outer(2L).toPb[Test.Outer].getLong should equal (2L)
      }
      "doubles" in {
        case class Outer(double: Double)
        Outer(3.0).toPb[Test.Outer].getDouble should equal (3.0)
      }
      "strings" in {
        case class Outer(text: String)
        Outer("Hi").toPb[Test.Outer].getText should equal ("Hi")
      }
      "byte arrays" in {
        case class Outer(bytes: Seq[Byte])
        Outer("Bye".getBytes).toPb[Test.Outer].getBytes.toByteArray should equal ("Bye".getBytes)
      }
    }
    "serialize sequences" in {
      import scala.collection.JavaConverters._
      case class Outer(textSeq: Seq[String])
      Outer("a" :: "b" :: Nil).toPb[Test.Outer].getTextSeqList.asScala should equal ("a" :: "b" :: Nil)
    }
    "serialize maps" in {
      import scala.collection.JavaConverters._
      case class Outer(intToStringMap: Map[Int, String])
      val pb = Outer(Map(8 -> "nine")).toPb[Test.Outer]
      val res = pb.getIntToStringMapList.asScala.map(p => p.getKey -> p.getValue).toMap
      res should equal (Map(8 -> "nine"))
    }
    "serialize options" when {
      case class Outer(int: Option[Int])
      "present" in {
        Outer(Some(1)).toPb[Test.Outer].getInt should equal (1)
      }
      "absent" in {
        Outer(None).toPb[Test.Outer].hasInt should be (false)
      }
    }
    "serialize nested classes" when {
      case class Inner(first: Int, second: Long)
      "required" in {
        case class Outer(inner: Inner)
        val pb = Outer(Inner(1, 2L)).toPb[Test.Outer].getInner
        pb.getFirst should be(1)
        pb.getSecond should be(2L)
      }
    }
  }
  "both" should {
    "reverse" in {
      case class Pair(key: Int, value: String)
      val pb = Test.Pair.newBuilder.setKey(1).setValue("two").build()
      val pair = Pair(1, "two")
      pb.as[Pair] should equal (pair)
      pair.toPb[Test.Pair] should equal (pb)
    }
  }
}
