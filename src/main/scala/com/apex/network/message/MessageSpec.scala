package com.apex.network.message

import com.apex.core.serialization.Serializer

trait MessageSpec[Content] extends Serializer[Content] {
  val messageCode: Message.MessageCode
  val messageName: String

  override def toString: String = s"MessageSpec($messageCode: $messageName)"
}
