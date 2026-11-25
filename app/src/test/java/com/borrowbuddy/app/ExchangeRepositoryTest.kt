package com.borrowbuddy.app

import com.borrowbuddy.app.model.CampusItem
import com.borrowbuddy.app.model.ExchangeRepository
import com.borrowbuddy.app.model.ExchangeStatus
import com.borrowbuddy.app.model.ExchangeType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ExchangeRepositoryTest {

    @Before
    fun setUp() {
        // Any setup if required
    }

    @Test
    fun testInitialExchangesExist() {
        val all = ExchangeRepository.getAll()
        assertTrue("Repository should have initial seeded exchange items", all.isNotEmpty())
        
        val borrowItems = ExchangeRepository.getBorrowItems()
        val lendItems = ExchangeRepository.getLendItems()
        assertTrue("Should have borrow items", borrowItems.isNotEmpty())
        assertTrue("Should have lend items", lendItems.isNotEmpty())
        assertEquals(all.size, borrowItems.size + lendItems.size)
    }

    @Test
    fun testFindByIdReturnsCorrectItem() {
        val all = ExchangeRepository.getAll()
        val first = all.first()
        val found = ExchangeRepository.findById(first.id)
        assertNotNull(found)
        assertEquals(first.title, found?.title)
        assertEquals(first.exchangeCode, found?.exchangeCode)
    }

    @Test
    fun testFindByIdWithUnknownIdReturnsNull() {
        val found = ExchangeRepository.findById("non_existent_id_99999")
        assertNull(found)
    }

    @Test
    fun testStateTransitions_acceptRequest() {
        // Find an incoming request
        val incoming = ExchangeRepository.getLendItems().firstOrNull { it.status == ExchangeStatus.AWAITING_RESPONSE }
        assertNotNull("Should have an incoming request to test", incoming)
        val id = incoming!!.id

        ExchangeRepository.acceptRequest(id)
        val updated = ExchangeRepository.findById(id)
        assertNotNull(updated)
        assertEquals(ExchangeStatus.HANDOVER_PENDING, updated?.status)
        assertEquals(3, updated?.currentStepIndex)
    }

    @Test
    fun testStateTransitions_declineRequest() {
        // Create a temporary borrow request to test declining
        val testItem = CampusItem(
            id = "test_item_decline",
            title = "Test Item for Decline",
            category = "TECH",
            pickupSpot = "Main Library"
        )
        val newExchange = ExchangeRepository.addBorrowRequest(
            item = testItem,
            returnTime = "Tomorrow, 5:00 PM",
            borrowerNote = "Testing decline",
            pin = "1234"
        )

        ExchangeRepository.declineRequest(newExchange.id)
        val updated = ExchangeRepository.findById(newExchange.id)
        assertNotNull(updated)
        assertEquals(ExchangeStatus.DECLINED, updated?.status)
    }

    @Test
    fun testStateTransitions_fullLifecycle_HandoverToComplete() {
        // Create an item and walk through lifecycle: AWAITING -> HANDOVER_PENDING -> BORROWED -> RETURN_PENDING -> COMPLETED
        val testItem = CampusItem(
            id = "test_item_lifecycle",
            title = "Test Lifecycle Book",
            category = "BOOKS",
            pickupSpot = "IT Block Entrance"
        )
        val exchange = ExchangeRepository.addBorrowRequest(
            item = testItem,
            returnTime = "Tomorrow, 2:00 PM",
            borrowerNote = "Lifecycle test",
            pin = "4321"
        )
        val id = exchange.id

        // 1. Accept -> HANDOVER_PENDING
        ExchangeRepository.acceptRequest(id)
        assertEquals(ExchangeStatus.HANDOVER_PENDING, ExchangeRepository.findById(id)?.status)
        assertEquals(3, ExchangeRepository.findById(id)?.currentStepIndex)

        // 2. Confirm Handover -> BORROWED
        ExchangeRepository.confirmHandover(id)
        assertEquals(ExchangeStatus.BORROWED, ExchangeRepository.findById(id)?.status)
        assertEquals(4, ExchangeRepository.findById(id)?.currentStepIndex)

        // 3. Initiate Return -> RETURN_PENDING
        ExchangeRepository.initiateReturn(id)
        assertEquals(ExchangeStatus.RETURN_PENDING, ExchangeRepository.findById(id)?.status)
        assertEquals(5, ExchangeRepository.findById(id)?.currentStepIndex)

        // 4. Confirm Return -> COMPLETED
        ExchangeRepository.confirmReturn(id)
        assertEquals(ExchangeStatus.COMPLETED, ExchangeRepository.findById(id)?.status)
        assertEquals(7, ExchangeRepository.findById(id)?.currentStepIndex)
    }

    @Test
    fun testIsItemAvailable() {
        // Blank item is considered available
        assertTrue(ExchangeRepository.isItemAvailable(""))

        // Item committed to active exchange is unavailable
        val activeBorrow = ExchangeRepository.getAll().firstOrNull { 
            it.status in listOf(ExchangeStatus.HANDOVER_PENDING, ExchangeStatus.BORROWED, ExchangeStatus.RETURN_PENDING)
        }
        if (activeBorrow != null) {
            assertFalse(ExchangeRepository.isItemAvailable(activeBorrow.itemId))
        }

        // Fresh random item is available
        assertTrue(ExchangeRepository.isItemAvailable("completely_unrelated_item_id_xyz"))
    }

    @Test
    fun testListenerNotificationOnStateChange() {
        var notified = false
        val listener = { notified = true }
        ExchangeRepository.registerListener(listener)

        val item = ExchangeRepository.getAll().first()
        ExchangeRepository.markCategorySeen("NON_EXISTENT_KEY") // No-op won't notify
        assertFalse(notified)

        ExchangeRepository.addUnseenActivity("TEST_KEY_NOTIF")
        assertTrue(notified)

        ExchangeRepository.unregisterListener(listener)
        notified = false
        ExchangeRepository.addUnseenActivity("TEST_KEY_NOTIF_2")
        assertFalse("Unregistered listener should not receive notifications", notified)
    }

    @Test
    fun testUnseenActivityTracking() {
        ExchangeRepository.addUnseenActivity("BORROW_TEST_UNSEEN")
        assertTrue(ExchangeRepository.isCategoryUnseen("BORROW_TEST_UNSEEN"))
        assertTrue(ExchangeRepository.hasUnseenActivity(ExchangeType.BORROW))

        ExchangeRepository.markCategorySeen("BORROW_TEST_UNSEEN")
        assertFalse(ExchangeRepository.isCategoryUnseen("BORROW_TEST_UNSEEN"))
    }

    @Test
    fun testClearSession_removesDynamicExchanges() {
        val testItem = CampusItem(
            id = "test_item_clear_session",
            title = "Test Clear Session Item",
            category = "TECH",
            pickupSpot = "Main Library"
        )
        val newExchange = ExchangeRepository.addBorrowRequest(
            item = testItem,
            returnTime = "Tomorrow, 5:00 PM",
            borrowerNote = "Testing clear session",
            pin = "9999"
        )
        assertNotNull(ExchangeRepository.findById(newExchange.id))

        // Clear session
        ExchangeRepository.clearSession()
        assertNull("Dynamic exchange should be removed after clearSession", ExchangeRepository.findById(newExchange.id))
    }

    @Test
    fun testClearSession_restoresMutatedDemoExchangeStatuses() {
        // 1. Mutate an incoming seed request by accepting it
        val incoming = ExchangeRepository.getLendItems().firstOrNull { it.status == ExchangeStatus.AWAITING_RESPONSE }
        assertNotNull(incoming)
        val id = incoming!!.id

        ExchangeRepository.acceptRequest(id)
        assertEquals(ExchangeStatus.HANDOVER_PENDING, ExchangeRepository.findById(id)?.status)

        // 2. Clear session
        ExchangeRepository.clearSession()

        // 3. Verify original demo status is restored to AWAITING_RESPONSE
        val restored = ExchangeRepository.findById(id)
        assertNotNull(restored)
        assertEquals(ExchangeStatus.AWAITING_RESPONSE, restored?.status)
        assertEquals(1, restored?.currentStepIndex)
    }
}
