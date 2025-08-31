package soia.internal

import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer

fun encodeInt32(
    input: Int,
    buffer: Buffer,
) {
    when {
        input < 0 -> {
            when {
                input >= -256 -> {
                    buffer.writeByte(235)
                    buffer.writeByte((input + 256))
                }
                input >= -65536 -> {
                    buffer.writeByte(236)
                    buffer.writeShortLe((input + 65536))
                }
                else -> {
                    buffer.writeByte(237)
                    buffer.writeIntLe(input)
                }
            }
        }
        input < 232 -> {
            buffer.writeByte(input)
        }
        input < 65536 -> {
            buffer.writeByte(232)
            buffer.writeShortLe(input)
        }
        else -> {
            buffer.writeByte(233)
            buffer.writeIntLe(input)
        }
    }
}

fun encodeLengthPrefix(
    length: Int,
    buffer: Buffer,
) {
    when {
        length < 232 -> {
            if (length >= 0) {
                buffer.writeByte(length)
            } else {
                throw IllegalArgumentException("Length overflow: ${length.toUInt()}")
            }
        }
        length < 65536 -> {
            buffer.writeByte(232)
            buffer.writeShortLe(length)
        }
        else -> {
            buffer.writeByte(233)
            buffer.writeIntLe(length)
        }
    }
}

fun decodeNumber(buffer: BufferedSource): Number {
    return when (val wire = buffer.readByte().toInt() and 0xFF) {
        in 0..231 -> wire
        232 -> buffer.readShortLe().toInt() and 0xFFFF // uint16
        233 -> buffer.readIntLe().toLong() and 0xFFFFFFFF // uint32
        234 -> buffer.readLongLe() // uint64
        235 -> (buffer.readByte().toInt() and 0xFF) - 256L
        236 -> (buffer.readShortLe().toInt() and 0xFFFF) - 65536L
        237 -> buffer.readIntLe()
        238 -> buffer.readLongLe()
        239 -> buffer.readLongLe()
        240 -> Float.fromBits(buffer.readIntLe())
        241 -> Double.fromBits(buffer.readLongLe())
        else -> throw IllegalArgumentException("Expected: number; wire: $wire")
    }
}

fun decodeUnused(buffer: BufferedSource) {
    val wire = buffer.readByte().toInt() and 0xFF
    if (wire < 232) {
        return
    }

    when (wire - 232) {
        0, 4 -> { // uint16, uint16 - 65536
            buffer.skip(2)
        }
        1, 5, 8 -> { // uint32, int32, float32
            buffer.skip(4)
        }
        2, 6, 7, 9 -> { // uint64, int64, uint64 timestamp, float64
            buffer.skip(8)
        }
        3 -> { // uint8 - 256
            buffer.skip(1)
        }
        11, 13 -> { // string, bytes
            val length = decodeNumber(buffer)
            buffer.skip(length.toLong())
        }
        15, 19, 20, 21, 22 -> { // array length==1, enum value kind==1-4
            decodeUnused(buffer)
        }
        16 -> { // array length==2
            decodeUnused(buffer)
            decodeUnused(buffer)
        }
        17 -> { // array length==3
            decodeUnused(buffer)
            decodeUnused(buffer)
            decodeUnused(buffer)
        }
        18 -> { // array length==N
            val length = decodeNumber(buffer)
            repeat(length.toInt()) {
                decodeUnused(buffer)
            }
        }
    }
}

class CountingSource(delegate: Source) : ForwardingSource(delegate) {
    var bytesRead = 0L
        private set

    override fun read(
        sink: Buffer,
        byteCount: Long,
    ): Long {
        val result = super.read(sink, byteCount)
        if (result != -1L) {
            bytesRead += result
        }
        return result
    }

    val buffer = buffer()
}
