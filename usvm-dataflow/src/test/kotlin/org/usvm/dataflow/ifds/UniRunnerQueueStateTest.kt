/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.usvm.dataflow.ifds

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.cfg.CommonInst
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.usvm.dataflow.graph.ApplicationGraph
import org.usvm.dataflow.taint.TaintAnalyzer
import org.usvm.dataflow.taint.TaintDomainFact
import org.usvm.dataflow.taint.TaintEvent
import org.usvm.dataflow.taint.TaintManager
import org.usvm.dataflow.taint.TaintZeroFact
import org.usvm.dataflow.util.Traits
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UniRunnerQueueStateTest {
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `a drained runner ends idle after a delayed sender activity notification`() {
        val activityEntered = CountDownLatch(1)
        val releaseActivity = CountDownLatch(1)
        val fixture = Fixture { event ->
            if (!event.isEmpty) {
                activityEntered.countDown()
                assertTrue(releaseActivity.await(3, TimeUnit.SECONDS))
            }
        }
        fixture.dispatcher.nextTask().run() // Reach the real runner's initial blocking receive.
        val sourceFailure = AtomicReference<Throwable?>()
        val receiverFailure = AtomicReference<Throwable?>()
        val sender = thread(name = "summary-sender") {
            captureFailure(sourceFailure) { fixture.runner.submitNewEdge(fixture.edge, Reason.Initial) }
        }
        var receiver: Thread? = null

        try {
            assertTrue(activityEntered.await(3, TimeUnit.SECONDS))
            val continuation = fixture.dispatcher.nextTask()
            receiver = thread(name = "summary-receiver") { captureFailure(receiverFailure) { continuation.run() } }
            awaitBlockedOrFinished(receiver)

            releaseActivity.countDown()
            sender.join(3000)
            receiver.join(3000)

            assertFalse(sender.isAlive)
            assertFalse(receiver.isAlive)
            assertNull(sourceFailure.get())
            assertNull(receiverFailure.get())
            val reports = fixture.reports.toList()
            assertEquals(expected = false, actual = reports.drop(1).first())
            assertEquals(expected = true, actual = reports.last())
            assertEquals(expected = 2, actual = fixture.callInspections.get())
        } finally {
            releaseActivity.countDown()
            sender.join(3000)
            receiver?.join(3000)
            fixture.close()
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `work sent during idle observation is processed with an activity notification`() {
        val idleEntered = CountDownLatch(1)
        val releaseIdle = CountDownLatch(1)
        val fixture = Fixture { event ->
            if (event.isEmpty && idleEntered.count > 0) {
                idleEntered.countDown()
                assertTrue(releaseIdle.await(3, TimeUnit.SECONDS))
            }
        }
        val failure = AtomicReference<Throwable?>()
        val initialTask = fixture.dispatcher.nextTask()
        val receiver = thread(name = "idle-receiver") { captureFailure(failure) { initialTask.run() } }
        var sender: Thread? = null

        try {
            assertTrue(idleEntered.await(3, TimeUnit.SECONDS))
            sender = thread(name = "idle-sender") {
                captureFailure(failure) { fixture.runner.submitNewEdge(fixture.edge, Reason.Initial) }
            }
            awaitBlockedOrFinished(sender)

            releaseIdle.countDown()
            sender.join(3000)
            receiver.join(3000)

            assertFalse(sender.isAlive)
            assertFalse(receiver.isAlive)
            assertNull(failure.get())
            assertEquals(expected = true, actual = fixture.reports.first())

            // Buffered work can be received in the initial task before it suspends.
            val edgeProcessingPending = fixture.callInspections.get() < 2
            if (edgeProcessingPending) {
                fixture.dispatcher.nextTask().run()
            }

            assertEquals(expected = 2, actual = fixture.callInspections.get())
            assertEquals(listOf(true, false, false, true), fixture.reports.toList())
        } finally {
            releaseIdle.countDown()
            sender?.join(3000)
            receiver.join(3000)
            fixture.close()
        }
    }

    private class Fixture(onControl: (QueueEmptinessChanged) -> Unit) {
        val dispatcher = ControlledDispatcher()
        val owner = CoroutineScope(SupervisorJob() + dispatcher)
        val reports = ConcurrentLinkedQueue<Boolean>()
        val callInspections = AtomicInteger()
        private val method = mockk<CommonMethod>(relaxed = true)
        private val statement = mockk<CommonInst>(relaxed = true)
        private val graph = mockk<ApplicationGraph<CommonMethod, CommonInst>>()
        private val traits = mockk<Traits<CommonMethod, CommonInst>>()
        private val unitResolver = UnitResolver<CommonMethod> { SingletonUnit }
        private val manager = object : TaintManager<CommonMethod, CommonInst>(
            traits = traits,
            graph = graph,
            unitResolver = unitResolver,
            getConfigForMethod = { null },
        ) {
            override fun handleControlEvent(event: ControlEvent) {
                if (event is QueueEmptinessChanged) {
                    onControl(event)
                    super.handleControlEvent(event)
                    reports += event.isEmpty
                }
            }
        }
        val runner: UniRunner<TaintDomainFact, TaintEvent<CommonInst>, CommonMethod, CommonInst>
        val edge: Edge<TaintDomainFact, CommonInst>
        private val job: Job

        init {
            every { method.name } returns "leaf"
            every { statement.location.method } returns method
            every { graph.methodOf(statement) } returns method
            every { graph.exitPoints(method) } returns emptySequence()
            every { graph.successors(statement) } returns emptySequence()
            every { traits.getCallExpr(statement) } answers {
                callInspections.incrementAndGet()
                null
            }
            runner = UniRunner(
                traits = traits,
                manager = manager,
                graph = graph,
                analyzer = TaintAnalyzer(
                    traits = traits,
                    graph = graph,
                    unitResolver = unitResolver,
                    getConfigForMethod = { null },
                ),
                unitResolver = unitResolver,
                unit = SingletonUnit,
                zeroFact = TaintZeroFact,
            )
            manager.runnerForUnit[SingletonUnit] = runner
            val vertex = Vertex<TaintDomainFact, CommonInst>(statement = statement, fact = TaintZeroFact)
            edge = Edge(vertex, vertex)
            job = owner.launch { runner.run(startMethods = emptyList()) }
        }

        fun close() {
            owner.cancel()
            while (!job.isCompleted) dispatcher.nextTask().run()
        }
    }

    private class ControlledDispatcher : CoroutineDispatcher() {
        private val tasks = LinkedBlockingQueue<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.add(block)
        }

        fun nextTask(): Runnable = tasks.poll(3, TimeUnit.SECONDS) ?: error("Missing dispatched runner step")
    }

    private fun awaitBlockedOrFinished(worker: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (true) {
            val settled = worker.state == Thread.State.BLOCKED || worker.state == Thread.State.TERMINATED
            if (settled) return

            assertTrue(System.nanoTime() < deadline, "Receiver neither blocked nor completed its dispatched step")
            Thread.yield()
        }
    }

    private fun captureFailure(destination: AtomicReference<Throwable?>, action: () -> Unit) {
        try {
            action()
        } catch (failure: Throwable) {
            destination.set(failure)
        }
    }
}
