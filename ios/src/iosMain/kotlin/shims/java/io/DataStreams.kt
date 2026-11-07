package java.io

/* Ersatz fuer DataInputStream und DataOutputStream: Zahlen big-endian, Texte in modifiziertem UTF-8 wie im JDK. */

open class UTFDataFormatException(message: String? = null) : IOException(message)

open class DataInputStream(private val quelle: InputStream) : InputStream() {

    override fun read(): Int = quelle.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = quelle.read(b, off, len)
    override fun available(): Int = quelle.available()
    override fun close() = quelle.close()

    fun readFully(b: ByteArray) = readFully(b, 0, b.size)

    fun readFully(b: ByteArray, off: Int, len: Int) {
        var n = 0
        while (n < len) {
            val r = quelle.read(b, off + n, len - n)
            if (r < 0) throw EOFException()
            n += r
        }
    }

    private fun u8(): Int {
        val c = quelle.read()
        if (c < 0) throw EOFException()
        return c
    }

    fun readBoolean(): Boolean = u8() != 0
    fun readByte(): Byte = u8().toByte()
    fun readUnsignedByte(): Int = u8()
    fun readUnsignedShort(): Int {
        val a = u8()
        val b = u8()
        return (a shl 8) or b
    }
    fun readShort(): Short = readUnsignedShort().toShort()
    fun readChar(): Char = readUnsignedShort().toChar()
    fun readInt(): Int {
        val a = u8()
        val b = u8()
        val c = u8()
        val d = u8()
        return (a shl 24) or (b shl 16) or (c shl 8) or d
    }
    fun readLong(): Long {
        val hoch = readInt().toLong()
        val tief = readInt().toLong() and 0xFFFFFFFFL
        return (hoch shl 32) or tief
    }
    fun readFloat(): Float = Float.fromBits(readInt())
    fun readDouble(): Double = Double.fromBits(readLong())

    fun readUTF(): String {
        val laenge = readUnsignedShort()
        val b = ByteArray(laenge)
        readFully(b)
        val sb = StringBuilder(laenge)
        var i = 0
        while (i < laenge) {
            val c = b[i].toInt() and 0xFF
            when (c shr 4) {
                in 0..7 -> {
                    sb.append(c.toChar())
                    i++
                }
                12, 13 -> {
                    if (i + 1 >= laenge) throw UTFDataFormatException("malformed input: partial character at end")
                    val c2 = b[i + 1].toInt() and 0xFF
                    if (c2 and 0xC0 != 0x80) throw UTFDataFormatException("malformed input around byte $i")
                    sb.append((((c and 0x1F) shl 6) or (c2 and 0x3F)).toChar())
                    i += 2
                }
                14 -> {
                    if (i + 2 >= laenge) throw UTFDataFormatException("malformed input: partial character at end")
                    val c2 = b[i + 1].toInt() and 0xFF
                    val c3 = b[i + 2].toInt() and 0xFF
                    if (c2 and 0xC0 != 0x80 || c3 and 0xC0 != 0x80) throw UTFDataFormatException("malformed input around byte $i")
                    sb.append((((c and 0x0F) shl 12) or ((c2 and 0x3F) shl 6) or (c3 and 0x3F)).toChar())
                    i += 3
                }
                else -> throw UTFDataFormatException("malformed input around byte $i")
            }
        }
        return sb.toString()
    }
}

open class DataOutputStream(private val ziel: OutputStream) : OutputStream() {
    private var geschrieben = 0

    override fun write(b: Int) {
        ziel.write(b)
        geschrieben++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        ziel.write(b, off, len)
        geschrieben += len
    }

    override fun flush() = ziel.flush()

    override fun close() {
        try {
            flush()
        } finally {
            ziel.close()
        }
    }

    fun size(): Int = geschrieben

    fun writeBoolean(v: Boolean) = write(if (v) 1 else 0)
    fun writeByte(v: Int) = write(v and 0xFF)
    fun writeShort(v: Int) = write(byteArrayOf((v ushr 8).toByte(), v.toByte()), 0, 2)
    fun writeChar(v: Int) = writeShort(v)
    fun writeInt(v: Int) = write(byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte()), 0, 4)
    fun writeLong(v: Long) {
        writeInt((v ushr 32).toInt())
        writeInt(v.toInt())
    }
    fun writeFloat(v: Float) = writeInt(v.toRawBits())
    fun writeDouble(v: Double) = writeLong(v.toRawBits())
    fun writeBytes(s: String) {
        for (ch in s) write(ch.code and 0xFF)
    }

    fun writeUTF(s: String) {
        var n = 0
        for (ch in s) {
            val c = ch.code
            n += if (c in 1..0x7F) 1 else if (c <= 0x7FF) 2 else 3
        }
        if (n > 65535) throw UTFDataFormatException("encoded string too long: $n bytes")
        val b = ByteArray(n + 2)
        b[0] = (n ushr 8).toByte()
        b[1] = n.toByte()
        var i = 2
        for (ch in s) {
            val c = ch.code
            if (c in 1..0x7F) {
                b[i++] = c.toByte()
            } else if (c <= 0x7FF) {
                b[i++] = (0xC0 or (c shr 6)).toByte()
                b[i++] = (0x80 or (c and 0x3F)).toByte()
            } else {
                b[i++] = (0xE0 or (c shr 12)).toByte()
                b[i++] = (0x80 or ((c shr 6) and 0x3F)).toByte()
                b[i++] = (0x80 or (c and 0x3F)).toByte()
            }
        }
        write(b, 0, b.size)
    }
}
