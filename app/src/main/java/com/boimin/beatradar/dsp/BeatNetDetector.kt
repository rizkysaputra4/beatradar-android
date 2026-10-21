package com.boimin.beatradar.dsp

import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln1p
import kotlin.math.roundToInt

/**
 * Tempo and beats from a small trained network (a causal TCN, ~97k weights, `beatnet.bin`), trained on ~1 700
 * hand-annotated songs played through a simulated phone. Same framing as [OnsetBpmDetector] (512 / 128 at 11 025 Hz),
 * then 40 log bands; the network runs one frame at a time and looks back ~12 s.
 *
 * It answers two things: per frame, how likely a beat is right now; and, from its state averaged over the last 4 s, a
 * vote over tempos 40–250 BPM (or "no beat"). The reading is the vote's peak; its confidence is how much of the vote sits
 * on that tempo (±2 BPM), so 0.8 really means "80% sure". Beat timing comes from lining a grid at that tempo up with
 * the beat activations.
 *
 * Mirrors `beatnet.py` (features, layers) exactly; a test checks it against the PyTorch model.
 */
class BeatNetDetector(private val net: Weights = Weights.bundled()) : BpmDetector {

    /** The network's weights, as exported by `export.py`. */
    class Weights(
        val bands: Int,
        val channels: Int,
        val kernel: Int,
        val dilations: IntArray,
        val tempoClasses: Int,
        val bpmMin: Int,
        /** Frames the tempo vote averages the network's state over. */
        val pool: Int,
        val tempoHidden: Int,
        val edges: IntArray,
        val inW: FloatArray, val inB: FloatArray,
        val convW: Array<FloatArray>, val convB: Array<FloatArray>,
        val mixW: Array<FloatArray>, val mixB: Array<FloatArray>,
        val beatW: FloatArray, val beatB: Float,
        val hidW: FloatArray, val hidB: FloatArray,
        val tempoW: FloatArray, val tempoB: FloatArray,
    ) {
        companion object {
            private var cached: Weights? = null

            /** The model shipped with the app (a Java resource, so unit tests read it too). */
            @Synchronized
            fun bundled(): Weights = cached ?: read(
                BeatNetDetector::class.java.getResourceAsStream("/beatnet.bin") ?: error("beatnet.bin missing"),
            ).also { cached = it }

            fun read(input: InputStream): Weights {
                val bytes = input.use { it.readBytes() }
                val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val magic = ByteArray(4).also { b.get(it) }
                check(String(magic) == "BRN1") { "not a BeatNet model" }
                val bands = b.int
                val channels = b.int
                val kernel = b.int
                val blocks = b.int
                val dilations = IntArray(blocks) { b.int }
                val tempoClasses = b.int
                val bpmMin = b.int
                val pool = b.int
                val tempoHidden = b.int
                val edges = IntArray(bands + 1) { b.int }
                fun floats(n: Int) = FloatArray(n) { b.float }
                val inW = floats(channels * 2 * bands)
                val inB = floats(channels)
                val convW = Array(blocks) { FloatArray(0) }
                val convB = Array(blocks) { FloatArray(0) }
                val mixW = Array(blocks) { FloatArray(0) }
                val mixB = Array(blocks) { FloatArray(0) }
                for (i in 0 until blocks) {
                    convW[i] = floats(channels * channels * kernel)
                    convB[i] = floats(channels)
                    mixW[i] = floats(channels * channels)
                    mixB[i] = floats(channels)
                }
                val beatW = floats(channels)
                val beatB = b.float
                val hidW = floats(tempoHidden * channels)
                val hidB = floats(tempoHidden)
                val tempoW = floats(tempoClasses * tempoHidden)
                val tempoB = floats(tempoClasses)
                check(!b.hasRemaining()) { "unexpected bytes at the end of the model" }
                return Weights(
                    bands, channels, kernel, dilations, tempoClasses, bpmMin, pool, tempoHidden, edges,
                    inW, inB, convW, convB, mixW, mixB, beatW, beatB, hidW, hidB, tempoW, tempoB,
                )
            }
        }
    }

    private val c = net.channels
    private val k = net.kernel
    private val fft = Fft(FRAME)
    private val window = DoubleArray(FRAME) { 0.5 - 0.5 * cos(2 * PI * it / FRAME) }
    private val frame = FloatArray(FRAME)
    private var frameFill = 0
    private val re = DoubleArray(FRAME)
    private val im = DoubleArray(FRAME)
    private val mag = DoubleArray(FRAME / 2 + 1)

    private val feat = FloatArray(net.bands)
    private val prevFeat = FloatArray(net.bands)
    private var haveFeat = false
    private val input = FloatArray(2 * net.bands)

    /** Per block, the last (k - 1) · d + 1 inputs, newest at [pos]. */
    private val history = Array(net.dilations.size) { i -> Array((k - 1) * net.dilations[i] + 1) { FloatArray(c) } }
    private val pos = IntArray(net.dilations.size)
    private val h = FloatArray(c)
    private val y = FloatArray(c)

    /** The network's state over the last [Weights.pool] frames and its running sum (the tempo vote's input). */
    private val states = Array(net.pool) { FloatArray(c) }
    private val stateSum = DoubleArray(c)
    private var stateCount = 0
    private var statePos = 0
    private val hidden = FloatArray(net.tempoHidden)
    private val vote = DoubleArray(net.tempoClasses)

    /** Beat activations (0..1) of the last [ACT_FRAMES] frames, oldest first when unrolled from [actPos]. */
    private val act = FloatArray(ACT_FRAMES)
    private var actPos = 0
    private var frames = 0
    private var framesSinceEstimate = 0
    private var onsetPeak = 0.0

    override fun reset() {
        frameFill = 0
        haveFeat = false
        for (hist in history) for (row in hist) row.fill(0f)
        pos.fill(0)
        for (v in states) v.fill(0f)
        stateSum.fill(0.0)
        stateCount = 0
        statePos = 0
        act.fill(0f)
        actPos = 0
        frames = 0
        framesSinceEstimate = 0
        onsetPeak = 0.0
    }

    override fun takeOnsetPeak(): Double = onsetPeak.also { onsetPeak = 0.0 }

    /** The network decides the octave itself, and better as it hears more: an octave change is a correction. */
    override val foldsOctaves: Boolean get() = false

    override fun process(samples: FloatArray, count: Int): BpmEstimate? {
        var result: BpmEstimate? = null
        var samplesAfterResult = 0
        var i = 0
        while (i < count) {
            val take = minOf(FRAME - frameFill, count - i)
            System.arraycopy(samples, i, frame, frameFill, take)
            frameFill += take
            i += take
            if (result != null) samplesAfterResult += take
            if (frameFill == FRAME) {
                step()
                System.arraycopy(frame, HOP, frame, 0, FRAME - HOP)
                frameFill = FRAME - HOP
                if (frames >= MIN_FRAMES && ++framesSinceEstimate >= ESTIMATE_EVERY) {
                    framesSinceEstimate = 0
                    estimate()?.let {
                        result = it
                        samplesAfterResult = 0
                    }
                }
            }
        }
        return result?.let { it.copy(secondsSinceBeat = it.secondsSinceBeat + (samplesAfterResult / RATE).toFloat()) }
    }

    /** One frame: features, the network, and the outputs into their buffers. */
    private fun step() {
        for (n in 0 until FRAME) {
            re[n] = frame[n] * window[n]
            im[n] = 0.0
        }
        fft.transform(re, im)
        for (n in 0..FRAME / 2) mag[n] = hypot(re[n], im[n])
        val bands = net.bands
        for (b in 0 until bands) {
            var s = 0.0
            for (n in net.edges[b] until net.edges[b + 1]) s += mag[n]
            feat[b] = ln1p(100.0 * s / (net.edges[b + 1] - net.edges[b])).toFloat()
        }
        if (!haveFeat) {
            feat.copyInto(prevFeat)
            haveFeat = true
        }
        var flux = 0.0
        for (b in 0 until bands) {
            input[b] = feat[b]
            val d = feat[b] - prevFeat[b]
            input[bands + b] = if (d > 0) d else 0f
            flux += input[bands + b]
        }
        feat.copyInto(prevFeat)
        if (flux > onsetPeak) onsetPeak = flux

        // Input layer: 1x1 conv + ELU.
        for (o in 0 until c) {
            var s = net.inB[o]
            val base = o * 2 * bands
            for (j in 0 until 2 * bands) s += net.inW[base + j] * input[j]
            h[o] = elu(s)
        }
        // Residual blocks: causal dilated conv + ELU, 1x1 mix, add.
        for (blk in net.dilations.indices) {
            val d = net.dilations[blk]
            val hist = history[blk]
            val len = hist.size
            pos[blk] = (pos[blk] + 1) % len
            h.copyInto(hist[pos[blk]])
            val w = net.convW[blk]
            val bias = net.convB[blk]
            for (o in 0 until c) {
                var s = bias[o]
                val ob = o * c * k
                for (tap in 0 until k) {
                    // PyTorch: output t uses inputs t - (k - 1 - tap) · d.
                    val row = hist[(pos[blk] - (k - 1 - tap) * d + len * k) % len]
                    var idx = ob + tap
                    for (j in 0 until c) {
                        s += w[idx] * row[j]
                        idx += k
                    }
                }
                y[o] = elu(s)
            }
            val mw = net.mixW[blk]
            val mb = net.mixB[blk]
            for (o in 0 until c) {
                var s = mb[o]
                val ob = o * c
                for (j in 0 until c) s += mw[ob + j] * y[j]
                h[o] += s
            }
        }
        // Heads.
        var beat = net.beatB
        for (j in 0 until c) beat += net.beatW[j] * h[j]
        act[actPos] = sigmoid(beat)
        actPos = (actPos + 1) % ACT_FRAMES

        val slot = states[statePos]
        for (j in 0 until c) {
            stateSum[j] += h[j] - slot[j]
            slot[j] = h[j]
        }
        statePos = (statePos + 1) % net.pool
        if (stateCount < net.pool) stateCount++
        frames++
    }

    /** The tempo vote from the state averaged over the last [Weights.pool] frames, into [vote] (sums to 1). */
    private fun computeVote() {
        val n = stateCount.coerceAtLeast(1)
        for (o in 0 until net.tempoHidden) {
            var s = net.hidB[o].toDouble()
            val ob = o * c
            for (j in 0 until c) s += net.hidW[ob + j] * (stateSum[j] / n)
            hidden[o] = elu(s.toFloat())
        }
        var max = Double.NEGATIVE_INFINITY
        for (t in 0 until net.tempoClasses) {
            var s = net.tempoB[t].toDouble()
            val ob = t * net.tempoHidden
            for (j in 0 until net.tempoHidden) s += net.tempoW[ob + j] * hidden[j]
            vote[t] = s
            if (s > max) max = s
        }
        var z = 0.0
        for (t in 0 until net.tempoClasses) {
            vote[t] = exp(vote[t] - max)
            z += vote[t]
        }
        for (t in 0 until net.tempoClasses) vote[t] /= z
    }

    private fun estimate(): BpmEstimate? {
        if (stateCount == 0) return null
        computeVote()
        // Best tempo (class 0 is "no beat"), refined between neighbouring classes.
        var best = 1
        for (t in 2 until net.tempoClasses) if (vote[t] > vote[best]) best = t
        var bpm = (net.bpmMin + best - 1).toDouble()
        if (best in 2 until net.tempoClasses - 1) {
            val a = vote[best - 1]
            val b = vote[best]
            val cc = vote[best + 1]
            val den = a - 2 * b + cc
            if (den < 0) bpm += 0.5 * (a - cc) / den
        }
        var mass = 0.0
        for (t in maxOf(1, best - 2)..minOf(net.tempoClasses - 1, best + 2)) mass += vote[t]
        val confidence = mass.toFloat().coerceIn(0f, 1f)
        val tau = 60.0 * FPS / bpm
        return BpmEstimate(bpm.toFloat(), confidence, secondsSinceLastBeat(tau), settled = frames >= SETTLE_FRAMES)
    }

    /** Lines a grid of period [tau] frames up with the last beat activations; how long ago its latest beat was. */
    private fun secondsSinceLastBeat(tau: Double): Float {
        val avail = minOf(frames, ACT_FRAMES)
        fun ago(i: Int): Float = act[((actPos - 1 - i) % ACT_FRAMES + ACT_FRAMES) % ACT_FRAMES]
        val beats = minOf(8, floor(avail / tau).toInt())
        var bestOffset = 0
        var bestSum = -1.0
        for (offset in 0 until tau.roundToInt().coerceAtLeast(1)) {
            var sum = 0.0
            for (b in 0 until beats) {
                val idx = offset + (b * tau).roundToInt()
                if (idx >= avail) break
                sum += ago(idx)
            }
            if (sum > bestSum) { bestSum = sum; bestOffset = offset }
        }
        // Frame t's activation is centred mid-frame.
        val samplesAgo = bestOffset * HOP + FRAME / 2
        return (samplesAgo / RATE).toFloat()
    }

    private fun elu(x: Float): Float = if (x > 0) x else (exp(x.toDouble()) - 1).toFloat()
    private fun sigmoid(x: Float): Float = (1 / (1 + exp(-x.toDouble()))).toFloat()

    internal fun framesSeen(): Int = frames

    internal fun lastActivation(): Float = act[(actPos - 1 + ACT_FRAMES) % ACT_FRAMES]

    internal fun currentVotes(): DoubleArray = vote.also { computeVote() }.copyOf()

    companion object {
        const val RATE = 11_025.0
        const val FRAME = 512
        const val HOP = 128
        const val FPS = RATE / HOP
        const val ACT_FRAMES = 688
        /** A first reading after 1.5 s, settled ("Locked") after 3 s: on held-out songs that is as accurate later on as 4 s. */
        const val MIN_FRAMES = 129
        const val SETTLE_FRAMES = 258
        const val ESTIMATE_EVERY = 43
    }
}
