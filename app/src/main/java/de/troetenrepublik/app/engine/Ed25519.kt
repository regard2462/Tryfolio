package de.troetenrepublik.app.engine

// Ed25519-Signaturen (RFC 8032) fuer Broker, die signierte Anfragen verlangen (Robinhood). Uebertragen aus TweetNaCl
// (gemeinfrei, https://tweetnacl.cr.yp.to): Zahlen mod 2^255-19 in 16 Gliedern zu 16 Bit. Ohne Bibliothek, weil Android
// Ed25519 erst ab Version 13 kennt. SHA-512 kommt von aussen (sha512), auf Android und Desktop aus der Java-Kryptografie.
// Geprueft gegen die Testvektoren aus RFC 8032 (Ed25519Test).
object Ed25519 {

    private fun gf(vararg w: Long) = LongArray(16).also { w.copyInto(it) }

    private val GF0 = LongArray(16)
    private val GF1 = gf(1)
    private val D2 = gf(0xf159, 0x26b2, 0x9b94, 0xebd6, 0xb156, 0x8283, 0x149a, 0x00e0, 0xd130, 0xeef3, 0x80f2, 0x198e, 0xfce7, 0x56df, 0xd9dc, 0x2406)
    private val X = gf(0xd51a, 0x8f25, 0x2d60, 0xc956, 0xa7b2, 0x9525, 0xc760, 0x692c, 0xdc5c, 0xfdd6, 0xe231, 0xc0a4, 0x53fe, 0xcd6e, 0x36d3, 0x2169)
    private val Y = gf(0x6658, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666, 0x6666)
    private val L = longArrayOf(
        0xed, 0xd3, 0xf5, 0x5c, 0x1a, 0x63, 0x12, 0x58, 0xd6, 0x9c, 0xf7, 0xa2, 0xde, 0xf9, 0xde, 0x14,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x10,
    )

    private fun car(o: LongArray) {
        for (i in 0 until 16) {
            o[i] += 1L shl 16
            val c = o[i] shr 16
            if (i < 15) o[i + 1] += c - 1 else o[0] += c - 1 + 37 * (c - 1)
            o[i] -= c shl 16
        }
    }

    private fun sel(p: LongArray, q: LongArray, b: Int) {
        val c = (b - 1).toLong().inv()
        for (i in 0 until 16) {
            val t = c and (p[i] xor q[i])
            p[i] = p[i] xor t
            q[i] = q[i] xor t
        }
    }

    private fun pack25519(o: ByteArray, off: Int, n: LongArray) {
        val m = LongArray(16)
        val t = n.copyOf()
        car(t); car(t); car(t)
        repeat(2) {
            m[0] = t[0] - 0xffed
            for (i in 1 until 15) {
                m[i] = t[i] - 0xffff - ((m[i - 1] shr 16) and 1)
                m[i - 1] = m[i - 1] and 0xffff
            }
            m[15] = t[15] - 0x7fff - ((m[14] shr 16) and 1)
            val b = ((m[15] shr 16) and 1).toInt()
            m[14] = m[14] and 0xffff
            sel(t, m, 1 - b)
        }
        for (i in 0 until 16) {
            o[off + 2 * i] = (t[i] and 0xff).toByte()
            o[off + 2 * i + 1] = (t[i] shr 8).toByte()
        }
    }

    private fun par(a: LongArray): Int = ByteArray(32).also { pack25519(it, 0, a) }[0].toInt() and 1

    private fun add(o: LongArray, a: LongArray, b: LongArray) { for (i in 0 until 16) o[i] = a[i] + b[i] }
    private fun sub(o: LongArray, a: LongArray, b: LongArray) { for (i in 0 until 16) o[i] = a[i] - b[i] }

    private fun mul(o: LongArray, a: LongArray, b: LongArray) {
        val t = LongArray(31)
        for (i in 0 until 16) for (j in 0 until 16) t[i + j] += a[i] * b[j]
        for (i in 0 until 15) t[i] += 38 * t[i + 16]
        for (i in 0 until 16) o[i] = t[i]
        car(o); car(o)
    }

    private fun inv(o: LongArray, i: LongArray) {
        val c = i.copyOf()
        for (a in 253 downTo 0) {
            mul(c, c, c)
            if (a != 2 && a != 4) mul(c, c, i)
        }
        c.copyInto(o)
    }

    private fun punktAdd(p: Array<LongArray>, q: Array<LongArray>) {
        val a = LongArray(16); val b = LongArray(16); val c = LongArray(16); val d = LongArray(16)
        val t = LongArray(16); val e = LongArray(16); val f = LongArray(16); val g = LongArray(16); val h = LongArray(16)
        sub(a, p[1], p[0]); sub(t, q[1], q[0]); mul(a, a, t)
        add(b, p[0], p[1]); add(t, q[0], q[1]); mul(b, b, t)
        mul(c, p[3], q[3]); mul(c, c, D2)
        mul(d, p[2], q[2]); add(d, d, d)
        sub(e, b, a); sub(f, d, c); add(g, d, c); add(h, b, a)
        mul(p[0], e, f); mul(p[1], h, g); mul(p[2], g, f); mul(p[3], e, h)
    }

    private fun cswap(p: Array<LongArray>, q: Array<LongArray>, b: Int) { for (i in 0 until 4) sel(p[i], q[i], b) }

    private fun pack(r: ByteArray, off: Int, p: Array<LongArray>) {
        val tx = LongArray(16); val ty = LongArray(16); val zi = LongArray(16)
        inv(zi, p[2]); mul(tx, p[0], zi); mul(ty, p[1], zi)
        pack25519(r, off, ty)
        r[off + 31] = (r[off + 31].toInt() xor (par(tx) shl 7)).toByte()
    }

    private fun scalarbase(s: ByteArray): Array<LongArray> {
        val p = arrayOf(GF0.copyOf(), GF1.copyOf(), GF1.copyOf(), GF0.copyOf())
        val q = arrayOf(X.copyOf(), Y.copyOf(), GF1.copyOf(), LongArray(16).also { mul(it, X, Y) })
        for (i in 255 downTo 0) {
            val b = (s[i / 8].toInt() shr (i and 7)) and 1
            cswap(p, q, b); punktAdd(q, p); punktAdd(p, p); cswap(p, q, b)
        }
        return p
    }

    private fun modL(r: ByteArray, off: Int, x: LongArray) {
        for (i in 63 downTo 32) {
            var carry = 0L
            var j = i - 32
            while (j < i - 12) {
                x[j] += carry - 16 * x[i] * L[j - (i - 32)]
                carry = (x[j] + 128) shr 8
                x[j] -= carry shl 8
                j++
            }
            x[j] += carry
            x[i] = 0
        }
        var carry = 0L
        for (j in 0 until 32) {
            x[j] += carry - (x[31] shr 4) * L[j]
            carry = x[j] shr 8
            x[j] = x[j] and 255
        }
        for (j in 0 until 32) x[j] -= carry * L[j]
        for (i in 0 until 32) {
            x[i + 1] += x[i] shr 8
            r[off + i] = (x[i] and 255).toByte()
        }
    }

    private fun reduce(r: ByteArray) {
        val x = LongArray(64) { (r[it].toInt() and 0xff).toLong() }
        r.fill(0)
        modL(r, 0, x)
    }

    private fun geklemmt(seed: ByteArray, sha512: (ByteArray) -> ByteArray): ByteArray =
        sha512(seed).also {
            it[0] = (it[0].toInt() and 248).toByte()
            it[31] = ((it[31].toInt() and 127) or 64).toByte()
        }

    // Oeffentlicher Schluessel (32 Byte) zum privaten Schluessel seed (32 Byte).
    fun oeffentlich(seed: ByteArray, sha512: (ByteArray) -> ByteArray): ByteArray {
        require(seed.size == 32) { "seed" }
        return ByteArray(32).also { pack(it, 0, scalarbase(geklemmt(seed, sha512))) }
    }

    // Signatur (64 Byte) ueber nachricht mit dem privaten Schluessel seed (32 Byte).
    fun signieren(seed: ByteArray, nachricht: ByteArray, sha512: (ByteArray) -> ByteArray): ByteArray {
        require(seed.size == 32) { "seed" }
        val d = geklemmt(seed, sha512)
        val pk = oeffentlich(seed, sha512)
        val r = sha512(d.copyOfRange(32, 64) + nachricht)
        reduce(r)
        val sig = ByteArray(64)
        pack(sig, 0, scalarbase(r))
        val h = sha512(sig.copyOfRange(0, 32) + pk + nachricht)
        reduce(h)
        val x = LongArray(64)
        for (i in 0 until 32) x[i] = (r[i].toInt() and 0xff).toLong()
        for (i in 0 until 32) for (j in 0 until 32) x[i + j] += (h[i].toInt() and 0xff).toLong() * (d[j].toInt() and 0xff).toLong()
        modL(sig, 32, x)
        return sig
    }
}
