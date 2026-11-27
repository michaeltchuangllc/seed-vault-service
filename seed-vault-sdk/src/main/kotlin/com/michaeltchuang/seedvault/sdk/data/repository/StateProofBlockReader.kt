package com.michaeltchuang.seedvault.sdk.data.repository

import com.michaeltchuang.seedvault.sdk.domain.model.StateProof
import com.michaeltchuang.seedvault.sdk.domain.model.StateProofMessage
import java.util.Base64

/**
 * Reads the state proof out of an algod block (`GET /v2/blocks/{round}?format=msgpack`).
 *
 * algod only serves a state proof from `/v2/stateproofs/{round}` for about the last 3 intervals (around 50 minutes),
 * but every state proof is also a transaction (`stpf`) in a block, and algod serves old blocks. That transaction
 * holds exactly what the endpoint serves: `sp` is the msgpack encoded state proof the light client verifies, and
 * `spmsg` is the message. So the proof of any interval can be read back from the block it was confirmed in.
 *
 * A small msgpack walker is enough: it finds `block.txns[].txn`, and for an `stpf` one it keeps the raw bytes of
 * `sp` without decoding them.
 */
internal object StateProofBlockReader {
    /** The state proof of the first `stpf` transaction in [block], or null if the block has none. */
    fun read(block: ByteArray): StateProof? {
        val root = Reader(block)
        val blockEntry = root.entries(0).firstOrNull { it.key == "block" } ?: return null
        val txns = root.entries(blockEntry.start).firstOrNull { it.key == "txns" } ?: return null
        for ((start, _) in root.items(txns.start)) {
            val stxn = root.entries(start).firstOrNull { it.key == "txn" } ?: continue
            val txn = root.entries(stxn.start).associateBy { it.key }
            val type = txn["type"] ?: continue
            if (root.string(type.start) != "stpf") continue
            val sp = txn.getValue("sp")
            val message = root.entries(txn.getValue("spmsg").start).associateBy { it.key }
            return StateProof(
                message =
                    StateProofMessage(
                        blockHeadersCommitment = base64(root.bytes(message.getValue("b").start)),
                        votersCommitment = base64(root.bytes(message.getValue("v").start)),
                        lnProvenWeight = root.unsigned(message.getValue("P").start),
                        firstAttestedRound = root.unsigned(message.getValue("f").start),
                        lastAttestedRound = root.unsigned(message.getValue("l").start),
                    ),
                proof = block.copyOfRange(sp.start, sp.end),
            )
        }
        return null
    }

    private fun base64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    /** One entry of a msgpack map: its string key and where its value starts and ends. */
    private class Entry(
        val key: String,
        val start: Int,
        val end: Int,
    )

    private class Reader(
        private val buf: ByteArray,
    ) {
        private fun u8(i: Int) = buf[i].toInt() and 0xFF

        private fun be(
            i: Int,
            size: Int,
        ): Long {
            var value = 0L
            for (k in 0 until size) value = (value shl 8) or u8(i + k).toLong()
            return value
        }

        /** End offset of the msgpack value that starts at [i]. */
        fun end(i: Int): Int {
            val b = u8(i)
            return when {
                b <= 0x7F || b >= 0xE0 || b == 0xC0 || b == 0xC2 || b == 0xC3 -> i + 1
                b in 0x80..0x8F -> skipAll(i + 1, 2 * (b and 0x0F))
                b in 0x90..0x9F -> skipAll(i + 1, b and 0x0F)
                b in 0xA0..0xBF -> i + 1 + (b and 0x1F)
                b == 0xCC || b == 0xD0 -> i + 2
                b == 0xCD || b == 0xD1 -> i + 3
                b == 0xCE || b == 0xD2 || b == 0xCA -> i + 5
                b == 0xCF || b == 0xD3 || b == 0xCB -> i + 9
                b == 0xC4 || b == 0xD9 -> i + 2 + u8(i + 1)
                b == 0xC5 || b == 0xDA -> i + 3 + be(i + 1, 2).toInt()
                b == 0xC6 || b == 0xDB -> i + 5 + be(i + 1, 4).toInt()
                b == 0xDC -> skipAll(i + 3, be(i + 1, 2).toInt())
                b == 0xDD -> skipAll(i + 5, be(i + 1, 4).toInt())
                b == 0xDE -> skipAll(i + 3, 2 * be(i + 1, 2).toInt())
                b == 0xDF -> skipAll(i + 5, 2 * be(i + 1, 4).toInt())
                else -> throw IllegalArgumentException("Unsupported msgpack byte 0x${b.toString(16)} at $i")
            }
        }

        private fun skipAll(
            from: Int,
            count: Int,
        ): Int {
            var j = from
            repeat(count) { j = end(j) }
            return j
        }

        /** The entries of the map at [i], in order. */
        fun entries(i: Int): List<Entry> {
            val b = u8(i)
            val (count, first) =
                when {
                    b in 0x80..0x8F -> (b and 0x0F) to i + 1
                    b == 0xDE -> be(i + 1, 2).toInt() to i + 3
                    b == 0xDF -> be(i + 1, 4).toInt() to i + 5
                    else -> throw IllegalArgumentException("Not a msgpack map: 0x${b.toString(16)} at $i")
                }
            var j = first
            return List(count) {
                val key = string(j)
                val start = end(j)
                val end = end(start)
                j = end
                Entry(key, start, end)
            }
        }

        /** The start and end of each element of the array at [i]. */
        fun items(i: Int): List<Pair<Int, Int>> {
            val b = u8(i)
            val (count, first) =
                when {
                    b in 0x90..0x9F -> (b and 0x0F) to i + 1
                    b == 0xDC -> be(i + 1, 2).toInt() to i + 3
                    b == 0xDD -> be(i + 1, 4).toInt() to i + 5
                    else -> throw IllegalArgumentException("Not a msgpack array: 0x${b.toString(16)} at $i")
                }
            var j = first
            return List(count) {
                val start = j
                j = end(j)
                start to j
            }
        }

        fun string(i: Int): String {
            val b = u8(i)
            return when {
                b in 0xA0..0xBF -> String(buf, i + 1, b and 0x1F, Charsets.UTF_8)
                b == 0xD9 -> String(buf, i + 2, u8(i + 1), Charsets.UTF_8)
                else -> throw IllegalArgumentException("Not a msgpack string: 0x${b.toString(16)} at $i")
            }
        }

        fun bytes(i: Int): ByteArray {
            val b = u8(i)
            return when (b) {
                0xC4 -> buf.copyOfRange(i + 2, i + 2 + u8(i + 1))
                0xC5 -> buf.copyOfRange(i + 3, i + 3 + be(i + 1, 2).toInt())
                else -> throw IllegalArgumentException("Not a msgpack binary: 0x${b.toString(16)} at $i")
            }
        }

        fun unsigned(i: Int): Long {
            val b = u8(i)
            return when (b) {
                in 0..0x7F -> b.toLong()
                0xCC -> be(i + 1, 1)
                0xCD -> be(i + 1, 2)
                0xCE -> be(i + 1, 4)
                0xCF -> be(i + 1, 8)
                else -> throw IllegalArgumentException("Not a msgpack unsigned integer: 0x${b.toString(16)} at $i")
            }
        }
    }
}
