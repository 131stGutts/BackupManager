package com.ayer.backupmanager.utils

import com.ayer.backupmanager.data.SyncRule
import java.util.*

object ScheduleUtils {
    fun calculateNextExecution(rule: SyncRule): Long {
        if (rule.scheduleType == "Manual" || !rule.isActive) return 0L

        val now = Calendar.getInstance()
        val next = Calendar.getInstance()
        next.set(Calendar.HOUR_OF_DAY, rule.scheduleHour)
        next.set(Calendar.MINUTE, rule.scheduleMinute)
        next.set(Calendar.SECOND, 0)
        next.set(Calendar.MILLISECOND, 0)

        when (rule.scheduleType) {
            "Daily" -> {
                if (next.before(now)) {
                    next.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            "Weekly" -> {
                next.set(Calendar.DAY_OF_WEEK, rule.scheduleDayOfWeek)
                while (next.before(now)) {
                    next.add(Calendar.WEEK_OF_YEAR, 1)
                }
            }
            "Monthly" -> {
                next.set(Calendar.DAY_OF_MONTH, rule.scheduleDayOfMonth)
                while (next.before(now)) {
                    next.add(Calendar.MONTH, 1)
                }
            }
        }
        return next.timeInMillis
    }

    fun getInitialDelay(rule: SyncRule): Long {
        val next = calculateNextExecution(rule)
        if (next == 0L) return 0L
        val delay = next - System.currentTimeMillis()
        return if (delay > 0) delay else 0L
    }
}
