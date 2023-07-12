package org.usvm.samples.stdlib

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.test.util.checkers.eq
import org.usvm.util.isException
import java.util.Date

class DateExampleTest : JavaMethodTestRunner() {
    @Disabled("Virtual call: sun.util.calendar.BaseCalendar.Date.getNormalizedYear")
    @Suppress("SpellCheckingInspection")
    @Tag("slow")
    @Test
    fun testGetTimeWithNpeChecksForNonPublicFields() {
        checkDiscoveredPropertiesWithExceptions(
            DateExample::getTime,
            eq(5),
            *commonMatchers,
            { _, date: Date?, r: Result<Boolean> ->
                val cdate = date!!.getDeclaredFieldValue("cdate")
                val calendarDate = cdate!!.getDeclaredFieldValue("date")

                calendarDate == null && r.isException<NullPointerException>()
            },
            { _, date: Date?, r: Result<Boolean> ->
                val cdate = date!!.getDeclaredFieldValue("cdate")
                val calendarDate = cdate!!.getDeclaredFieldValue("date")

                val gcal = date.getDeclaredFieldValue("gcal")

                val normalized = calendarDate!!.getDeclaredFieldValue("normalized") as Boolean
                val gregorianYear = calendarDate.getDeclaredFieldValue("gregorianYear") as Int

                gcal == null && !normalized && gregorianYear >= 1582 && r.isException<NullPointerException>()
            }
        )
    }

    @Test
    @Disabled("Sequence is empty.")
    fun testGetTimeWithoutReflection() {
        checkDiscoveredPropertiesWithExceptions(
            DateExample::getTime,
            eq(3),
            *commonMatchers
        )
    }

    private val commonMatchers = arrayOf(
        { _: DateExample, date: Date?, r: Result<Boolean> -> date == null && r.isException<NullPointerException>() },
        { _, date: Date?, r: Result<Boolean> -> date != null && date.time == 100L && r.getOrThrow() },
        { _, date: Date?, r: Result<Boolean> -> date != null && date.time != 100L && !r.getOrThrow() }
    )

    private fun Any.getDeclaredFieldValue(fieldName: String): Any? {
        val declaredField = javaClass.getDeclaredField(fieldName)
        declaredField.isAccessible = true

        return declaredField.get(this)
    }
}