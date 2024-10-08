package kotlinx.coroutines.channels

import kotlinx.coroutines.testing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.*
import org.junit.*
import org.junit.runner.*
import org.junit.runners.*
import java.util.concurrent.atomic.*

/**
 * Tests delivery of events to multiple broadcast channel subscribers.
 */
@RunWith(Parameterized::class)
class BroadcastChannelMultiReceiveStressTest(
    private val kind: TestBroadcastChannelKind
) : TestBase() {

    // Stressed by lincheck
    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun params(): Collection<Array<Any>> =
            TestBroadcastChannelKind.entries.map { arrayOf<Any>(it) }
    }

    private val nReceivers = if (isStressTest) 10 else 5
    private val nSeconds = 3 * stressTestMultiplierSqrt

    private val broadcast = kind.create<Long>()
    private val pool = newFixedThreadPoolContext(nReceivers + 1, "BroadcastChannelMultiReceiveStressTest")

    private val sentTotal = AtomicLong()
    private val receivedTotal = AtomicLong()
    private val stopOnReceive = AtomicLong(-1)
    private val lastReceived = Array(nReceivers) { AtomicLong(-1) }

    @After
    fun tearDown() {
        pool.close()
    }

    @Test
    fun testStress() = runBlocking {
        println("--- BroadcastChannelMultiReceiveStressTest $kind with nReceivers=$nReceivers")
        val sender =
            launch(pool + CoroutineName("Sender")) {
                var i = 0L
                while (isActive) {
                    i++
                    broadcast.send(i) // could be cancelled
                    sentTotal.set(i) // only was for it if it was not cancelled
                }
            }
        val receivers = mutableListOf<Job>()
        fun printProgress() {
            println("Sent ${sentTotal.get()}, received ${receivedTotal.get()}, receivers=${receivers.size}")
        }
        // ramp up receivers
        repeat(nReceivers) {
            delay(100) // wait 0.1 sec
            val receiverIndex = receivers.size
            val name = "Receiver$receiverIndex"
            println("Launching $name")
            receivers += launch(pool + CoroutineName(name)) {
                val channel = broadcast.openSubscription()
                when (receiverIndex % 5) {
                    0 -> doReceive(channel, receiverIndex)
                    1 -> doReceiveCatching(channel, receiverIndex)
                    2 -> doIterator(channel, receiverIndex)
                    3 -> doReceiveSelect(channel, receiverIndex)
                    4 -> doReceiveCatchingSelect(channel, receiverIndex)
                }
                channel.cancel()
            }
            printProgress()
        }
        // wait
        repeat(nSeconds) { _ ->
            delay(1000)
            printProgress()
        }
        sender.cancelAndJoin()
        println("Tested $kind with nReceivers=$nReceivers")
        val total = sentTotal.get()
        println("      Sent $total events, waiting for receivers")
        stopOnReceive.set(total)
        try {
            withTimeout(5000) {
                receivers.forEachIndexed { index, receiver ->
                    if (lastReceived[index].get() >= total) receiver.cancel()
                    receiver.join()
                }
            }
        } catch (e: Exception) {
            println("Failed: $e")
            pool.dumpThreads("Threads in pool")
            receivers.indices.forEach { index ->
                println("lastReceived[$index] = ${lastReceived[index].get()}")
            }
            throw e
        }
        println("  Received ${receivedTotal.get()} events")
    }

    private fun doReceived(receiverIndex: Int, i: Long): Boolean {
        val last = lastReceived[receiverIndex].get()
        check(i > last) { "Last was $last, got $i" }
        if (last != -1L && !kind.isConflated)
            check(i == last + 1) { "Last was $last, got $i" }
        receivedTotal.incrementAndGet()
        lastReceived[receiverIndex].set(i)
        return i >= stopOnReceive.get()
    }

    private suspend fun doReceive(channel: ReceiveChannel<Long>, receiverIndex: Int) {
        while (true) {
            try {
                val stop = doReceived(receiverIndex, channel.receive())
                if (stop) break
            } catch (_: ClosedReceiveChannelException) {
                break
            }
        }
    }

    private suspend fun doReceiveCatching(channel: ReceiveChannel<Long>, receiverIndex: Int) {
        while (true) {
            val stop = doReceived(receiverIndex, channel.receiveCatching().getOrNull() ?: break)
            if (stop) break
        }
    }

    private suspend fun doIterator(channel: ReceiveChannel<Long>, receiverIndex: Int) {
        for (event in channel) {
            val stop = doReceived(receiverIndex, event)
            if (stop) break
        }
    }

    private suspend fun doReceiveSelect(channel: ReceiveChannel<Long>, receiverIndex: Int) {
        while (true) {
            try {
                val event = select<Long> { channel.onReceive { it } }
                val stop = doReceived(receiverIndex, event)
                if (stop) break
            } catch (_: ClosedReceiveChannelException) {
                break
            }
        }
    }

    private suspend fun doReceiveCatchingSelect(channel: ReceiveChannel<Long>, receiverIndex: Int) {
        while (true) {
            val event = select<Long?> { channel.onReceiveCatching { it.getOrNull() } } ?: break
            val stop = doReceived(receiverIndex, event)
            if (stop) break
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun println(debugMessage: String) {
        // Uncomment for local debugging
        //println(debugMessage as Any?)
    }
}
