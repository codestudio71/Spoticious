package com.codestudio71.spoticious.data.wrapped

import java.util.Calendar

enum class WrappedPeriod {
    LAST_WEEK,
    THIS_MONTH,
    THIS_YEAR,
    ALL_TIME,
    ;

    fun sinceMs(nowMs: Long = System.currentTimeMillis()): Long =
        when (this) {
            ALL_TIME -> 0L
            LAST_WEEK -> nowMs - 7L * 24L * 60L * 60L * 1000L
            THIS_MONTH -> startOfMonth(nowMs)
            THIS_YEAR -> startOfYear(nowMs)
        }

    private fun startOfMonth(nowMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMs
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun startOfYear(nowMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMs
        cal.set(Calendar.MONTH, Calendar.JANUARY)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
