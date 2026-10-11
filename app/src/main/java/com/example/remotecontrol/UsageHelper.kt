package com.example.remotecontrol

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.*

object UsageHelper {
    fun getAppUsageStats(context: Context): List<Pair<String, Long>> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        return stats?.map { it.packageName to it.totalTimeInForeground }
            ?.filter { it.second > 0 }
            ?.sortedByDescending { it.second } ?: emptyList()
    }
}
