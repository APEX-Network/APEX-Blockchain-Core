package com.apex.test

import java.time.Instant

import com.apex.consensus.{ProducerUtil, RegisterData, WitnessInfo}
import com.apex.core._
import com.apex.crypto.{BinaryData, Crypto, Ecdsa, FixedNumber, MerkleTree, UInt160, UInt256}
import com.apex.crypto.Ecdsa.{PrivateKey, PublicKey}
import com.apex.settings.{ConsensusSettings, RuntimeParas, _}
import com.apex.proposal._
import com.apex.solidity.Abi
import com.apex.vm.DataWord
import com.apex.vm.precompiled._
import org.junit.{AfterClass, Test}

import scala.collection.mutable.ArrayBuffer
import scala.reflect.io.Directory

@Test
class BlockchainTest {

  Directory("BlockchainTest").deleteRecursively()

  val _produceInterval = 500

  val _minerAward: Double = 12.3

  val _acct1 = Ecdsa.PrivateKey.fromWIF("KwmuSp41VWBtGSWaQQ82ZRRSFzkJVTAyuDLQ9NzP9CPqLWirh4UQ").get
  val _acct2 = Ecdsa.PrivateKey.fromWIF("L32JpLopG2hWjEMSCkAjS1nUnPixVrDTPqFAGYbddQrtUjRfkjEP").get
  val _acct3 = Ecdsa.PrivateKey.fromWIF("KyUTLv2BeP9SJD6Sa8aHBVmuRkgw9eThjNGJDE4PySEgf2TvCQCn").get
  val _acct4 = Ecdsa.PrivateKey.fromWIF("L33Uh9L35pSoEqBPP43U6rQcD2xMpJ7F4b3QMjUMAL6HZhxUqEGq").get
  val _acct5 = Ecdsa.PrivateKey.fromWIF("KxisR46MUfkekvgfuuydTD91avsjxhoqs5S6Ech2uiG21RDUEbna").get
  val _acct6 = Ecdsa.PrivateKey.fromWIF("L58v5YMhVPWJJRVAAQ5kgZ3gvKfHEMJPLXLrN6b9vWfNkhYmkvcV").get
  val _acct7 = Ecdsa.PrivateKey.fromWIF("Ky8Fr89cZPEetAQyKHzcLn5BrqehZAn9jTQxpNAmxusuZuxM4xN2").get

  val _witAcct1 = Ecdsa.PrivateKey.fromWIF("Kzgt5pr3a7ZFoz1sA7mtqHQsL7iFXvxWRSPF2NBwqCdBgvpyYFRL").get
  val _witAcct2 = Ecdsa.PrivateKey.fromWIF("L5UB3ejT6kiYKfue8G2YkGz5FCNxyLZ3AuK2NBPgHbBniTqQ2M6s").get
  val _witAcct3 = Ecdsa.PrivateKey.fromWIF("KyAHDybvf2dSoiKbfEgdNvMLsJjn67w3HYMPLAcVpVTBhfhGF3gB").get
  val _witAcct4 = Ecdsa.PrivateKey.fromWIF("KyWL2DuAosLkSzuVaGb3RkGWrAr26sdbkLVAZ6FNPBrCBD7cMCGo").get

  val _witness1 = InitWitness("init1", _witAcct1.publicKey.address)
  val _witness2 = InitWitness("init2", _witAcct2.publicKey.address)
  val _witness3 = InitWitness("init3", _witAcct3.publicKey.address)
  val _witness4 = InitWitness("init4", _witAcct4.publicKey.address)

  val _miners = MinerSettings(Array(
    _witAcct1,
    _witAcct2,
    _witAcct3,
    _witAcct4))

  val _consensusSettings  = ConsensusSettings(_produceInterval, 500, 1, 4, 5, 2.1, 63000, Array(_witness1, _witness2, _witness3, _witness4))
  val _consensusSettings2 = ConsensusSettings(_produceInterval, 500, 3, 4, 5, 2.1, 63000, Array(_witness1, _witness2, _witness3, _witness4))

  val _runtimeParas = RuntimeParas(100)

  private val minerCoinFrom = UInt160.Zero

  private def makeTx(from: PrivateKey,
                     to: PrivateKey,
                     amount: FixedNumber,
                     nonce: Long,
                     gasLimit: Long = 21000,
                     gasPrice: FixedNumber = FixedNumber.MinValue,
                     txType: TransactionType.Value = TransactionType.Transfer,
                     executedTime: Long = 0): Transaction = {

    val tx = new Transaction(txType, from.publicKey.pubKeyHash, to.publicKey.pubKeyHash,
      amount, nonce, BinaryData.empty, gasPrice, gasLimit, BinaryData.empty, executeTime = executedTime)
    tx.sign(from)
    tx
  }

  private def makeBlock(chain: Blockchain,
                        preBlock: Block,
                        txs: Seq[Transaction],
                        award: Double = _minerAward): Block = {
    val blockTime = preBlock.timeStamp + _consensusSettings.produceInterval
    val miner = chain.getWitness(blockTime)

    val minerTx = new Transaction(TransactionType.Miner, minerCoinFrom,
      miner, FixedNumber.fromDecimal(award),
      preBlock.height + 1,
      BinaryData(Crypto.randomBytes(8)), // add random bytes to distinct different blocks with same block index during debug in some cases
      FixedNumber.MinValue, 0, BinaryData.empty)

    val allTxs = ArrayBuffer.empty[Transaction]

    allTxs.append(minerTx)
    txs.foreach(allTxs.append(_))

    val header: BlockHeader = BlockHeader.build(preBlock.header.index + 1,
      blockTime, MerkleTree.root(allTxs.map(_.id)),
      preBlock.id(), _miners.findPrivKey(miner).get)

    Block.build(header, allTxs)
  }

  private def makeBlockByTime(chain: Blockchain, preBlock: Block,
                              //txs: Seq[Transaction],
                              blockTime: Long): Block = {
    //val blockTime = preBlock.timeStamp + _consensusSettings.produceInterval
    val miner = chain.getWitness(blockTime)

    val minerTx = new Transaction(TransactionType.Miner, minerCoinFrom,
      miner, FixedNumber.fromDecimal(_minerAward), preBlock.height + 1,
      BinaryData(Crypto.randomBytes(8)), // add random bytes to distinct different blocks with same block index during debug in some cases
      FixedNumber.MinValue, 0, BinaryData.empty)

    val allTxs = ArrayBuffer.empty[Transaction]

    allTxs.append(minerTx)
    //txs.foreach(allTxs.append(_))

    val header: BlockHeader = BlockHeader.build(preBlock.header.index + 1,
      blockTime, MerkleTree.root(allTxs.map(_.id)),
      preBlock.id(), _miners.findPrivKey(miner).get)

    Block.build(header, allTxs)
  }

  private def startProduceBlock(chain: Blockchain, blockTime: Long, stopProcessTxTime: Long) = {

    val witness = chain.getWitness(blockTime)
    chain.startProduceBlock(_miners.findPrivKey(witness).get, blockTime, stopProcessTxTime)
  }

  private def createChain(path: String,
                          consensusSettings: ConsensusSettings = _consensusSettings): Blockchain = {
    val baseDir = s"BlockchainTest/$path"
    val chainSetting = ChainSettings(
      BlockBaseSettings(s"$baseDir/block", false, 0, DBType.LevelDB),
      DataBaseSettings(s"$baseDir/data", false, 0, DBType.LevelDB),
      ForkBaseSettings(s"$baseDir/fork", false, 0, DBType.LevelDB),
      _minerAward,
      GenesisSettings(Instant.EPOCH,
        "7a93d447bffe6d89e690f529a3a0bdff8ff6169172458e04849ef1d4eafd7f86",
        Array(CoinAirdrop(_acct1.publicKey.address, 123.12),
          CoinAirdrop(_acct2.publicKey.address, 234.2))
      )
    )

    new Blockchain(chainSetting, consensusSettings, _runtimeParas, Notification())
  }

  private def sleepTo(time: Long) = {
    val nowTime = Instant.now.toEpochMilli
    if (time > nowTime)
      Thread.sleep(time - nowTime)
  }

  @Test
  def testIsLastBlockOfProducer(): Unit = {
    def doTestIsLastBlockOfProducer(chain: Blockchain): Unit = {
      try{

        var nowTime = Instant.now.toEpochMilli
        var blockTime = ProducerUtil.nextBlockTime(chain.getHeadTime(), nowTime, _produceInterval / 10, _produceInterval)
        for (i <- 0 to 50) {
          blockTime += _produceInterval
          if (chain.getWitness(blockTime).equals(chain.getWitness(blockTime + _produceInterval))) {
            //println("dd")
            assert(!chain.isLastBlockOfProducer(blockTime))
          }
          else {
            //println("dddddd")
            assert(chain.isLastBlockOfProducer(blockTime))
          }
        }
      }

      catch {
        case e: Exception => e.printStackTrace()
      }

      finally {
        chain.close()
      }
    }
      doTestIsLastBlockOfProducer(createChain("testIsLastBlockOfProducer", _consensusSettings))
      doTestIsLastBlockOfProducer(createChain("testIsLastBlockOfProducer2", _consensusSettings2))

  }

  @Test
  def testCreateChain(): Unit = {
    val chain = createChain("testCreateChain")
    try {

      assert(chain.getHeight() == 0)

      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(123.12))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(234.2))

      var nowTime = Instant.now.toEpochMilli - 90000
      var blockTime = ProducerUtil.nextBlockTime(chain.getHeadTime(), nowTime, _produceInterval / 10, _produceInterval) //  chain.getHeadTime() + _consensusSettings.produceInterval
      sleepTo(blockTime)
      blockTime += _produceInterval
      startProduceBlock(chain, blockTime, Long.MaxValue)

      assert(chain.isProducingBlock())

      // not enough coin
      assert(!chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(123.13), 0)))
      // not enough coin
      assert(!chain.addTransaction(makeTx(_acct3, _acct5, FixedNumber.fromDecimal(1), 0)))

      //wrong txType
      assert(!chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 0, txType = TransactionType.Miner)))
      assert(!chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 0, txType = TransactionType.Refund)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 0)))
      assert(!chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(2), 0)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(2), 1)))
      assert(chain.addTransaction(makeTx(_acct1, _acct3, FixedNumber.fromDecimal(100), 2)))
      assert(!chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(20.121), 3)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(20.02), 3)))

      assert(!chain.addTransaction(makeTx(_acct3, _acct5, FixedNumber.fromDecimal(100.1), 0)))
      assert(chain.addTransaction(makeTx(_acct3, _acct5, FixedNumber.fromDecimal(80), 0)))

      sleepTo(blockTime)
      val block1 = chain.produceBlockFinalize()
      assert(block1.isDefined)
      assert(block1.get.transactions.size == 6)
      assert(!chain.isProducingBlock())
      assert(chain.getHeight() == 1)
      assert(chain.getHeadTime() == blockTime)
      assert(chain.head.id() == block1.get.id())

      assert(chain.getBalance(_acct3).get == FixedNumber.fromDecimal(BigDecimal("19.999999999999979000")))
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("0.099999999999916000")))

      val tx1 = makeTx(_acct3, _acct4, FixedNumber.fromDecimal(11), 1)

      val block2 = makeBlock(chain, block1.get, Seq(tx1))

      // test getTransaction()
      //assert(block2.getTransaction(tx1.id).get.id == tx1.id)

      println("call tryInsertBlock block2")
      assert(chain.tryInsertBlock(block2, true))
      println("block2 inserted")
      println(block2.shortId())

      assert(chain.getBalance(_acct4).get == FixedNumber.fromDecimal(11))

      assert(chain.getBalance(_acct3).get == FixedNumber.fromDecimal(BigDecimal("8.999999999999958000")))

      assert(!chain.tryInsertBlock(makeBlock(chain, block2, Seq.empty[Transaction], _minerAward + 0.1), true))

      assert(chain.getBalance(_acct3).get == FixedNumber.fromDecimal(BigDecimal("8.999999999999958000")))

      val block22 = makeBlock(chain, block1.get, Seq.empty[Transaction])
      sleepTo(block22.timeStamp)
      assert(chain.tryInsertBlock(block22, true))

      assert(chain.getBalance(_acct3).get == FixedNumber.fromDecimal(BigDecimal("8.999999999999958000")))

      assert(chain.head.id() == block2.id())
      assert(chain.getLatestHeader().id() == block2.id())

      val block33 = makeBlock(chain, block22, Seq.empty[Transaction])
      sleepTo(block33.timeStamp)
      assert(chain.tryInsertBlock(block33, true))

      assert(chain.head.id() == block33.id())
      assert(chain.getLatestHeader().id() == block33.id())

      assert(chain.getBalance(_acct3).get == FixedNumber.fromDecimal(BigDecimal("19.999999999999979000")))
      assert(chain.getBalance(_acct4).isEmpty)

      //      val block332 = makeBlock(chain, block22, Seq.empty[Transaction])
      //      sleepTo(block332.timeStamp)
      //      assert(chain.tryInsertBlock(block332, true))
      //
      //      assert(chain.head.id() == block332.id())
      //      assert(chain.getLatestHeader().id() == block332.id())
      //
      //      assert(chain.getBalance(_acct3).get == FixedNumber.fromDecimal(20))
      //      assert(chain.getBalance(_acct4).isEmpty)

    }
    finally {
      chain.close()
    }
  }

  @Test
  def testMempoolSeqence(): Unit = {
    val chain = createChain("testMempoolSeqence")
    try {
      assert(chain.getHeight() == 0)
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(123.12))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(234.2))

      var nowTime = Instant.now.toEpochMilli - 90000
      var blockTime = ProducerUtil.nextBlockTime(chain.getHeadTime(), nowTime, _produceInterval / 10, _produceInterval) //  chain.getHeadTime() + _consensusSettings.produceInterval
      sleepTo(blockTime)
      blockTime += _produceInterval

      assert(!chain.isProducingBlock())

      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 0)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 1)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 2)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 3)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 4)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 5)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 6)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 7)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 8)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 9)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 10)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 11)))

      startProduceBlock(chain, blockTime, Long.MaxValue)

      assert(chain.isProducingBlock())

      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 12)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 13)))
      assert(!chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 13)))

      sleepTo(blockTime)
      val block1 = chain.produceBlockFinalize()
      assert(block1.isDefined)
      assert(!chain.isProducingBlock())
      assert(chain.getHeight() == 1)
      assert(chain.getHeadTime() == blockTime)
      assert(chain.head.id() == block1.get.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("109.119999999999706000")))
      assert(block1.get.transactions.size == 15) //  14 + 1
    }
    finally {
      chain.close()
    }
  }

  @Test
  def testNonce(): Unit = {
    val chain = createChain("testNonce")
    try {
      assert(chain.getHeight() == 0)
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(123.12))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(234.2))

      var nowTime = Instant.now.toEpochMilli - 90000
      var blockTime = ProducerUtil.nextBlockTime(chain.getHeadTime(), nowTime, _produceInterval / 10, _produceInterval) //  chain.getHeadTime() + _consensusSettings.produceInterval
      sleepTo(blockTime)
      blockTime += _produceInterval

      assert(!chain.isProducingBlock())

      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 0)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 1)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 2)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 3)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 4)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 5)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 6)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 7)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 8)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 9)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 10)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 11)))

      assert(chain.txPoolTxNumber == 12)

      startProduceBlock(chain, blockTime, Long.MaxValue)

      assert(chain.isProducingBlock())
      assert(chain.txPoolTxNumber == 0)

      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 12)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 13)))
      assert(!chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 13)))
      assert(!chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 12)))

      // nonce too big
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 17)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 16)))
      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 15)))

      assert(chain.txPoolTxNumber == 3)

      val block1 = chain.produceBlockFinalize()
      assert(block1.isDefined)
      assert(!chain.isProducingBlock())
      assert(chain.getHeight() == 1)
      assert(chain.getHeadTime() == blockTime)
      assert(chain.head.id() == block1.get.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("109.119999999999706000")))
      assert(block1.get.transactions.size == 15) //  14 + 1


      blockTime += _produceInterval
      startProduceBlock(chain, blockTime, Long.MaxValue)
      val block2 = chain.produceBlockFinalize()
      assert(block2.get.transactions.size == 1)

      assert(chain.txPoolTxNumber == 3)

      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 14)))

      blockTime += _produceInterval
      startProduceBlock(chain, blockTime, Long.MaxValue)
      val block3 = chain.produceBlockFinalize()
      assert(block3.get.transactions.size == 5)   // 14  15  16  17
      assert(chain.getAccount(_acct1).get.nextNonce == 18)

      assert(chain.txPoolTxNumber == 0)

    }
    finally {
      chain.close()
    }
  }

  @Test
  def testGas(): Unit = {
    val chain = createChain("testGas")
    try {

      assert(chain.getHeight() == 0)

      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(123.12))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(234.2))

      var nowTime = Instant.now.toEpochMilli
      var blockTime = ProducerUtil.nextBlockTime(chain.getHeadTime(), nowTime, _produceInterval / 10, _produceInterval) //  chain.getHeadTime() + _consensusSettings.produceInterval
      //      sleepTo(blockTime)
      //      nowTime = Instant.now.toEpochMilli
      blockTime += _produceInterval

      startProduceBlock(chain, blockTime, Long.MaxValue)

      assert(chain.isProducingBlock())

      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 0)))

      assert(!chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 1, 20999)))

      assert(chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 1, 21000)))

      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("121.119999999999958000")))

      assert(!chain.addTransaction(makeTx(_acct1, _acct5, FixedNumber.fromDecimal(121.12), 2, 21000, FixedNumber(12))))

      assert(chain.addTransaction(makeTx(_acct2, _acct5, FixedNumber.fromDecimal(234), 0)))
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("121.119999999999958000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(BigDecimal("0.199999999999979000")))

      assert(chain.addTransaction(makeTx(_acct1, _acct2,
        FixedNumber.fromDecimal(21.12), 2, 21000, FixedNumber(12000000000L))))

      // 21000 * 12000000000 =  252000000000000 = 0.000252
      println(chain.getBalance(_acct1).get)
      println(chain.getBalance(_acct2).get)
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("99.999747999999958000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(BigDecimal("21.319999999999979000")))

    }
    finally {
      chain.close()
    }
  }

  @Test
  def testChainFork1(): Unit = {
    val chain = createChain("testChainFork1")
    try {

      assert(chain.getHeight() == 0)

      var blockTime = chain.getHeadTime() + _consensusSettings.produceInterval
      startProduceBlock(chain, blockTime, blockTime - 100)

      assert(chain.isProducingBlock())

      val block1 = chain.produceBlockFinalize()
      assert(block1.isDefined)

      val time1 = block1.get.timeStamp // A
      val time2 = time1 + _consensusSettings.produceInterval // B
      val time3 = time2 + _consensusSettings.produceInterval // C
      val time4 = time3 + _consensusSettings.produceInterval // D

      val time5 = time4 + _consensusSettings.produceInterval // a
      val time6 = time5 + _consensusSettings.produceInterval // b
      val time7 = time6 + _consensusSettings.produceInterval // c
      val time8 = time7 + _consensusSettings.produceInterval // d

      val time9 = time8 + _consensusSettings.produceInterval // a
      val time10 = time9 + _consensusSettings.produceInterval // b
      val time11 = time10 + _consensusSettings.produceInterval // c
      val time12 = time11 + _consensusSettings.produceInterval

      val time13 = time12 + _consensusSettings.produceInterval
      val time14 = time13 + _consensusSettings.produceInterval
      val time15 = time14 + _consensusSettings.produceInterval
      val time16 = time15 + _consensusSettings.produceInterval


      assert(!chain.isProducingBlock())

      val block2 = makeBlockByTime(chain, block1.get, time2)
      assert(chain.tryInsertBlock(block2, true)) // b

      val block3 = makeBlockByTime(chain, block2, time3)
      assert(chain.tryInsertBlock(block3, true)) // c

      val block4 = makeBlockByTime(chain, block3, time5)
      assert(chain.tryInsertBlock(block4, true)) // a

      assert(chain.getConfirmedHeader().id() == block1.get.id())

      val block5 = makeBlockByTime(chain, block4, time6)
      assert(chain.tryInsertBlock(block5, true)) // b

      assert(chain.getConfirmedHeader().id() == block2.id())
      assert(chain.getHeight() == 5)
      assert(chain.getLatestHeader().id() == block5.id())

      startProduceBlock(chain, time9, time9 - 100) // a


      assert(chain.isProducingBlock())
      val block999 = chain.produceBlockFinalize()
      assert(block999.isDefined)

      assert(chain.getHeight() == 6)
      assert(chain.getLatestHeader().id() == block999.get.id())

      val block6 = makeBlockByTime(chain, block5, time7) // c
      assert(chain.tryInsertBlock(block6, true))

      assert(chain.getHeight() == 6)
      assert(chain.getLatestHeader().id() == block6.id())

      val block7 = makeBlockByTime(chain, block6, time10) // b
      assert(chain.tryInsertBlock(block7, true))

      assert(chain.getLatestHeader().id() == block7.id())
      assert(chain.getConfirmedHeader().id() == block3.id())

      val block8 = makeBlockByTime(chain, block7, time11) // c
      assert(chain.tryInsertBlock(block8, true))

      assert(chain.getHeight() == 8)
      assert(chain.getConfirmedHeader().id() == block3.id())

      val block9 = makeBlockByTime(chain, block8, time13) // a
      assert(chain.tryInsertBlock(block9, true))

      val block10 = makeBlockByTime(chain, block9, time14) // b
      assert(chain.tryInsertBlock(block10, true))

      assert(chain.getConfirmedHeader().id() == block7.id())

      val block11 = makeBlockByTime(chain, block10, time15) // c
      assert(chain.tryInsertBlock(block11, true))

      assert(chain.getConfirmedHeader().id() == block8.id())

      assert(chain.getHeight() == 11)

    }
    finally {
      chain.close()
    }
  }


  @Test
  def testChainFork2(): Unit = {
    val chain = createChain("testChainFork2")
    try {
      //      val header = BlockHeader.build(
      //        1, 0, UInt256.Zero,
      //        UInt256.Zero, PublicKey("022ac01a1ea9275241615ea6369c85b41e2016abc47485ec616c3c583f1b92a5c8"),
      //        new PrivateKey(BinaryData("efc382ccc0358f468c2a80f3738211be98e5ae419fc0907cb2f51d3334001471")))
      //      val block = Block.build(header, Seq.empty)
      //      assert(chain.tryInsertBlock(block, true) == false)

      assert(chain.getHeight() == 0)

      var blockTime = chain.getHeadTime() + _consensusSettings.produceInterval
      startProduceBlock(chain, blockTime, blockTime - 100)

      assert(chain.isProducingBlock())

      val block1 = chain.produceBlockFinalize()
      assert(block1.isDefined)

      val time1 = block1.get.timeStamp // A
      val time2 = time1 + _consensusSettings.produceInterval // B
      val time3 = time2 + _consensusSettings.produceInterval // C
      val time4 = time3 + _consensusSettings.produceInterval // D

      val time5 = time4 + _consensusSettings.produceInterval // a
      val time6 = time5 + _consensusSettings.produceInterval // b
      val time7 = time6 + _consensusSettings.produceInterval // c
      val time8 = time7 + _consensusSettings.produceInterval // d

      val time9 = time8 + _consensusSettings.produceInterval // a
      val time10 = time9 + _consensusSettings.produceInterval // b
      val time11 = time10 + _consensusSettings.produceInterval // c
      val time12 = time11 + _consensusSettings.produceInterval

      val time13 = time12 + _consensusSettings.produceInterval
      val time14 = time13 + _consensusSettings.produceInterval
      val time15 = time14 + _consensusSettings.produceInterval
      val time16 = time15 + _consensusSettings.produceInterval


      assert(!chain.isProducingBlock())

      val block2 = makeBlockByTime(chain, block1.get, time2)
      assert(chain.tryInsertBlock(block2, true)) // b

      val block3 = makeBlockByTime(chain, block2, time3)
      assert(chain.tryInsertBlock(block3, true)) // c

      val block9999 = makeBlockByTime(chain, block3, time5)
      assert(chain.tryInsertBlock(block9999, true)) // a

      assert(chain.getConfirmedHeader().id() == block1.get.id())
      assert(chain.getHeight() == 4)
      assert(chain.getLatestHeader().id() == block9999.id())

      val block4 = makeBlockByTime(chain, block3, time4)
      assert(chain.tryInsertBlock(block4, true)) // d

      assert(chain.getConfirmedHeader().id() == block1.get.id())
      assert(chain.getHeight() == 4)
      assert(chain.getLatestHeader().id() == block9999.id())

      val block5 = makeBlockByTime(chain, block4, time7)
      assert(chain.tryInsertBlock(block5, true)) // c

      assert(chain.getConfirmedHeader().id() == block1.get.id())
      assert(chain.getHeight() == 5)
      assert(chain.getLatestHeader().id() == block5.id())


      val block6 = makeBlockByTime(chain, block5, time8)
      assert(chain.tryInsertBlock(block6, true)) // d

      assert(chain.getConfirmedHeader().id() == block1.get.id())
      assert(chain.getHeight() == 6)
      assert(chain.getLatestHeader().id() == block6.id())

      val block7 = makeBlockByTime(chain, block6, time9)
      assert(chain.tryInsertBlock(block7, true)) // a

      assert(chain.getConfirmedHeader().id() == block4.id())
      assert(chain.getHeight() == 7)
      assert(chain.getLatestHeader().id() == block7.id())
    }
    finally {
      chain.close()
    }
  }

  def tryInsertBlock(chain: Blockchain, block: Block): Boolean = {
    sleepTo(block.timeStamp)
    chain.tryInsertBlock(block, true)
  }

  @Test
  def testChainFork3(): Unit = {
    val chain = createChain("testChainFork3")
    try {
      assert(chain.getHeight() == 0)
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(123.12))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(234.2))

      var nowTime = Instant.now.toEpochMilli - 90000
      var blockTime = ProducerUtil.nextBlockTime(chain.getHeadTime(), nowTime, _produceInterval / 10, _produceInterval) //  chain.getHeadTime() + _consensusSettings.produceInterval
      sleepTo(blockTime)
      blockTime += _produceInterval
      startProduceBlock(chain, blockTime, Long.MaxValue)

      assert(chain.isProducingBlock())
      assert(chain.addTransaction(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(10), 0)))

      sleepTo(blockTime)
      val block1 = chain.produceBlockFinalize()
      assert(chain.head.id() == block1.get.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("113.119999999999979000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(244.2))

      /*
      *
      *    0 ---> 1 ---> 2A
      *           |
      *           └----> 2B ---> 3A
      *                  |
      *                  └---->  3B ---> 4A
      * */

      val block2A = makeBlock(chain, block1.get, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(11), 1)))
      assert(tryInsertBlock(chain, block2A))

      assert(chain.head.id() == block2A.id())
      assert(chain.getLatestHeader().id() == block2A.id())

      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("102.119999999999958000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(255.2))

      val block2B = makeBlock(chain, block1.get, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(12), 1)))
      assert(tryInsertBlock(chain, block2B))

      assert(chain.head.id() == block2A.id())
      assert(chain.getLatestHeader().id() == block2A.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("102.119999999999958")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(255.2))

      val block3A = makeBlock(chain, block2B, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(13), 2)))
      assert(tryInsertBlock(chain, block3A))

      assert(chain.head.id() == block3A.id())
      assert(chain.getLatestHeader().id() == block3A.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("88.119999999999937000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(269.2))

      val block3B = makeBlock(chain, block2B, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(14), 2)))
      assert(tryInsertBlock(chain, block3B))

      assert(chain.head.id() == block3A.id())
      assert(chain.getLatestHeader().id() == block3A.id())
      println(chain.getBalance(_acct1).get)
      println(chain.getBalance(_acct2).get)
//      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(88.12))
//      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(269.2))

      val block4A = makeBlock(chain, block3B, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(15), 3)))
      assert(tryInsertBlock(chain, block4A))

      assert(chain.head.id() == block4A.id())
      assert(chain.getLatestHeader().id() == block4A.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("72.119999999999916000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(285.2))

    }
    finally {
      chain.close()
    }
  }

  @Test
  def testChainFork4(): Unit = {
    val chain = createChain("testChainFork4")
    try {
      assert(chain.getHeight() == 0)
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(123.12))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(234.2))

      var nowTime = Instant.now.toEpochMilli - 90000
      var blockTime = ProducerUtil.nextBlockTime(chain.getHeadTime(), nowTime, _produceInterval / 10, _produceInterval) //  chain.getHeadTime() + _consensusSettings.produceInterval
      sleepTo(blockTime)
      blockTime += _produceInterval
      startProduceBlock(chain, blockTime, Long.MaxValue)

      assert(chain.isProducingBlock())
      assert(chain.addTransaction(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(10), 0)))

      sleepTo(blockTime)
      val block1 = chain.produceBlockFinalize()
      assert(chain.head.id() == block1.get.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("113.119999999999979000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(244.2))

      /*
      *
      *    0 ---> 1 ---> 2A
      *           |
      *           └----> 2B ---> 3B
      *           |
      *           └----> 2C ---> 3C  ---> 4C
      * */

      val block2A = makeBlock(chain, block1.get, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(11), 1)))
      assert(tryInsertBlock(chain, block2A))

      assert(chain.head.id() == block2A.id())
      assert(chain.getLatestHeader().id() == block2A.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("102.119999999999958000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(255.2))

      val block2B = makeBlock(chain, block1.get, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(12), 1)))
      assert(tryInsertBlock(chain, block2B))

      assert(chain.head.id() == block2A.id())
      assert(chain.getLatestHeader().id() == block2A.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("102.119999999999958000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(255.2))

      val block3B = makeBlock(chain, block2B, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(13), 2)))
      assert(tryInsertBlock(chain, block3B))

      assert(chain.head.id() == block3B.id())
      assert(chain.getLatestHeader().id() == block3B.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("88.1199999999999370")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(269.2))

      val block2C = makeBlock(chain, block1.get, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(14), 1)))
      assert(tryInsertBlock(chain, block2C))

      val block3C = makeBlock(chain, block2C, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(15), 2)))
      assert(tryInsertBlock(chain, block3C))

      assert(chain.head.id() == block3B.id())
      assert(chain.getLatestHeader().id() == block3B.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("88.1199999999999370")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(269.2))

      val block4C = makeBlock(chain, block3C, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(16), 3)))
      assert(tryInsertBlock(chain, block4C))

      assert(chain.head.id() == block4C.id())
      assert(chain.getLatestHeader().id() == block4C.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("68.119999999999916000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(289.2))
    }
    finally {
      chain.close()
    }
  }

  @Test
  def testChainFork5(): Unit = {
    val chain = createChain("testChainFork5")
    try {
      assert(chain.getHeight() == 0)
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(123.12))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(234.2))

      var nowTime = Instant.now.toEpochMilli - 90000
      var blockTime = ProducerUtil.nextBlockTime(chain.getHeadTime(), nowTime, _produceInterval / 10, _produceInterval) //  chain.getHeadTime() + _consensusSettings.produceInterval
      sleepTo(blockTime)
      blockTime += _produceInterval
      startProduceBlock(chain, blockTime, Long.MaxValue)

      assert(chain.isProducingBlock())
      assert(chain.addTransaction(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(10), 0)))

      sleepTo(blockTime)
      val block1 = chain.produceBlockFinalize()
      assert(chain.head.id() == block1.get.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("113.119999999999979000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(244.2))

      /*
      *
      *                    /----> 3A ---> 4A
      *                   |
      *    0 ---> 1 ---> 2A
      *           |
      *           └----> 2B ---> 3B    <----  3B  invalid
      *                   |
      *                   └----> 3C
      *
      * */

      val block2A = makeBlock(chain, block1.get, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(11), 1)))
      assert(tryInsertBlock(chain, block2A))

      assert(chain.head.id() == block2A.id())
      assert(chain.getLatestHeader().id() == block2A.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("102.119999999999958000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(255.2))

      val block2B = makeBlock(chain, block1.get, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(12), 1)))
      assert(tryInsertBlock(chain, block2B))

      assert(chain.head.id() == block2A.id())
      assert(chain.getLatestHeader().id() == block2A.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("102.119999999999958000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(255.2))

      val block3B = makeBlock(chain, block2B, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(9999), 2)))
      assert(!tryInsertBlock(chain, block3B))

      assert(chain.containsBlock(block2B))
      assert(!chain.containsBlock(block3B))

      assert(chain.head.id() == block2A.id())
      assert(chain.getLatestHeader().id() == block2A.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("102.119999999999958000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(255.2))

      val block4B = makeBlock(chain, block3B, Seq.empty)
      assert(!tryInsertBlock(chain, block4B))

      val block3C = makeBlock(chain, block2B, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(13), 2)))
      assert(tryInsertBlock(chain, block3C))

      assert(chain.head.id() == block3C.id())
      assert(chain.getLatestHeader().id() == block3C.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("88.119999999999937000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(269.2))

      val block3A = makeBlock(chain, block2A, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(14), 2)))
      assert(tryInsertBlock(chain, block3A))

      assert(chain.head.id() == block3C.id())
      assert(chain.getLatestHeader().id() == block3C.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("88.119999999999937000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(269.2))

      val block4A = makeBlock(chain, block3A, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(15), 3)))
      assert(tryInsertBlock(chain, block4A))

      assert(chain.head.id() == block4A.id())
      assert(chain.getLatestHeader().id() == block4A.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("73.119999999999916000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(284.2))
    }
    finally {
      chain.close()
    }
  }

  @Test
  def testChainFork6(): Unit = {
    val chain = createChain("testChainFork6")
    try {
      assert(chain.getHeight() == 0)
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(123.12))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(234.2))

      var nowTime = Instant.now.toEpochMilli - 90000
      var blockTime = ProducerUtil.nextBlockTime(chain.getHeadTime(), nowTime, _produceInterval / 10, _produceInterval) //  chain.getHeadTime() + _consensusSettings.produceInterval
      sleepTo(blockTime)
      blockTime += _produceInterval
      startProduceBlock(chain, blockTime, Long.MaxValue)

      assert(chain.isProducingBlock())
      assert(chain.addTransaction(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(10), 0)))

      sleepTo(blockTime)
      val block1 = chain.produceBlockFinalize()
      assert(chain.head.id() == block1.get.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("113.119999999999979000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(244.2))

      /*
      *
      *    0 ---> 1 ---> 2A
      *           |
      *           └----> 2B ---> 3B    <----  2B  invalid
      *
      * */

      val block2A = makeBlock(chain, block1.get, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(11), 1)))
      assert(tryInsertBlock(chain, block2A))

      assert(chain.head.id() == block2A.id())
      assert(chain.getLatestHeader().id() == block2A.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("102.119999999999958000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(255.2))

      val block2B = makeBlock(chain, block1.get, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(99999), 1)))
      assert(tryInsertBlock(chain, block2B))

      assert(chain.head.id() == block2A.id())
      assert(chain.getLatestHeader().id() == block2A.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("102.119999999999958000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(255.2))

      val block3B = makeBlock(chain, block2B, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(9), 2)))
      assert(!tryInsertBlock(chain, block3B))

      assert(!chain.containsBlock(block2B))
      assert(!chain.containsBlock(block3B))

      assert(chain.head.id() == block2A.id())
      assert(chain.getLatestHeader().id() == block2A.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("102.119999999999958000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(255.2))

      val block3A = makeBlock(chain, block2A, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(2), 2)))
      assert(tryInsertBlock(chain, block3A))

      assert(chain.head.id() == block3A.id())
      assert(chain.getLatestHeader().id() == block3A.id())
      println(chain.getBalance(_acct1).get)
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("100.119999999999937000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(257.2))

    }
    finally {
      chain.close()
    }
  }

  @Test
  def testChainForkScheduleTx(): Unit = {
    val chain = createChain("testChainForkScheduleTx")
    try {
      assert(chain.getHeight() == 0)
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(123.12))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(234.2))

      var nowTime = Instant.now.toEpochMilli - 90000
      var blockTime = ProducerUtil.nextBlockTime(chain.getHeadTime(), nowTime, _produceInterval / 10, _produceInterval) //  chain.getHeadTime() + _consensusSettings.produceInterval
      sleepTo(blockTime)
      blockTime += _produceInterval
      startProduceBlock(chain, blockTime, Long.MaxValue)

      assert(chain.isProducingBlock())
      //assert(chain.addTransaction(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(10), 0, executedTime = 750)))
      assert(chain.addTransaction(makeTx(_acct1, _acct3, FixedNumber.fromDecimal(10), 0)))
      makeRegisterTransaction() {
        tx =>
          assert(chain.addTransaction(tx))
          val witness = chain.getWitness(_acct3.publicKey.pubKeyHash)
          assert(witness.isDefined)
          assert(witness.get.name == "register node1")

//          assert(chain.getBalance(_acct3).get == FixedNumber.fromDecimal(BigDecimal("8.999999999999975088")))
          assert(chain.getBalance(new UInt160(PrecompiledContracts.registerNodeAddr.getLast20Bytes)).get == FixedNumber.One)
      }
      makeRegisterTransaction(OperationType.resisterCancel, 1) {
        tx => {
          assert(chain.addTransaction(tx))
          val witness = chain.getWitness(_acct3.publicKey.pubKeyHash)
          assert(witness.isDefined)
          assert(chain.getScheduleTx().size == 1)
//          assert(chain.getBalance(_acct3).get == FixedNumber.fromDecimal(BigDecimal("8.999999999999950112")))
          assert(chain.getBalance(new UInt160(PrecompiledContracts.registerNodeAddr.getLast20Bytes)).get == FixedNumber.One)
        }
      }
      sleepTo(blockTime)
      val block0 = chain.produceBlockFinalize()
      assert(chain.head.id() == block0.get.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("113.119999999999979000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(234.2))
      //      val txxxx = chain.getScheduleTx()
      //      assert(chain.getScheduleTx().size == 1)
      //
      //      /*
      //      *
      //      *    0 ---> 1 ---> 2A
      //      *           |
      //      *           └----> 2B ---> 3A
      //      * */
      //
      blockTime += _produceInterval
      startProduceBlock(chain, blockTime, Long.MaxValue)
      assert(chain.isProducingBlock())
      sleepTo(blockTime)
      val block1 = chain.produceBlockFinalize()

      blockTime += _produceInterval
      startProduceBlock(chain, blockTime, Long.MaxValue)
      assert(chain.isProducingBlock())
      sleepTo(blockTime)
      chain.addTransaction(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(11), 1))

      val block2A = chain.produceBlockFinalize().get

      //val block2A = makeBlock(chain, block1.get, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(11), 1)))
      //assert(tryInsertBlock(chain, block2A))

      assert(chain.head.id() == block2A.id())
      assert(chain.getLatestHeader().id() == block2A.id())

      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("102.119999999999958000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(245.2))
      assert(block2A.transactions.size == 2)
      assert(chain.getScheduleTx().size == 1)

//      assert(chain.getBalance(_acct3).get == FixedNumber.fromDecimal(BigDecimal("9.999999999999950112")))
//      assert(chain.getBalance(new UInt160(PrecompiledContracts.registerNodeAddr.getLast20Bytes)).get == FixedNumber.Zero)

      val block2B = makeBlock(chain, block1.get, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(12), 1)))
      assert(tryInsertBlock(chain, block2B))

      assert(chain.head.id() == block2A.id())
      assert(chain.getLatestHeader().id() == block2A.id())
      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("102.119999999999958000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(245.2))

      val block3A = makeBlock(chain, block2B, Seq(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(13), 2)))
      assert(tryInsertBlock(chain, block3A))

      assert(chain.head.id() == block3A.id())
      assert(chain.getLatestHeader().id() == block3A.id())

      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(BigDecimal("88.119999999999937000")))
      assert(chain.getBalance(_acct2).get == FixedNumber.fromDecimal(259.2))

      blockTime += _produceInterval
      startProduceBlock(chain, blockTime, Long.MaxValue)
      assert(chain.isProducingBlock())
      sleepTo(blockTime)
      //      assert(chain.getBalance(_acct3).get == FixedNumber.fromDecimal(9))
      //chain.addTransaction(makeTx(_acct1, _acct2, FixedNumber.fromDecimal(11), 1))

      val block4A = chain.produceBlockFinalize().get
      assert(chain.head.id() == block4A.id())
      assert(chain.getLatestHeader().id() == block4A.id())
      assert(block4A.transactions.size == 1)
      val transaction = block4A.transactions.find(_.txType == TransactionType.Refund)
      assert(transaction.isEmpty)

    }
    finally {
      chain.close()
    }
  }

  private def makeRegisterTransaction(operationType: OperationType.Value = OperationType.register,
                                      nonce: Long = 0,
                                      account: UInt160 = _acct3.publicKey.pubKeyHash,
                                      name: String = "register node1")(f: Transaction => Unit) {
    val txData = RegisterData(account, WitnessInfo(account, false, name), operationType).toBytes
    val registerContractAddr = new UInt160(DataWord.of("0000000000000000000000000000000000000000000000000000000000000101").getLast20Bytes)
    val tx = new Transaction(TransactionType.Call, account, registerContractAddr, FixedNumber.Zero,
      nonce, txData, FixedNumber.MinValue, 9000000L, BinaryData.empty)
    f(tx)
  }

  //  def checkRegisterSuccess(tx: Transaction): Unit ={
  //    assert(chain.addTransaction(tx))
  //    val witness = chain.getWitness(_acct3.publicKey.pubKeyHash)
  //    assert(witness.isDefined)
  //    assert(witness.get.name == "register node1")
  //    assert(chain.getBalance(_acct3.publicKey.pubKeyHash).get == FixedNumber.fromDecimal(2))
  //    assert(chain.getBalance(new UInt160(PrecompiledContracts.registerNodeAddr.getLast20Bytes)).get == FixedNumber.One)
  //  }

  @Test
  def testStopProcessNewTxTime(): Unit = {
    val chain = createChain("testStopProcessNewTxTime")
    try {

      assert(chain.getHeight() == 0)
      assert(_produceInterval == 500)

      assert(chain.getBalance(_acct1).get == FixedNumber.fromDecimal(123.12))

      var nowTime = Instant.now.toEpochMilli
      var blockTime = ProducerUtil.nextBlockTime(chain.getHeadTime(), nowTime, _produceInterval / 10, _produceInterval) //  chain.getHeadTime() + _consensusSettings.produceInterval
      sleepTo(blockTime)
      blockTime += _produceInterval

      startProduceBlock(chain, blockTime, blockTime - 200)

      assert(chain.isProducingBlock())

      nowTime = Instant.now.toEpochMilli
      assert(nowTime < blockTime - 200)

      var tx = makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 0)
      assert(chain.addTransaction(tx))
      assert(chain.getTransactionFromPendingTxs(tx.id).isDefined)
      assert(chain.getTransactionFromUnapplyTxs(tx.id).isEmpty)
      assert(chain.getTransactionFromMempool(tx.id).isDefined)

      sleepTo(blockTime - 150)

      tx = makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 1)
      assert(chain.addTransaction(tx))
      assert(chain.getTransactionFromPendingTxs(tx.id).isEmpty)
      assert(chain.getTransactionFromUnapplyTxs(tx.id).isDefined)
      assert(chain.getTransactionFromMempool(tx.id).isDefined)

      tx = makeTx(_acct1, _acct5, FixedNumber.fromDecimal(1), 99)
      assert(chain.addTransaction(tx))
      assert(chain.getTransactionFromPendingTxs(tx.id).isEmpty)
      assert(chain.getTransactionFromUnapplyTxs(tx.id).isDefined)
      assert(chain.getTransactionFromMempool(tx.id).isDefined)
    }
    finally {
      chain.close()
    }
  }

  @Test
  def testDBGetAll1(): Unit = {
    val chain = createChain("testDBGetAll1")
    try {

      assert(chain.getHeight() == 0)

      var nowTime = Instant.now.toEpochMilli - 90000
      var blockTime = ProducerUtil.nextBlockTime(chain.getHeadTime(), nowTime, _produceInterval / 10, _produceInterval) //  chain.getHeadTime() + _consensusSettings.produceInterval
      sleepTo(blockTime)
      blockTime += _produceInterval
      startProduceBlock(chain, blockTime, Long.MaxValue)

      assert(chain.isProducingBlock())

      val db = chain.getDataBase()

      val id1 = UInt256.fromBytes(BinaryData("15e52a9bab596291d0afef27bfa40de6993ebd7c3837f2e4634f5bd60d22d841"))
      val id2 = UInt256.fromBytes(BinaryData("25e52a9bab596291d0afef27bfa40de6993ebd7c3837f2e4634f5bd60d22d841"))

      val p1 = new Proposal(id1, ProposalType.BlockAward, ProposalStatus.PendingActive, 1, 1, 1, Array.empty, BinaryData.empty)
      val p2 = new Proposal(id2, ProposalType.BlockAward, ProposalStatus.PendingActive, 1, 1, 1, Array.empty, BinaryData.empty)
      val p22 = new Proposal(id2, ProposalType.BlockAward, ProposalStatus.PendingActive, 2, 2, 2, Array.empty, BinaryData.empty)

      db.setProposal(p1)
      db.setProposal(p2)

      var allP = db.getAllProposal()
      assert(allP.size == 2)

//      db.commit()
      db.startSession()

      db.setProposal(p22)

      allP = db.getAllProposal()
      assert(allP.size == 2)

    }
    finally {
      chain.close()
    }
  }

  @Test
  def testDBGetAll2(): Unit = {
    val chain = createChain("testDBGetAll2")
    try {

      assert(chain.getHeight() == 0)

      var nowTime = Instant.now.toEpochMilli - 90000
      var blockTime = ProducerUtil.nextBlockTime(chain.getHeadTime(), nowTime, _produceInterval / 10, _produceInterval) //  chain.getHeadTime() + _consensusSettings.produceInterval
      sleepTo(blockTime)
      blockTime += _produceInterval
      startProduceBlock(chain, blockTime, Long.MaxValue)

      assert(chain.isProducingBlock())

      val db = chain.getDataBase()

      val id1 = UInt256.fromBytes(BinaryData("15e52a9bab596291d0afef27bfa40de6993ebd7c3837f2e4634f5bd60d22d841"))
      val id2 = UInt256.fromBytes(BinaryData("25e52a9bab596291d0afef27bfa40de6993ebd7c3837f2e4634f5bd60d22d841"))

      val p1 = new Proposal(id1, ProposalType.BlockAward, ProposalStatus.PendingActive, 1, 1, 1, Array.empty, BinaryData.empty)
      val p2 = new Proposal(id2, ProposalType.BlockAward, ProposalStatus.PendingActive, 1, 1, 1, Array.empty, BinaryData.empty)

      db.setProposal(p1)
      db.setProposal(p2)

      db.deleteProposal(id2)

      var allP = db.getAllProposal()
      assert(allP.size == 1)

    }
    finally {
      chain.close()
    }
  }


}

object BlockchainTest {
  @AfterClass
  def cleanUp: Unit = {
    println("clean Directory")
    Directory("BlockchainTest").deleteRecursively()
  }
}
