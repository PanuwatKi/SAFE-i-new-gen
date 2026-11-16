package com.panuwatki.safe.speech

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.OutputStream
import java.nio.ByteOrder
import kotlin.math.floor

/**
 * แปลงไฟล์เสียง (m4a / mp3 / amr / wav / ...) เป็น PCM 16-bit โมโน 16 kHz
 * เพื่อส่งให้ตัวถอดเสียง — ทำงานแบบสตรีม ไม่โหลดไฟล์ทั้งก้อนเข้าหน่วยความจำ
 */
object AudioDecoder {
    const val TARGET_RATE = 16_000
    private const val TIMEOUT_US = 10_000L

    /** @return จำนวน sample ที่เขียนออกไป */
    fun decodeToPcm16Mono(context: Context, uri: Uri, out: OutputStream, maxSeconds: Int = 20 * 60): Long {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("ไม่พบเสียงในไฟล์นี้")
            extractor.selectTrack(track)
            val inFormat = extractor.getTrackFormat(track)
            val mime = inFormat.getString(MediaFormat.KEY_MIME)!!

            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(inFormat, null, null, 0)
            c.start()

            var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var isFloat = false
            var resampler = LinearResampler(inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE), TARGET_RATE)
            val maxSamples = maxSeconds.toLong() * TARGET_RATE
            var written = 0L
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val emit: (Float) -> Unit = { s ->
                if (written < maxSamples) {
                    val v = (s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
                    out.write(v and 0xFF)
                    out.write((v shr 8) and 0xFF)
                    written++
                }
            }

            while (!outputDone && written < maxSamples) {
                if (!inputDone) {
                    val inIdx = c.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf = c.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            c.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = c.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIdx >= 0 -> {
                        val buf = c.getOutputBuffer(outIdx)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        buf.order(ByteOrder.LITTLE_ENDIAN)
                        val mono = toMono(buf, channels, isFloat)
                        resampler.process(mono, emit)
                        c.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = c.outputFormat
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        isFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        resampler = LinearResampler(f.getInteger(MediaFormat.KEY_SAMPLE_RATE), TARGET_RATE)
                    }
                }
            }
            out.flush()
            return written
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun toMono(buf: java.nio.ByteBuffer, channels: Int, isFloat: Boolean): FloatArray {
        val ch = channels.coerceAtLeast(1)
        return if (isFloat) {
            val fb = buf.asFloatBuffer()
            val frames = fb.remaining() / ch
            FloatArray(frames) { i ->
                var sum = 0f
                for (k in 0 until ch) sum += fb.get(i * ch + k)
                sum / ch
            }
        } else {
            val sb = buf.asShortBuffer()
            val frames = sb.remaining() / ch
            FloatArray(frames) { i ->
                var sum = 0f
                for (k in 0 until ch) sum += sb.get(i * ch + k) / 32768f
                sum / ch
            }
        }
    }
}

/** ปรับอัตราสุ่มเสียงแบบเส้นตรง ต่อเนื่องข้ามก้อนข้อมูลได้ */
class LinearResampler(srcRate: Int, dstRate: Int) {
    private val step = srcRate.toDouble() / dstRate
    private var pos = 0.0
    private var last = 0f

    fun process(input: FloatArray, emit: (Float) -> Unit) {
        if (input.isEmpty()) return
        while (true) {
            val i = floor(pos).toInt()
            val frac = (pos - i).toFloat()
            val a = if (i < 0) last else input.getOrNull(i) ?: break
            val b = input.getOrNull(i + 1) ?: break
            emit(a + (b - a) * frac)
            pos += step
        }
        pos -= input.size
        last = input.last()
    }
}
