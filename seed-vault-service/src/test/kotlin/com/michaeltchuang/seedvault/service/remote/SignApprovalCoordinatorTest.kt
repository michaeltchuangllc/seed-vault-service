package com.michaeltchuang.seedvault.service.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignApprovalCoordinatorTest {
    private var now = 0L
    private val coordinator = SignApprovalCoordinator(clock = { now }, grantMillis = 1_000)
    private val operation = SignOperation.bytes(address = "ADDRESS", data = byteArrayOf(1, 2, 3))

    private fun begin(
        caller: String = "client",
        operation: SignOperation = this.operation,
    ) = coordinator.begin(SignApprovalRequest(caller, "Client", operation, "Sign 3 bytes of data"))

    @Test
    fun approvedOperationCanBeUsedOnce() {
        coordinator.resolve(begin(), approved = true)

        assertTrue(coordinator.consume(setOf("client"), operation))
        assertFalse(coordinator.consume(setOf("client"), operation))
    }

    @Test
    fun deniedOperationCannotBeUsed() {
        coordinator.resolve(begin(), approved = false)

        assertFalse(coordinator.consume(setOf("client"), operation))
    }

    @Test
    fun nothingApprovedMeansNothingToUse() {
        assertFalse(coordinator.consume(setOf("client"), operation))
    }

    @Test
    fun approvalIsForTheAppThatGotIt() {
        coordinator.resolve(begin(caller = "client"), approved = true)

        assertFalse(coordinator.consume(setOf("other"), operation))
    }

    @Test
    fun approvalIsForTheExactOperation() {
        coordinator.resolve(begin(), approved = true)

        assertFalse(coordinator.consume(setOf("client"), SignOperation.bytes("ADDRESS", byteArrayOf(1, 2, 4))))
        assertFalse(coordinator.consume(setOf("client"), SignOperation.bytes("OTHER_ADDRESS", byteArrayOf(1, 2, 3))))
    }

    @Test
    fun approvalExpires() {
        coordinator.resolve(begin(), approved = true)
        now = 1_001

        assertFalse(coordinator.consume(setOf("client"), operation))
    }

    @Test
    fun transactionBytesAndSignerBothMatter() {
        val transaction = SignOperation.transaction("ADDRESS", byteArrayOf(1, 2, 3))

        assertEquals(transaction, SignOperation.transaction("ADDRESS", byteArrayOf(1, 2, 3)))
        assertNotEquals(transaction, SignOperation.transaction("ADDRESS", byteArrayOf(1, 2, 4)))
        assertNotEquals(transaction, SignOperation.transaction("OTHER_ADDRESS", byteArrayOf(1, 2, 3)))
        assertNotEquals(transaction, SignOperation.bytes("ADDRESS", byteArrayOf(1, 2, 3)))
    }

    @Test
    fun requestIsGoneOnceSettled() {
        val id = begin()
        assertTrue(coordinator.request(id) != null)

        coordinator.resolve(id, approved = true)

        assertNull(coordinator.request(id))
    }
}
