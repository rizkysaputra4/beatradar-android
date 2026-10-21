package com.boimin.beatradar.dsp

import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Benchmark: tempo reliability with background noise (room, traffic, chatter, clatter) at several levels.
 * Slow, so it only runs on request: `NOISE_BENCH=1 gradlew :app:testDebugUnitTest --tests '*NoiseBench*' -i`.
 * `BENCH_OUT=<dir>` writes the clips as WAV files for playing to a phone.
 */
class NoiseBench {
    private val sr = 44_100
    private val seconds = 30.0

    class Song(val pcm: FloatArray, val onsets: List<Double>, val events: List<Double>)

    private fun env(t: Double, decay: Number) = exp(-t * decay.toDouble())

    private fun add(out: FloatArray, at: Double, len: Double, f: (Double) -> Double) {
        val s = (at * sr).toInt()
        val n = (len * sr).toInt()
        for (k in 0 until n) {
            val i = s + k
            if (i < 0 || i >= out.size) continue
            out[i] += f(k.toDouble() / sr).toFloat()
        }
    }

    private fun song(style: String, bpm: Double, seed: Int): Song {
        val out = FloatArray((seconds * sr).toInt())
        val r = Random(seed)
        val beat = 60.0 / bpm
        val onsets = mutableListOf<Double>()
        val events = mutableListOf<Double>()
        val beats = (seconds / beat).toInt()
        val roots = listOf(55.0, 65.4, 49.0, 73.4)
        // Sustained pad under everything: harmonic content without onsets.
        for (i in out.indices) {
            val t = i.toDouble() / sr
            val root = roots[((t / (beat * 8)).toInt()) % roots.size] * 4
            out[i] += (0.04 * (sin(2 * PI * root * t) + sin(2 * PI * root * 1.25 * t) + sin(2 * PI * root * 1.5 * t))).toFloat()
        }
        for (b in 0 until beats) {
            val jitter = if (style == "edm") 0.0 else r.nextDouble(-0.008, 0.008)
            val t = b * beat + jitter
            val bar = b % 4
            val root = roots[(b / 8) % roots.size]
            when (style) {
                "edm" -> {
                    add(out, t, 0.3) { x -> 0.9 * sin(2 * PI * (50 + 100 * env(x, 30)) * x) * env(x, 10) }
                    if (bar == 1 || bar == 3) add(out, t, 0.2) { x -> 0.35 * r.nextDouble(-1.0, 1.0) * env(x, 25) }
                    add(out, t + beat / 2, 0.15) { x -> 0.18 * r.nextDouble(-1.0, 1.0) * env(x, 30) }
                    add(out, t + beat / 2, beat / 2) { x -> 0.3 * sin(2 * PI * root * x) * env(x, 6) }
                    onsets += t; events += t; events += t + beat / 2
                }
                "rock" -> {
                    if (bar == 0 || bar == 2) add(out, t, 0.3) { x -> 0.8 * sin(2 * PI * (55 + 80 * env(x, 30)) * x) * env(x, 14) }
                    if (bar == 2) add(out, t + beat / 2, 0.3) { x -> 0.6 * sin(2 * PI * (55 + 80 * env(x, 30)) * x) * env(x, 14) }
                    if (bar == 1 || bar == 3) add(out, t, 0.25) { x -> 0.45 * (r.nextDouble(-1.0, 1.0) * 0.7 + 0.3 * sin(2 * PI * 190 * x)) * env(x, 18) }
                    for (h in 0..1) add(out, t + h * beat / 2, 0.05) { x -> 0.1 * r.nextDouble(-1.0, 1.0) * env(x, 90) }
                    if (bar == 0) add(out, t, beat * 2) { x ->
                        0.12 * (1..6).sumOf { k -> sin(2 * PI * root * 2 * k * x) / k } * env(x, 2.5)
                    }
                    add(out, t, beat) { x -> 0.25 * sin(2 * PI * root * x) * env(x, 5) }
                    onsets += t; events += t; events += t + beat / 2
                }
                else -> { // acoustic: plucked notes, no drums, uneven loudness, some eighth-note fills
                    val v = r.nextDouble(0.5, 1.0)
                    val f = root * listOf(2, 3, 4, 5)[r.nextInt(4)]
                    add(out, t, 0.8) { x -> v * 0.35 * (1..5).sumOf { k -> sin(2 * PI * f * k * x) / (k * k) } * env(x, 6) }
                    if (r.nextDouble() < 0.4) {
                        val f2 = f * 1.5
                        add(out, t + beat / 2, 0.5) { x -> v * 0.2 * (1..4).sumOf { k -> sin(2 * PI * f2 * k * x) / (k * k) } * env(x, 8) }
                        events += t + beat / 2
                    }
                    onsets += t; events += t
                }
            }
        }
        return Song(out, onsets, events)
    }

    private fun noise(kind: String, seed: Int): FloatArray {
        val out = FloatArray((seconds * sr).toInt())
        val r = Random(seed)
        when (kind) {
            "room" -> { // pink noise (Paul Kellet)
                var b0 = 0.0; var b1 = 0.0; var b2 = 0.0
                for (i in out.indices) {
                    val w = r.nextDouble(-1.0, 1.0)
                    b0 = 0.99765 * b0 + w * 0.0990460; b1 = 0.96300 * b1 + w * 0.2965164; b2 = 0.57000 * b2 + w * 1.0526913
                    out[i] = (b0 + b1 + b2 + w * 0.1848).toFloat()
                }
            }
            "traffic" -> { // brown rumble that swells as cars pass
                var y = 0.0
                for (i in out.indices) {
                    y = 0.995 * y + r.nextDouble(-1.0, 1.0) * 0.1
                    val t = i.toDouble() / sr
                    out[i] = (y * (0.6 + 0.4 * sin(2 * PI * 0.13 * t) * sin(2 * PI * 0.07 * t + 1))).toFloat()
                }
            }
            "chatter" -> { // 6 talkers: band-limited noise in syllables and phrases
                repeat(6) { talker ->
                    val tr = Random(seed * 10 + talker)
                    var lp = 0.0; var hpPrev = 0.0; var hpOut = 0.0
                    var gain = 0.0
                    var target = 0.0
                    var next = 0
                    for (i in out.indices) {
                        if (i >= next) {
                            val speaking = tr.nextDouble() < 0.8
                            target = if (speaking) tr.nextDouble(0.4, 1.0) else 0.0
                            next = i + (sr * if (speaking) tr.nextDouble(0.08, 0.3) else tr.nextDouble(0.05, 0.6)).toInt()
                        }
                        gain += (target - gain) * 0.002
                        val w = tr.nextDouble(-1.0, 1.0)
                        lp += (w - lp) * 0.35
                        hpOut = 0.97 * (hpOut + lp - hpPrev); hpPrev = lp
                        out[i] += (hpOut * gain).toFloat()
                    }
                }
            }
            "clatter" -> { // dishes, footsteps, doors: random sharp transients plus a little room tone
                for (i in out.indices) out[i] = (r.nextDouble(-1.0, 1.0) * 0.05).toFloat()
                var t = 0.0
                while (t < seconds) {
                    t += -ln(1 - r.nextDouble()) / 3.0
                    val a = r.nextDouble(0.2, 1.0)
                    val ring = r.nextDouble(1500.0, 5000.0)
                    val metallic = r.nextBoolean()
                    add(out, t, 0.15) { x -> a * (r.nextDouble(-1.0, 1.0) * env(x, 60) + if (metallic) 0.6 * sin(2 * PI * ring * x) * env(x, 25) else 0.0) }
                }
            }
        }
        return out
    }

    private fun ln(x: Double) = kotlin.math.ln(x)
    private fun rms(a: FloatArray) = sqrt(a.fold(0.0) { s, v -> s + v * v } / a.size)

    data class Result(
        val correct: Double, val octave: Double, val wrong: Double, val blank: Double,
        val firstCorrectS: Double?, val matchedAtS: Double?, val falseMatch: Boolean,
        val hitPrecision: Double, val hitRecall: Double, val hitsPerS: Double,
    )

    /**
     * Small speakers and phone mics lose deep bass. `CHAIN_HZ=150` runs the audio through two 2nd-order
     * high-pass filters (speaker, then mic) at that frequency, like playing it to a phone.
     */
    private val chainHz = System.getenv("CHAIN_HZ")?.toDouble()

    private fun highPass(x: FloatArray, hz: Double): FloatArray {
        val w = 2 * PI * hz / sr
        val alpha = sin(w) / (2 * 0.7071)
        val cosw = kotlin.math.cos(w)
        val a0 = 1 + alpha
        val b0 = (1 + cosw) / 2 / a0; val b1 = -(1 + cosw) / a0; val b2 = b0
        val a1 = -2 * cosw / a0; val a2 = (1 - alpha) / a0
        val y = FloatArray(x.size)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in x.indices) {
            val v = b0 * x[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x[i].toDouble(); y2 = y1; y1 = v
            y[i] = v.toFloat()
        }
        return y
    }

    private fun run(input: FloatArray, trueBpm: Double?, onsets: List<Double>, events: List<Double> = onsets): Result {
        val signal = chainHz?.let { highPass(highPass(input, it), it) } ?: input
        // A typical mic level for music in a room: about -26 dBFS.
        val scale = 0.05 / rms(signal)
        val pcm = ShortArray(signal.size) { ((signal[it] * scale).coerceIn(-1.0, 1.0) * 32767).toInt().toShort() }
        val updates = mutableListOf<BeatUpdate>()
        val hits = mutableListOf<Long>()
        val engine = BeatEngine(sr, detectorFactory = if (System.getenv("NOISE_BENCH_DETECTOR") == "onset") { r -> OnsetBpmDetector(sampleRate = r) } else { _ -> BeatNetDetector() }, listener = object : BeatEngine.Listener {
            override fun onBeat(update: BeatUpdate) { updates += update }
            override fun onHit(atMs: Long, strength: Float) { hits += atMs }
        })
        val chunk = sr / 40
        var t = 0L
        var i = 0
        while (i < pcm.size) {
            val n = minOf(chunk, pcm.size - i)
            t += 25
            engine.feed(pcm.copyOfRange(i, i + n), n, t)
            i += n
        }
        // Mirror the service: what the screen shows, and what the judge decides.
        val judge = SongJudge()
        val anyJudge = SongJudge()
        var lastConfident = Long.MIN_VALUE / 2
        var correct = 0; var octave = 0; var wrong = 0; var blank = 0
        var firstCorrect: Double? = null
        var matchedAt: Double? = null
        var falseMatch = false
        for (u in updates) {
            val confident = u.confidence >= BeatEngine.MIN_CONFIDENCE
            if (confident) lastConfident = u.timeMs
            val shown = confident || u.timeMs - lastConfident < 3_000
            val c = if (u.settled) u.confidence else 0f
            if (trueBpm != null) {
                val v = judge.onBeat(u.bpm, c, u.timeMs, true) { abs(it - trueBpm) <= 3 }
                if (v.matched && matchedAt == null) matchedAt = u.timeMs / 1000.0
                // Anything held steady for 10 s at a wrong, non-octave tempo would be a false alert for that range.
                val w = anyJudge.onBeat(u.bpm, c, u.timeMs, true) { b ->
                    listOf(1.0, 2.0, 0.5).none { abs(b - trueBpm * it) <= 3 }
                }
                if (w.matched) falseMatch = true
            } else {
                val v = anyJudge.onBeat(u.bpm, c, u.timeMs, true) { true }
                if (v.matched) falseMatch = true
            }
            when {
                !shown -> blank++
                trueBpm == null -> wrong++
                abs(u.bpm - trueBpm) <= 2 -> { correct++; if (firstCorrect == null && u.settled) firstCorrect = u.timeMs / 1000.0 }
                abs(u.bpm - trueBpm * 2) <= 3 || abs(u.bpm - trueBpm / 2) <= 2 -> octave++
                else -> wrong++
            }
        }
        val total = maxOf(1, updates.size).toDouble()
        // Hits: a hit counts if it lands within 0-100 ms after a musical onset (25 ms reads + analysis delay).
        val onsetMs = onsets.map { (it * 1000).roundToInt() }
        val eventMs = events.map { (it * 1000).roundToInt() }
        val good = hits.count { h -> eventMs.any { h - it in 0..100 } }
        val found = onsetMs.count { o -> o > 300 && hits.any { it - o in 0..100 } }
        return Result(
            correct / total, octave / total, wrong / total, blank / total, firstCorrect, matchedAt, falseMatch,
            if (hits.isEmpty()) 1.0 else good.toDouble() / hits.size,
            if (onsetMs.isEmpty()) 0.0 else found.toDouble() / onsetMs.count { it > 300 },
            hits.size / seconds,
        )
    }

    @Test fun bench() {
        org.junit.Assume.assumeTrue(System.getenv("NOISE_BENCH") != null)
        val styles = listOf("edm", "rock", "acoustic")
        val tempos = listOf(85.0, 100.0, 120.0, 128.0, 140.0, 174.0)
        val noises = listOf("room", "traffic", "chatter", "clatter")
        val snrs = listOf(99, 10, 5, 0, -5, -10)
        val noiseCache = noises.associateWith { noise(it, 7) }
        data class Cfg(val style: String, val bpm: Double, val noise: String, val snr: Int)
        val cfgs = styles.flatMap { s -> tempos.flatMap { b -> noises.flatMap { n -> snrs.filter { it != 99 || n == "room" }.map { Cfg(s, b, n, it) } } } }
        val songs = java.util.concurrent.ConcurrentHashMap<String, Song>()
        val results = cfgs.parallelStream().map { c ->
            val song = songs.getOrPut("${c.style}${c.bpm}") { song(c.style, c.bpm, c.bpm.toInt()) }
            val nz = noiseCache.getValue(c.noise)
            val k = if (c.snr == 99) 0.0 else rms(song.pcm) / rms(nz) / 10.0.pow(c.snr / 20.0)
            val mix = FloatArray(song.pcm.size) { (song.pcm[it] + k * nz[it]).toFloat() }
            c to run(mix, c.bpm, song.onsets, song.events)
        }.toList()

        fun pct(x: Double) = "%3.0f%%".format(x * 100)
        fun table(title: String, key: (Cfg) -> String, order: List<String>) {
            println("\n== $title")
            println("%-18s %7s %7s %7s %7s %9s %8s %6s %7s %7s".format("", "right", "½/2×", "wrong", "blank", "1st ok s", "matched", "false", "hitPrec", "hitRec"))
            for (g in order) {
                val rs = results.filter { key(it.first) == g }.map { it.second }
                if (rs.isEmpty()) continue
                val firsts = rs.mapNotNull { it.firstCorrectS }.sorted()
                println("%-18s %7s %7s %7s %7s %9s %8s %6s %7s %7s".format(
                    g, pct(rs.map { it.correct }.average()), pct(rs.map { it.octave }.average()), pct(rs.map { it.wrong }.average()),
                    pct(rs.map { it.blank }.average()),
                    if (firsts.isEmpty()) "-" else "%.1f".format(firsts[firsts.size / 2]),
                    pct(rs.count { it.matchedAtS != null }.toDouble() / rs.size), rs.count { it.falseMatch },
                    pct(rs.map { it.hitPrecision }.average()), pct(rs.map { it.hitRecall }.average()),
                ))
            }
        }
        val snrName = { s: Int -> if (s == 99) "clean" else "%+d dB".format(s) }
        fun right(sel: (Cfg) -> Boolean) = results.filter { sel(it.first) }.map { it.second.correct }.average()
        fun wrongOf(sel: (Cfg) -> Boolean) = results.filter { sel(it.first) }.map { it.second.wrong }.average()
        val noiseShown = noises.flatMap { n -> (1..3).map { run(noise(n, it * 13), null, emptyList()) } }
        println("SUMMARY right clean=%s +10=%s +5=%s 0=%s -5=%s | right+octave 0dB=%s | wrong 0dB=%s | clatter0 right=%s wrong=%s | noise-only shown max=%s falseAlerts=%d".format(
            pct(right { it.snr == 99 }), pct(right { it.snr == 10 }), pct(right { it.snr == 5 }), pct(right { it.snr == 0 }), pct(right { it.snr == -5 }),
            pct(results.filter { it.first.snr == 0 }.map { it.second.correct + it.second.octave }.average()),
            pct(wrongOf { it.snr == 0 }), pct(right { it.snr == 0 && it.noise == "clatter" }), pct(wrongOf { it.snr == 0 && it.noise == "clatter" }),
            pct(noiseShown.maxOf { it.wrong }), noiseShown.count { it.falseMatch }))
        table("By noise level (all songs, all noise types)", { snrName(it.snr) }, snrs.map(snrName))
        for (n in noises) table("$n noise", { if (it.noise == n) snrName(it.snr) else "" }, snrs.map(snrName))
        for (s in styles) table("$s songs", { if (it.style == s) snrName(it.snr) else "" }, snrs.map(snrName))
        table("By tempo at 0 dB", { if (it.snr == 0) "${it.bpm.toInt()} BPM" else "" }, tempos.map { "${it.toInt()} BPM" })

        println("\n== Clean, per song (median settled reading)")
        for (st in styles) println("%-9s".format(st) + tempos.joinToString(" ") { b ->
            val song = songs.getValue("$st$b")
            val ups = mutableListOf<Float>()
            val e = BeatEngine(sr, object : BeatEngine.Listener { override fun onBeat(update: BeatUpdate) { if (update.settled) ups += update.bpm } })
            val gain = 0.05 / rms(song.pcm)
            val pcm = TestSignals.toPcm16(FloatArray(song.pcm.size) { (song.pcm[it] * gain).toFloat() })
            var i = 0; var t = 0L
            while (i < pcm.size) { val n = minOf(sr / 40, pcm.size - i); t += 25; e.feed(pcm.copyOfRange(i, i + n), n, t); i += n }
            "${b.toInt()}->%.0f".format(ups.sorted()[ups.size / 2])
        })
        println("\n== Noise only, no music (30 s each)")
        for (n in noises) for (seed in 1..3) {
            val r = run(noise(n, seed * 13), null, emptyList())
            println("%-8s seed %d: shows a BPM %s of the time, false alert %s, %.1f hits/s".format(n, seed, pct(r.wrong), r.falseMatch, r.hitsPerS))
        }
    }

    /** Writes the phone-test clips: `BENCH_OUT=<dir>` to run. */
    @Test fun exportClips() {
        val dir = System.getenv("BENCH_OUT")
        org.junit.Assume.assumeTrue(dir != null)
        java.io.File(dir).mkdirs()
        val plan = listOf("rock" to 120.0, "edm" to 128.0, "acoustic" to 100.0)
        for ((style, bpm) in plan) {
            val song = song(style, bpm, bpm.toInt())
            val clips = mutableListOf("clean" to song.pcm)
            for (n in listOf("room", "traffic", "chatter", "clatter")) for (snr in listOf(10, 5, 0)) {
                val nz = noise(n, 7)
                val k = rms(song.pcm) / rms(nz) / 10.0.pow(snr / 20.0)
                clips += "$n%+d".format(snr) to FloatArray(song.pcm.size) { (song.pcm[it] + k * nz[it]).toFloat() }
            }
            for ((name, mix) in clips) {
                val g = 0.12 / rms(mix)
                val peak = mix.maxOf { abs(it) } * g
                val gain = if (peak > 0.95) g * 0.95 / peak else g
                writeWav(java.io.File(dir, "${style}_${bpm.toInt()}_$name.wav"), FloatArray(mix.size) { (mix[it] * gain).toFloat() })
            }
        }
    }

    private fun writeWav(f: java.io.File, x: FloatArray) {
        val data = java.nio.ByteBuffer.allocate(44 + x.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray()).putInt(36 + x.size * 2).put("WAVE".toByteArray())
        data.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(sr).putInt(sr * 2).putShort(2).putShort(16)
        data.put("data".toByteArray()).putInt(x.size * 2)
        for (v in x) data.putShort((v.coerceIn(-1f, 1f) * 32767).toInt().toShort())
        f.writeBytes(data.array())
    }
}
