package com.apex.common

import java.io.{ByteArrayOutputStream, DataInputStream, DataOutputStream}

trait Serializable {
  def serialize(os: DataOutputStream): Unit

  //  def deserialize[T <: Serializable](is: DataInputStream): T
}

object Serializable {
  implicit class Extension(val obj: Serializable) {
    def toBytes: Array[Byte] = {
      val bs = new ByteArrayOutputStream()
      val os = new DataOutputStream(bs)
      obj.serialize(os)
      bs.toByteArray
    }
  }

  implicit class DataOutputStreamExtension(val os: DataOutputStream) {
    def writeBytes(bytes: Array[Byte]) = {
      os.writeInt(bytes.length)
      os.write(bytes)
    }

    def writeString(str: String) = {
      os.writeBytes(str.getBytes("UTF-8"))
    }

    def write[A <: Serializable](value: A) = {
      value.serialize(os)
    }

    def writeSeq[A <: Serializable](arr: Seq[A]) = {
      os.writeInt(arr.length)
      arr.foreach(_.serialize(os))
    }

    def writeMap[K <: Serializable, V <: Serializable](map: Map[K, V]) = {
      os.writeInt(map.size)
      map.foreach(o => {
        o._1.serialize(os)
        o._2.serialize(os)
      })
    }
  }

  implicit class DataInputStreamExtension(val is: DataInputStream) {
    def readBytes(): Array[Byte] = {
      val bytes = Array.fill(is.readInt())(0.toByte)
      is.read(bytes, 0, bytes.length)
      bytes
    }

    def readSeq[A <: Serializable](deserializer: DataInputStream => A): Seq[A] = {
      (1 to is.readInt) map (_ => deserializer(is))
    }

    def readMap[K <: Serializable, V <: Serializable](kDeserializer: DataInputStream => K,
                                                      vDeserializer: DataInputStream => V): Map[K, V] = {
      (1 to is.readInt) map (_ => kDeserializer(is) -> vDeserializer(is)) toMap
    }

    def readObj[A <: Serializable](deserializer: DataInputStream => A): A = {
      deserializer(is)
    }

    def readString(): String = {
      new String(is.readBytes, "UTF-8")
    }
  }

}
