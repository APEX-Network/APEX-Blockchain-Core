/*
 * Copyright  2018 APEX Technologies.Co.Ltd. All rights reserved.
 *
 * FileName: Node.scala
 *
 * @author: shan.huang@chinapex.com: 2018-7-25 下午1:06@version: 1.0
 */

package com.apex.network

import java.time.{Duration, Instant}

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.apex.common.ApexLogging
import com.apex.consensus._
import com.apex.core._
import com.apex.crypto.UInt256
import com.apex.network.rpc.{GetBlockByHeightCmd, GetBlockByIdCmd, GetBlocks, RPCCommand}
import com.apex.settings.ConsensusSettings

import scala.collection.mutable.Map

class Node(val chain: Blockchain, val peerManager: ActorRef) extends Actor with ApexLogging {

  // connectedPeers
  // unconnectedPeers
  // badPeers

  //def AcceptPeers() = {  }

//  def addTransaction(tx: Transaction): Boolean = {
//    //lock (Blockchain.Default.PersistLock)
//    //lock (mem_pool)
//    if (memPool.contains(tx.id)) return false
//    if (chain.containsTransaction(tx.id)) return false
//    //if (!tx.Verify(mem_pool.Values)) return false;
//    memPool.put(tx.id, tx)
//    //CheckMemPool()
//    true
//  }
//
//  def getMemoryPool(): Seq[Transaction] = {
//    memPool.values.toSeq
//  }
//
//  def clearMemoryPool() = {
//    memPool.clear()
//  }
//
//  def getTransaction(hash: UInt256): Option[Transaction] = {
//
//    return memPool.get(hash)
//  }
//
//  def containsTransaction(tx: Transaction): Boolean = {
//
//    return memPool.contains(tx.id)
//  }

  def removeTransactionsInBlock(block: Block) = {
    //TODO
  }

  override def receive: Receive = {
    case message: Message => processMessage(message)
    case cmd: RPCCommand => processRPCCommand(cmd)
    case unknown: Any => println(unknown)
  }

  private def processRPCCommand(cmd: RPCCommand) = {
    cmd match {
      case GetBlockByIdCmd(id) => {
        sender() ! chain.getBlock(id)
      }
      case GetBlockByHeightCmd(height) => {
        sender() ! chain.getBlock(height)
      }
    }
  }

  private def processMessage(message: Message) = {
    message match {
      case VersionMessage(height) => {
        if (height < chain.getHeight) {
          sender() ! GetBlockMessage(height).pack
        }
      }
      case GetBlockMessage(height) => {
        chain.getBlock(height) match {
          case Some(block) => sender() ! BlockMessage(block).pack
          case None => log.error(s"get block($height) failed")
        }
      }
      case BlockMessage(block) => {
        if (!chain.tryInsertBlock(block)) {
          sender() ! GetBlockMessage(block.height + 1).pack
        } else {
          log.error(s"insert block(${block.height}, ${block.id}) failed")
        }
      }
    }
  }
}

object NodeRef {
  def props(chain: Blockchain, peerManager: ActorRef): Props = Props(new Node(chain, peerManager))

  def apply(chain: Blockchain, peerManager: ActorRef)
           (implicit system: ActorSystem): ActorRef = system.actorOf(props(chain, peerManager))

  def apply(chain: Blockchain, peerManager: ActorRef, name: String)
           (implicit system: ActorSystem): ActorRef = system.actorOf(props(chain, peerManager), name)

}