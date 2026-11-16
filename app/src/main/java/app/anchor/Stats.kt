package app.anchor

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate

object Stats {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("stats", Context.MODE_PRIVATE)
    private fun dayKey() = LocalDate.now().toString()

    @Synchronized
    fun recordBlock(ctx: Context) {
        val p = prefs(ctx)
        val day = dayKey()
        val today = if (p.getString("day", null) == day) p.getInt("today", 0) else 0
        p.edit {
            putString("day", day)
            putInt("today", today + 1)
            putLong("total", p.getLong("total", 0L) + 1)
        }
    }

    fun blockedToday(ctx: Context): Int {
        val p = prefs(ctx)
        return if (p.getString("day", null) == dayKey()) p.getInt("today", 0) else 0
    }

    fun blockedTotal(ctx: Context): Long = prefs(ctx).getLong("total", 0L)
}
