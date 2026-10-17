package com.buddhanag.luminux.feature.viewer

import android.content.Context
import android.text.format.Formatter
import com.buddhanag.luminux.data.MediaItem
import com.buddhanag.luminux.data.MediaType
import com.buddhanag.luminux.data.PhotoRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** The details panel's rows (DetailsPanel.swift `AssetDetails`); each row is skipped when its value is unknown. */
internal data class AssetDetails(val rows: List<Row>, val location: Pair<Double, Double>?) {
    data class Row(val label: String, val value: String)

    companion object {
        suspend fun load(context: Context, item: MediaItem, photos: PhotoRepository): AssetDetails {
            val rows = mutableListOf<Row>()
            item.effectiveDate?.let {
                val format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT)
                rows += Row("taken", format.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())))
            }
            item.displayName.takeIf { it.isNotEmpty() }?.let { rows += Row("file name", it) }
            rows += Row("dimensions", DetailsFormat.dimensions(item.width, item.height))
            if (item.sizeBytes > 0) rows += Row("size", Formatter.formatShortFileSize(context, item.sizeBytes))
            rows += Row("type", if (item.type == MediaType.VIDEO) "video · ${DetailsFormat.time(item.durationMs / 1000.0)}" else "photo")
            if (item.type == MediaType.IMAGE) {
                val exif = photos.exif(item)
                listOfNotNull(exif.make, exif.model).joinToString(" ").takeIf { it.isNotBlank() }?.let { rows += Row("camera", it) }
                DetailsFormat.exposure(exif.fNumber, exif.exposureTime, exif.iso, exif.focalLength35mm)?.let { rows += Row("exposure", it) }
            }
            val location = photos.location(item)
            location?.let { rows += Row("location", DetailsFormat.location(it.first, it.second)) }
            return AssetDetails(rows, location)
        }
    }
}

/** Pure formatters, matching the iOS output. */
internal object DetailsFormat {
    fun dimensions(w: Int, h: Int) = "$w × $h · ${String.format(Locale.ROOT, "%.1f", w.toLong() * h / 1_000_000.0)} MP"

    fun location(lat: Double, lon: Double) = String.format(Locale.ROOT, "%.5f, %.5f", lat, lon)

    /** `f/1.8 · 1/120 s · ISO 100 · 26 mm`; null when nothing is known. */
    fun exposure(fNumber: Double?, exposureTime: Double?, iso: Int?, focal35mm: Int?): String? {
        val parts = buildList {
            fNumber?.let { add("f/${trim((it * 10).roundToInt() / 10.0)}") }
            exposureTime?.takeIf { it > 0 }?.let { add(if (it >= 1) "${trim(it)} s" else "1/${(1 / it).roundToLong()} s") }
            iso?.let { add("ISO $it") }
            focal35mm?.let { add("$it mm") }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /** Minutes and seconds, rounded down: 65.9 → "1:05". Minutes don't wrap into hours, like iOS `.minuteSecond`. */
    fun time(seconds: Double): String {
        val total = floor(seconds.coerceAtLeast(0.0)).toLong()
        return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
    }

    /** The time left, rounded up: duration 10, current 3.2 → "-0:07". */
    fun remaining(duration: Double, current: Double) = "-" + time(ceil(duration - current))

    private fun trim(v: Double) = if (v == floor(v)) v.toLong().toString() else v.toBigDecimal().stripTrailingZeros().toPlainString()
}
