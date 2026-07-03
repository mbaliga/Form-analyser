package xyz.mdhv.formanalyser.wellness

import java.time.LocalDate
import kotlin.math.abs

/** Small numeric + date helpers shared across the wellness math. Pure. */
internal object Stats {
    fun datesInclusive(from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to.isBefore(from)) return emptyList()
        val out = ArrayList<LocalDate>()
        var d = from
        while (!d.isAfter(to)) { out.add(d); d = d.plusDays(1) }
        return out
    }

    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }

    /** Median absolute deviation from the median. */
    fun mad(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val m = median(values)
        return median(values.map { abs(it - m) })
    }
}
