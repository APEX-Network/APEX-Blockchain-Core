package com.apex.common

import com.google.common.primitives.Longs
import io.circe.Encoder
import io.circe.syntax._
import com.apex.core.serialization.Serializer
import com.apex.core.structure.account.PublicKeyNoncedBox
import com.apex.core.structure.box.proposition.PublicKey25519Proposition
import com.apex.core.utils.ApexEncoding
import com.apex.core.hash.Blake2b256
import com.apex.core.signatures.{Curve25519, PublicKey}
import com.apex.crypto.Base16
import scala.util.Try

case class PublicKey25519NoncedBox(override val proposition: PublicKey25519Proposition,
                                   override val nonce: Nonce,
                                   override val value: Value) extends PublicKeyNoncedBox[PublicKey25519Proposition] {

  override type M = PublicKey25519NoncedBox

  override def serializer: Serializer[PublicKey25519NoncedBox] = PublicKey25519NoncedBoxSerializer

  override def toString: String =
    s"PublicKey25519NoncedBox(id: ${Base16.encode(id)}, proposition: $proposition, nonce: $nonce, value: $value)"
}

object PublicKey25519NoncedBox extends ApexEncoding {
  val BoxKeyLength: Int = Blake2b256.DigestSize
  val BoxLength: Int = Curve25519.KeyLength + 2 * 8

  implicit val publicKey25519NoncedBoxEncoder: Encoder[PublicKey25519NoncedBox] = (pknb: PublicKey25519NoncedBox) =>
    Map(
      "id" -> encoder.encode(pknb.id).asJson,
      "address" -> pknb.proposition.address.asJson,
      "publicKey" -> encoder.encode(pknb.proposition.pubKeyBytes).asJson,
      "nonce" -> pknb.nonce.toLong.asJson,
      "value" -> pknb.value.toLong.asJson
    ).asJson
}

object PublicKey25519NoncedBoxSerializer extends Serializer[PublicKey25519NoncedBox] {

  override def toBytes(obj: PublicKey25519NoncedBox): Array[Byte] =
    obj.proposition.pubKeyBytes ++
      Longs.toByteArray(obj.nonce) ++
      Longs.toByteArray(obj.value)


  override def parseBytes(bytes: Array[Byte]): Try[PublicKey25519NoncedBox] = Try {
    val pk = PublicKey25519Proposition(PublicKey @@ bytes.take(32))
    val nonce = Nonce @@ Longs.fromByteArray(bytes.slice(32, 40))
    val value = Value @@ Longs.fromByteArray(bytes.slice(40, 48))
    PublicKey25519NoncedBox(pk, nonce, value)
  }
}

