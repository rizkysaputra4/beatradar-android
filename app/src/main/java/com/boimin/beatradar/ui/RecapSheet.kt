package com.boimin.beatradar.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.boimin.beatradar.data.Recap
import com.boimin.beatradar.ui.theme.Brand
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Weekly Tempo Recap: songs heard, the tempo spread, the BPM you lived in, streak and beats, shareable as a card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapSheet(recap: Recap, streak: Long?, beatsWeek: Long?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Your week in tempo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(period(recap), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (recap.songs == 0) {
                Text("Nothing logged this week yet. Tap the dial near some music.", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("SONGS", recap.songs.toString(), Modifier.weight(1f))
                Stat("YOUR BPM", recap.topBand?.let { "$it–${it + 9}" } ?: "—", Modifier.weight(1f))
                Stat("IN TARGET", recap.inTarget.toString(), Modifier.weight(1f))
            }
            Histogram(recap)
            val extras = listOfNotNull(
                recap.averageBpm?.let { "average $it BPM" },
                streak?.takeIf { it > 0 }?.let { "$it-day streak" },
                beatsWeek?.takeIf { it > 0 }?.let { "$it beats" },
            )
            if (extras.isNotEmpty()) Text(extras.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            recap.topSongs.firstOrNull()?.let { (label, plays) ->
                Text("Most played: $label${if (plays > 1) " ($plays×)" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { shareRecap(context, recap, streak, beatsWeek) }) { Text("Share card") }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
    ) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun Histogram(recap: Recap) {
    val max = recap.bands.maxOf { it.second }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        recap.bands.forEach { (band, plays) ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(fraction = (plays.toFloat() / max).coerceAtLeast(0.04f))
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(if (band == recap.topBand) Brand.Coral else MaterialTheme.colorScheme.primary.copy(alpha = if (plays > 0) 0.8f else 0.15f)),
            )
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Text("${recap.bands.first().first}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f))
        Text("${recap.bands.last().first + 10} BPM", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun period(r: Recap): String {
    val f = DateFormat.getDateInstance(DateFormat.MEDIUM)
    return "${f.format(Date(r.fromMs))} – ${f.format(Date(r.toMs))}"
}

/** Draws the recap as a 1080×1350 card and opens the share sheet with it. */
private fun shareRecap(context: Context, r: Recap, streak: Long?, beatsWeek: Long?) {
    val w = 1080
    val h = 1350
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(0xFF1D1735.toInt())
    val coral = 0xFFFF5A5F.toInt()
    val violet = 0xFFB9AEF5.toInt()
    val white = 0xFFFFFFFF.toInt()
    fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    c.drawText("BeatRadar · my week in tempo", 80f, 140f, paint(44f, violet, true))
    c.drawText(period(r), 80f, 200f, paint(36f, violet))
    c.drawText(r.topBand?.let { "$it–${it + 9}" } ?: "—", 80f, 420f, paint(200f, white, true))
    c.drawText("BPM I lived in", 80f, 490f, paint(44f, coral, true))
    // Histogram
    val max = r.bands.maxOf { it.second }.coerceAtLeast(1)
    val left = 80f
    val right = w - 80f
    val bottom = 900f
    val barW = (right - left) / r.bands.size
    r.bands.forEachIndexed { i, (band, plays) ->
        val bh = 300f * plays / max
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (band == r.topBand) coral else if (plays > 0) violet else 0x33B9AEF5 }
        c.drawRoundRect(RectF(left + i * barW + 4, bottom - bh.coerceAtLeast(6f), left + (i + 1) * barW - 4, bottom), 8f, 8f, p)
    }
    c.drawText("${r.bands.first().first}", left, bottom + 50, paint(30f, violet))
    val endLabel = "${r.bands.last().first + 10} BPM"
    val endPaint = paint(30f, violet)
    c.drawText(endLabel, right - endPaint.measureText(endLabel), bottom + 50, endPaint)
    val lines = listOfNotNull(
        "${r.songs} songs · ${r.inTarget} in my target",
        r.averageBpm?.let { "Average $it BPM" },
        listOfNotNull(streak?.takeIf { it > 0 }?.let { "$it-day streak" }, beatsWeek?.takeIf { it > 0 }?.let { "$it beats" }).joinToString(" · ").ifEmpty { null },
    )
    lines.forEachIndexed { i, line -> c.drawText(line, 80f, 1060f + i * 70, paint(46f, white, i == 0)) }
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "beatradar-recap.png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bmp.recycle()
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, "My week in tempo on BeatRadar")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
