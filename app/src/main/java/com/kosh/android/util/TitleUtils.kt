package com.kosh.android.util

import java.time.LocalDate

const val MAX_TITLE_LENGTH = 80

/** Default document name, e.g. `Scan_2026-10-13`. */
fun defaultTitle(date: LocalDate = LocalDate.now()): String = "Scan_$date"

/** Keeps user-entered titles to a sane length while they type. */
fun clampTitle(input: String): String = input.take(MAX_TITLE_LENGTH)
