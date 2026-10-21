package com.boimin.beatradar.dsp

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Benchmark on real, hand-annotated music: how often the live reading is the right tempo, and whether the beat grid
 * (the ripple) lands on the real beats. Runs every clip clean and through a simulated phone (small speaker and mic:
 * little below ~180 Hz or above ~7 kHz, room noise 20 dB down).
 *
 * Opt-in and slow: `MUSIC_BENCH=<dir> gradlew :app:testDebugUnitTest --tests '*MusicBench*' -i`, where `<dir>` holds
 * - `BallroomData/` (ISMIR 2004 Ballroom audio) and `BallroomAnnotations/` (CPJKU beat annotations), and/or
 * - `genres/` (GTZAN audio) and `gtzan_tempo_beat/` (tempo and beat annotations), and/or
 * - `giantsteps_wav/` (held-out GiantSteps clips with tempo only, written by the training scripts' prep.py).
 * `MUSIC_BENCH_LIST=<file>` keeps only the clips named in it. `MUSIC_BENCH_LIMIT=<n>` takes every k-th clip so about n run; results also go to `<dir>/results-<label>.csv`.
 */
class MusicBench {
    class Clip(val set: String, val genre: String, val name: String, val file: File, val bpm: Double, val beats: DoubleArray)
    class Audio(val pcm: ShortArray, val rate: Int)

    enum class Verdict { EXACT, HALF, DOUBLE, OTHER_OCTAVE, WRONG, NONE }

    class Result(
        val clip: Clip,
        val mode: String,
        /** The reading shown 10 s in and at the end of the clip. */
        val at10: Float?,
        val atEnd: Float?,
        /** Share of the settled, confident readings after 5 s that are the exact tempo. */
        val exactShare: Double,
        /** Share of predicted beats within 70 ms of an annotated one, over readings with the exact tempo. */
        val onBeat: Double,
        val confidence: Double,
        /** The engine's last reading, whatever its confidence. */
        val last: BeatUpdate?,
        val falseGaps: Int = 0,
    ) {
        val verdict10 = verdict(at10, clip.bpm)
        val verdictEnd = verdict(atEnd, clip.bpm)
    }

    /** The line between showing a reading and showing nothing. */
    private val minConfidence = System.getenv("MUSIC_BENCH_MIN_CONF")?.toFloatOrNull() ?: BeatEngine.MIN_CONFIDENCE

    @Test fun bench() {
        val root = System.getenv("MUSIC_BENCH")?.let(::File)
        assumeTrue("set MUSIC_BENCH to run", root != null && root.isDirectory)
        root!!
        var clips = ballroom(root) + gtzan(root) + giantsteps(root)
        assumeTrue("no clips found in $root", clips.isNotEmpty())
        // Only the clips named in a list (one name per line), e.g. the held-out songs the network never trained on.
        System.getenv("MUSIC_BENCH_LIST")?.let { list ->
            val names = File(list).readLines().map { it.trim() }.toSet()
            clips = clips.filter { it.name in names }
        }
        System.getenv("MUSIC_BENCH_LIMIT")?.toIntOrNull()?.let { n ->
            val step = (clips.size / n).coerceAtLeast(1)
            clips = clips.filterIndexed { i, _ -> i % step == 0 }
        }
        val label = System.getenv("MUSIC_BENCH_LABEL") ?: "run"
        val results = clips.parallelStream().flatMap { clip ->
            val audio = runCatching { read(clip.file) }.getOrNull() ?: return@flatMap java.util.stream.Stream.empty<Result>()
            listOf(run(clip, audio, "clean"), run(clip, phone(audio, clip.name.hashCode()), "phone")).stream()
        }.toList()

        report(results, label)
        File(root, "results-$label.csv").printWriter().use { w ->
            w.println("set,genre,name,mode,truth,at10,atEnd,verdict10,verdictEnd,exactShare,onBeat,confidence")
            for (r in results.sortedWith(compareBy({ it.clip.set }, { it.clip.name }, { it.mode }))) {
                w.println(
                    listOf(
                        r.clip.set, r.clip.genre, r.clip.name, r.mode, "%.1f".format(Locale.US, r.clip.bpm),
                        r.at10?.let { "%.1f".format(Locale.US, it) } ?: "", r.atEnd?.let { "%.1f".format(Locale.US, it) } ?: "",
                        r.verdict10, r.verdictEnd, "%.3f".format(Locale.US, r.exactShare), "%.3f".format(Locale.US, r.onBeat),
                        "%.3f".format(Locale.US, r.confidence),
                    ).joinToString(","),
                )
            }
        }
    }

    /** Feeds the clip through the engine in 20 ms buffers, as the phone does, and scores the readings. */
    private fun run(clip: Clip, audio: Audio, mode: String): Result {
        val updates = ArrayList<BeatUpdate>()
        val gaps = ArrayList<Long>()
        val engine = BeatEngine(
            audio.rate,
            object : BeatEngine.Listener {
                override fun onBeat(update: BeatUpdate) { updates += update }
                override fun onSongGap() { gaps += 0L }
            },
            // MUSIC_BENCH_DETECTOR=onset: the hand-written detector the network replaced, for comparison.
            detectorFactory = if (System.getenv("MUSIC_BENCH_DETECTOR") == "onset") { r -> OnsetBpmDetector(sampleRate = r) } else { _ -> BeatNetDetector() },
        )
        val chunk = audio.rate / 50
        var i = 0
        while (i < audio.pcm.size) {
            val n = minOf(chunk, audio.pcm.size - i)
            val buf = audio.pcm.copyOfRange(i, i + n)
            i += n
            val before = gaps.size
            engine.feed(buf, n, i * 1000L / audio.rate)
            if (gaps.size > before) gaps[gaps.size - 1] = i * 1000L / audio.rate
        }
        // What the screen shows: a reading only while it is confident.
        fun shown(atMs: Long): Float? = updates.lastOrNull { it.timeMs <= atMs }
            ?.takeIf { it.confidence >= minConfidence && atMs - it.timeMs < 1_500 }?.bpm
        val endMs = audio.pcm.size * 1000L / audio.rate
        val judged = updates.filter { it.timeMs >= 5_000 && it.settled && it.confidence >= minConfidence }
        val exact = judged.filter { verdict(it.bpm, clip.bpm) == Verdict.EXACT }
        val onBeat = exact.count { u -> clip.beats.any { abs(it * 1000 - u.lastBeatAtMs) <= 70 } }
        return Result(
            clip, mode,
            at10 = shown(10_000),
            atEnd = shown(endMs),
            exactShare = if (judged.isEmpty()) 0.0 else exact.size.toDouble() / judged.size,
            onBeat = if (exact.isEmpty()) 0.0 else onBeat.toDouble() / exact.size,
            confidence = updates.filter { it.timeMs >= 5_000 }.map { it.confidence.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0,
            last = updates.lastOrNull(),
            // A gap inside one song (not its fade-out at the very end) would wrongly end it.
            falseGaps = gaps.count { it < endMs - 3_000 },
        )
    }

    private fun report(results: List<Result>, label: String) {
        fun pct(n: Int, of: Int) = if (of == 0) "  -  " else "%5.1f%%".format(Locale.US, 100.0 * n / of)
        println("\n=== MusicBench [$label]: ${results.size / 2} clips ===")
        println(String.format(Locale.US, "%-22s %-6s %5s | %-7s %-7s %-7s %-7s | %-7s %-7s | %-7s %-7s", "group", "mode", "n", "exact", "half", "double", "wrong", "exact10", "steady", "onBeat", "conf"))
        val groups = results.groupBy { it.clip.set to it.mode }.toSortedMap(compareBy({ it.first }, { it.second })) +
            results.filter { it.clip.bpm >= 160 }.groupBy { "fast >=160 BPM" to it.mode }.toSortedMap(compareBy({ it.first }, { it.second })) +
            results.groupBy { "${it.clip.set}/${it.clip.genre}" to it.mode }.toSortedMap(compareBy({ it.first }, { it.second }))
        for ((key, rs) in groups) {
            val n = rs.size
            fun c(v: Verdict) = rs.count { it.verdictEnd == v }
            println(
                String.format(
                    Locale.US, "%-22s %-6s %5d | %-7s %-7s %-7s %-7s | %-7s %6.1f%% | %6.1f%% %6.3f",
                    key.first, key.second, n,
                    pct(c(Verdict.EXACT), n), pct(c(Verdict.HALF), n), pct(c(Verdict.DOUBLE), n),
                    pct(c(Verdict.WRONG) + c(Verdict.OTHER_OCTAVE) + c(Verdict.NONE), n),
                    pct(rs.count { it.verdict10 == Verdict.EXACT }, n),
                    100 * rs.map { it.exactShare }.average(),
                    100 * rs.filter { it.verdictEnd == Verdict.EXACT }.map { it.onBeat }.average().let { if (it.isNaN()) 0.0 else it },
                    rs.map { it.confidence }.average(),
                ),
            )
        }
        for (mode in listOf("clean", "phone")) {
            val rs = results.filter { it.mode == mode }
            println("gaps $mode: ${rs.count { it.falseGaps > 0 }} of ${rs.size} songs had a gap mid-song (${rs.sumOf { it.falseGaps }} in all): " +
                rs.filter { it.falseGaps > 0 }.take(8).joinToString { it.clip.name })
        }
        // Where to draw the line between showing a reading and showing nothing.
        for (mode in listOf("clean", "phone")) {
            val rs = results.filter { it.mode == mode }
            val line = listOf(0f, 0.1f, 0.15f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f).joinToString("  ") { t ->
                val shown = rs.filter { (it.last?.confidence ?: 0f) >= t && it.last != null }
                val right = shown.count { verdict(it.last!!.bpm, it.clip.bpm) == Verdict.EXACT }
                "≥%.2f: shown %s right %s".format(Locale.US, t, pct(shown.size, rs.size), pct(right, shown.size))
            }
            println("threshold $mode  $line")
        }
        println("exact/half/double/wrong: the reading at the end of the clip (±4%). exact10: right 10 s in. steady: share of")
        println("confident readings after 5 s that are exact. onBeat: predicted beats within 70 ms of a real one (exact clips).")
    }

    // --- Datasets ---

    private fun ballroom(root: File): List<Clip> {
        val audio = File(root, "BallroomData")
        val ann = File(root, "BallroomAnnotations")
        if (!audio.isDirectory || !ann.isDirectory) return emptyList()
        return audio.walkTopDown().filter { it.extension == "wav" }.mapNotNull { f ->
            val beatsFile = File(ann, f.nameWithoutExtension + ".beats")
            if (!beatsFile.exists()) return@mapNotNull null
            val beats = beatsFile.readLines().mapNotNull { it.trim().split(Regex("\\s+")).firstOrNull()?.toDoubleOrNull() }.toDoubleArray()
            if (beats.size < 4) return@mapNotNull null
            Clip("ballroom", f.parentFile.name, f.nameWithoutExtension, f, 60.0 / median(beats.toList().zipWithNext { a, b -> b - a }), beats)
        }.toList()
    }

    private fun gtzan(root: File): List<Clip> {
        val audio = File(root, "genres")
        val ann = File(root, "gtzan_tempo_beat")
        if (!audio.isDirectory || !ann.isDirectory) return emptyList()
        return audio.walkTopDown().filter { it.extension == "au" || it.extension == "wav" }.mapNotNull { f ->
            // genres/blues/blues.00000.au -> gtzan_blues_00000
            val (genre, no) = f.nameWithoutExtension.split('.').takeIf { it.size == 2 } ?: return@mapNotNull null
            val bpm = File(ann, "tempo/gtzan_${genre}_$no.bpm").takeIf { it.exists() }?.readText()?.trim()?.toDoubleOrNull()
                ?: return@mapNotNull null
            val beats = File(ann, "beats/gtzan_${genre}_$no.beats").takeIf { it.exists() }?.readLines()
                ?.mapNotNull { it.trim().split(Regex("\\s+")).firstOrNull()?.toDoubleOrNull() }?.toDoubleArray() ?: DoubleArray(0)
            Clip("gtzan", genre, "${genre}_$no", f, bpm, beats)
        }.toList()
    }

    /** `giantsteps_wav/` (written by prep.py): held-out GiantSteps clips as WAV, tempo in a .bpm file; no beats. */
    private fun giantsteps(root: File): List<Clip> {
        val dir = File(root, "giantsteps_wav")
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.extension == "wav" }.orEmpty().sortedBy { it.name }.mapNotNull { f ->
            val bpm = File(dir, f.nameWithoutExtension + ".bpm").takeIf { it.exists() }?.readText()?.trim()?.toDoubleOrNull()
                ?: return@mapNotNull null
            Clip("giantsteps", "edm", f.nameWithoutExtension, f, bpm, DoubleArray(0))
        }
    }

    // --- Audio ---

    private fun read(f: File): Audio = if (f.extension == "au") readAu(f) else readWav(f)

    /** PCM WAV, 8/16-bit, any channel count (mixed to mono). */
    private fun readWav(f: File): Audio {
        val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        b.position(12)
        var channels = 1
        var rate = 44_100
        var bits = 16
        while (b.remaining() >= 8) {
            val id = String(ByteArray(4).also { b.get(it) })
            val size = b.int
            val start = b.position()
            when (id) {
                "fmt " -> {
                    b.short; channels = b.short.toInt(); rate = b.int; b.int; b.short; bits = b.short.toInt()
                }
                "data" -> {
                    val frames = minOf(size, b.remaining()) / (bits / 8) / channels
                    val out = ShortArray(frames)
                    for (i in 0 until frames) {
                        var sum = 0
                        for (c in 0 until channels) sum += if (bits == 16) b.short.toInt() else ((b.get().toInt() and 0xff) - 128) shl 8
                        out[i] = (sum / channels).toShort()
                    }
                    return Audio(out, rate)
                }
            }
            b.position(start + size + (size and 1))
        }
        error("no data in $f")
    }

    /** Sun .au, 16-bit linear PCM (GTZAN). */
    private fun readAu(f: File): Audio {
        val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.BIG_ENDIAN)
        check(b.int == 0x2e736e64) { "not .au: $f" }
        val offset = b.int
        b.int
        check(b.int == 3) { "not 16-bit PCM: $f" }
        val rate = b.int
        val channels = b.int
        b.position(offset)
        val frames = b.remaining() / 2 / channels
        val out = ShortArray(frames)
        for (i in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) sum += b.short.toInt()
            out[i] = (sum / channels).toShort()
        }
        return Audio(out, rate)
    }

    /**
     * A phone hearing a speaker across a room: steep high-pass at 180 Hz (small speaker and mic), low-pass at 7 kHz,
     * a little room echo, and noise 20 dB under the music.
     */
    private fun phone(a: Audio, seed: Int): Audio {
        val x = DoubleArray(a.pcm.size) { a.pcm[it] / 32768.0 }
        var y = biquad(biquad(x, a.rate, 180.0, highPass = true), a.rate, 180.0, highPass = true)
        y = biquad(y, a.rate, minOf(7_000.0, 0.45 * a.rate), highPass = false)
        val d1 = (0.023 * a.rate).toInt()
        val d2 = (0.041 * a.rate).toInt()
        for (i in y.indices.reversed()) {
            if (i >= d1) y[i] += 0.3 * y[i - d1]
            if (i >= d2) y[i] += 0.2 * y[i - d2]
        }
        val rms = sqrt(y.sumOf { it * it } / y.size)
        val r = Random(seed)
        var pink = 0.0
        val noise = DoubleArray(y.size) { pink = 0.97 * pink + 0.03 * r.nextDouble(-1.0, 1.0); pink }
        val noiseRms = sqrt(noise.sumOf { it * it } / noise.size).coerceAtLeast(1e-12)
        val k = rms * 0.1 / noiseRms
        val peak = y.indices.maxOf { abs(y[it] + k * noise[it]) }.coerceAtLeast(1e-9)
        val gain = minOf(1.0, 0.9 / peak)
        return Audio(ShortArray(y.size) { ((y[it] + k * noise[it]) * gain * 32767).toInt().toShort() }, a.rate)
    }

    private fun biquad(x: DoubleArray, rate: Int, hz: Double, highPass: Boolean): DoubleArray {
        val w = 2 * PI * hz / rate
        val alpha = sin(w) / (2 * 0.707)
        val c = cos(w)
        val b0 = if (highPass) (1 + c) / 2 else (1 - c) / 2
        val b1 = if (highPass) -(1 + c) else 1 - c
        val a0 = 1 + alpha
        val a1 = -2 * c
        val a2 = 1 - alpha
        val out = DoubleArray(x.size)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in x.indices) {
            val y = (b0 * x[i] + b1 * x1 + b0 * x2 - a1 * y1 - a2 * y2) / a0
            x2 = x1; x1 = x[i]; y2 = y1; y1 = y
            out[i] = y
        }
        return out
    }

    companion object {
        fun median(v: List<Double>): Double = v.sorted().let { it[it.size / 2] }

        fun verdict(reading: Float?, truth: Double): Verdict {
            if (reading == null) return Verdict.NONE
            fun near(f: Double) = abs(reading - truth * f) <= truth * f * 0.04
            return when {
                near(1.0) -> Verdict.EXACT
                near(0.5) -> Verdict.HALF
                near(2.0) -> Verdict.DOUBLE
                near(1.0 / 3) || near(3.0) || near(2.0 / 3) || near(1.5) -> Verdict.OTHER_OCTAVE
                else -> Verdict.WRONG
            }
        }
    }
}
