package com.averycorp.prismtask.ui.components

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Converts a Material3 `DatePickerState.selectedDateMillis` into local-midnight
 * epoch millis.
 *
 * Material3's date picker returns the selected date as **UTC midnight** so that
 * the state is timezone-independent. Downstream code (task due dates, filters,
 * display) treats millis as local, so without this conversion the chosen date
 * can drift to the previous calendar day whenever the device is west of UTC —
 * e.g. the user taps "Apr 19" in EDT (UTC-4) and gets back
 * `Apr 19 00:00 UTC` = `Apr 18 20:00 EDT`, which then renders as "Apr 18".
 */
fun datePickerToLocalMillis(
    selectedDateMillis: Long?,
    zone: ZoneId = ZoneId.systemDefault()
): Long? {
    if (selectedDateMillis == null) return null
    val localDate = Instant.ofEpochMilli(selectedDateMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
    return localDate.atStartOfDay(zone).toInstant().toEpochMilli()
}
