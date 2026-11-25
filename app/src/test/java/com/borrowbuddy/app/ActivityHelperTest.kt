package com.borrowbuddy.app

import com.borrowbuddy.app.model.ExchangeItem
import com.borrowbuddy.app.model.ExchangeStatus
import com.borrowbuddy.app.model.ExchangeType
import com.borrowbuddy.app.util.ActivityHelper
import org.junit.Assert.*
import org.junit.Test

class ActivityHelperTest {

    private fun createTestExchange(
        type: ExchangeType,
        status: ExchangeStatus,
        requestDate: String = "Today, 10:30 AM",
        handoverDate: String = ""
    ): ExchangeItem {
        return ExchangeItem(
            id = "test_${type.name}_${status.name}",
            type = type,
            title = "Test Item",
            ownerName = "Rahul Dave",
            borrowerName = "Harsh Mistry",
            pickupSpot = "Main Library",
            duration = "1 Day",
            requestDate = requestDate,
            handoverDate = handoverDate,
            status = status
        )
    }

    @Test
    fun testGetActivityDescription_borrowFlow() {
        val awaiting = createTestExchange(ExchangeType.BORROW, ExchangeStatus.AWAITING_RESPONSE)
        assertEquals("You requested to borrow this item.", ActivityHelper.getActivityDescription(awaiting))

        val accepted = createTestExchange(ExchangeType.BORROW, ExchangeStatus.HANDOVER_PENDING)
        assertEquals("Borrowing request accepted.", ActivityHelper.getActivityDescription(accepted))

        val borrowed = createTestExchange(ExchangeType.BORROW, ExchangeStatus.BORROWED)
        assertEquals("You borrowed this item.", ActivityHelper.getActivityDescription(borrowed))

        val returnPending = createTestExchange(ExchangeType.BORROW, ExchangeStatus.RETURN_PENDING)
        assertEquals("Return in progress.", ActivityHelper.getActivityDescription(returnPending))

        val completed = createTestExchange(ExchangeType.BORROW, ExchangeStatus.COMPLETED)
        assertEquals("You returned this item.", ActivityHelper.getActivityDescription(completed))

        val declined = createTestExchange(ExchangeType.BORROW, ExchangeStatus.DECLINED)
        assertEquals("Borrow request declined.", ActivityHelper.getActivityDescription(declined))

        val cancelled = createTestExchange(ExchangeType.BORROW, ExchangeStatus.CANCELLED)
        assertEquals("Borrow request cancelled.", ActivityHelper.getActivityDescription(cancelled))

        val overdue = createTestExchange(ExchangeType.BORROW, ExchangeStatus.OVERDUE)
        assertEquals("Return overdue.", ActivityHelper.getActivityDescription(overdue))
    }

    @Test
    fun testGetActivityDescription_lendFlow() {
        val awaiting = createTestExchange(ExchangeType.LEND, ExchangeStatus.AWAITING_RESPONSE)
        assertEquals("You received a borrowing request.", ActivityHelper.getActivityDescription(awaiting))

        val handover = createTestExchange(ExchangeType.LEND, ExchangeStatus.HANDOVER_PENDING)
        assertEquals("You lent this item.", ActivityHelper.getActivityDescription(handover))

        val returnPending = createTestExchange(ExchangeType.LEND, ExchangeStatus.RETURN_PENDING)
        assertEquals("Borrower returned this item.", ActivityHelper.getActivityDescription(returnPending))

        val completed = createTestExchange(ExchangeType.LEND, ExchangeStatus.COMPLETED)
        assertEquals("You lent this item.", ActivityHelper.getActivityDescription(completed))

        val declined = createTestExchange(ExchangeType.LEND, ExchangeStatus.DECLINED)
        assertEquals("Lending request declined.", ActivityHelper.getActivityDescription(declined))

        val cancelled = createTestExchange(ExchangeType.LEND, ExchangeStatus.CANCELLED)
        assertEquals("Lending request cancelled.", ActivityHelper.getActivityDescription(cancelled))

        val overdue = createTestExchange(ExchangeType.LEND, ExchangeStatus.OVERDUE)
        assertEquals("Item return overdue.", ActivityHelper.getActivityDescription(overdue))
    }

    @Test
    fun testGetActivityDate() {
        val itemWithHandover = createTestExchange(
            type = ExchangeType.BORROW,
            status = ExchangeStatus.BORROWED,
            requestDate = "Yesterday, 10:00 AM",
            handoverDate = "Today, 12:00 PM"
        )
        assertEquals("Today, 12:00 PM", ActivityHelper.getActivityDate(itemWithHandover))

        val itemAwaiting = createTestExchange(
            type = ExchangeType.BORROW,
            status = ExchangeStatus.AWAITING_RESPONSE,
            requestDate = "Yesterday, 10:00 AM",
            handoverDate = "Today, 12:00 PM"
        )
        assertEquals("Yesterday, 10:00 AM", ActivityHelper.getActivityDate(itemAwaiting))
    }

    @Test
    fun testParseActivityTimestamp_handlesVariousFormats() {
        val now = System.currentTimeMillis()

        // Empty string
        assertEquals(0L, ActivityHelper.parseActivityTimestamp(""))
        assertEquals(0L, ActivityHelper.parseActivityTimestamp("   "))

        // "Today, 10:30 AM"
        val todayTs = ActivityHelper.parseActivityTimestamp("Today, 10:30 AM")
        assertTrue("Today timestamp should be positive", todayTs > 0L)

        // "Yesterday, 4:15 PM"
        val yesterdayTs = ActivityHelper.parseActivityTimestamp("Yesterday, 4:15 PM")
        assertTrue("Yesterday timestamp should be positive", yesterdayTs > 0L)
        assertTrue("Yesterday should be earlier than today", yesterdayTs < todayTs || (todayTs - yesterdayTs) > 0)

        // "3 days ago"
        val daysAgoTs = ActivityHelper.parseActivityTimestamp("3 days ago")
        assertTrue("Days ago timestamp should be positive", daysAgoTs > 0L)
        assertTrue(daysAgoTs < now)

        // Standard date format e.g. "15 Nov 2026"
        val standardDateTs = ActivityHelper.parseActivityTimestamp("15 Nov 2026")
        assertTrue("Standard date string should parse without throwing", standardDateTs >= 0L)
    }
}
