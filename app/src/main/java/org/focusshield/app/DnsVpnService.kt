package org.focusshield.app

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A local "VPN" that only carries DNS. Android sends every app's DNS lookups to a fake
 * server address inside this tunnel; lookups for blocked sites get "no such site" back, the
 * rest are forwarded to a public resolver. No other traffic goes through it.
 */
class DnsVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var out: FileOutputStream? = null
    private var reader: Thread? = null
    private var pool: ExecutorService? = null
    private lateinit var prefs: Prefs

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        if (tun != null) return START_STICKY
        prefs = Prefs(this)
        if (!prefs.dnsFilter) {
            stopSelf()
            return START_NOT_STICKY
        }

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(VPN_ADDRESS, 32)
            .addRoute(DNS_ADDRESS, 32)
            .addDnsServer(DNS_ADDRESS)
        // Our own forwarded lookups must go out on the real network, not back into the tunnel.
        try {
            builder.addDisallowedApplication(packageName)
        } catch (e: Exception) {
        }
        if (Build.VERSION.SDK_INT >= 29) builder.setBlocking(true)

        val fd = try {
            builder.establish()
        } catch (e: Exception) {
            null
        } ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        tun = fd
        out = FileOutputStream(fd.fileDescriptor)
        pool = Executors.newFixedThreadPool(6)
        running = true
        reader = Thread({ readLoop(FileInputStream(fd.fileDescriptor)) }, "dns-filter").also { it.start() }
        return START_STICKY
    }

    override fun onRevoke() {
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        running = false
        reader?.interrupt()
        reader = null
        pool?.shutdownNow()
        pool = null
        try {
            tun?.close()
        } catch (e: Exception) {
        }
        tun = null
        out = null
    }

    private fun readLoop(input: FileInputStream) {
        val buf = ByteArray(32767)
        while (running && !Thread.currentThread().isInterrupted) {
            val n = try {
                input.read(buf)
            } catch (e: Exception) {
                if (!running) return
                // Before Android 10 the tunnel can't be made blocking, so an empty read throws.
                try {
                    Thread.sleep(20)
                } catch (ie: InterruptedException) {
                    return
                }
                continue
            }
            if (n <= 0) {
                try {
                    Thread.sleep(20)
                } catch (ie: InterruptedException) {
                    return
                }
                continue
            }
            try {
                handlePacket(buf.copyOf(n))
            } catch (e: Exception) {
                // A malformed packet is dropped; the app will retry.
            }
        }
    }

    private fun handlePacket(p: ByteArray) {
        if (p.size < 28 || (p[0].toInt() shr 4) != 4) return
        val ihl = (p[0].toInt() and 0x0f) * 4
        if (p[9].toInt() != 17 || p.size < ihl + 8 + 12) return // UDP only
        val srcPort = u16(p, ihl)
        val dstPort = u16(p, ihl + 2)
        if (dstPort != 53) return
        val src = p.copyOfRange(12, 16)
        val dst = p.copyOfRange(16, 20)
        val query = p.copyOfRange(ihl + 8, p.size)

        val name = questionName(query)
        if (name != null && !prefs.isUnlocked && siteMatches(name, prefs.blockedSites) != null) {
            reply(dst, src, srcPort, blockedAnswer(query))
            return
        }
        pool?.execute {
            val answer = forward(query) ?: return@execute
            reply(dst, src, srcPort, answer)
        }
    }

    private fun forward(query: ByteArray): ByteArray? {
        for (server in UPSTREAM) {
            try {
                DatagramSocket().use { s ->
                    protect(s)
                    s.soTimeout = 4000
                    s.send(DatagramPacket(query, query.size, InetAddress.getByName(server), 53))
                    val buf = ByteArray(4096)
                    val resp = DatagramPacket(buf, buf.size)
                    s.receive(resp)
                    return buf.copyOf(resp.length)
                }
            } catch (e: Exception) {
                // try the next resolver
            }
        }
        return null
    }

    private fun reply(from: ByteArray, to: ByteArray, toPort: Int, payload: ByteArray) {
        val total = 20 + 8 + payload.size
        val pkt = ByteArray(total)
        pkt[0] = 0x45
        put16(pkt, 2, total)
        pkt[6] = 0x40 // don't fragment
        pkt[8] = 64 // ttl
        pkt[9] = 17 // UDP
        System.arraycopy(from, 0, pkt, 12, 4)
        System.arraycopy(to, 0, pkt, 16, 4)
        put16(pkt, 10, ipChecksum(pkt))
        put16(pkt, 20, 53)
        put16(pkt, 22, toPort)
        put16(pkt, 24, 8 + payload.size)
        // UDP checksum 0 = not used, which IPv4 allows.
        System.arraycopy(payload, 0, pkt, 28, payload.size)
        val o = out ?: return
        synchronized(o) {
            try {
                o.write(pkt)
            } catch (e: Exception) {
            }
        }
    }

    companion object {
        const val ACTION_STOP = "org.focusshield.app.STOP_DNS"
        private const val VPN_ADDRESS = "10.111.222.1"
        private const val DNS_ADDRESS = "10.111.222.2"
        private val UPSTREAM = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9")

        @Volatile
        var running = false
            private set

        /** Starts the filter if it's switched on and the user already allowed the VPN. */
        fun ensureRunning(ctx: Context) {
            if (running || !Prefs(ctx).dnsFilter) return
            if (prepare(ctx) != null) return
            try {
                ctx.startService(Intent(ctx, DnsVpnService::class.java))
            } catch (e: Exception) {
            }
        }

        fun stop(ctx: Context) {
            if (!running) return
            try {
                ctx.startService(Intent(ctx, DnsVpnService::class.java).setAction(ACTION_STOP))
            } catch (e: Exception) {
            }
        }

        private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)

        private fun put16(b: ByteArray, i: Int, v: Int) {
            b[i] = (v shr 8).toByte()
            b[i + 1] = v.toByte()
        }

        private fun ipChecksum(pkt: ByteArray): Int {
            var sum = 0
            for (i in 0 until 20 step 2) sum += u16(pkt, i)
            while (sum shr 16 != 0) sum = (sum and 0xffff) + (sum shr 16)
            return sum.inv() and 0xffff
        }

        /** The first question's name, e.g. "m.youtube.com", or null if the query is unusual. */
        internal fun questionName(q: ByteArray): String? {
            if (u16(q, 4) < 1) return null
            val labels = ArrayList<String>()
            var i = 12
            while (i < q.size) {
                val len = q[i].toInt() and 0xff
                if (len == 0) return labels.joinToString(".").lowercase()
                if (len and 0xc0 != 0 || i + 1 + len > q.size) return null
                labels.add(String(q, i + 1, len, Charsets.US_ASCII))
                i += 1 + len
            }
            return null
        }

        /** Same id and question, flagged as an answer with "no such domain". */
        internal fun blockedAnswer(q: ByteArray): ByteArray {
            var i = 12
            while (i < q.size && q[i].toInt() != 0) i += 1 + (q[i].toInt() and 0xff)
            val questionEnd = minOf(i + 1 + 4, q.size)
            val r = q.copyOf(questionEnd)
            r[2] = ((q[2].toInt() and 0x79) or 0x80).toByte() // QR=1, keep opcode and RD
            r[3] = 0x83.toByte() // RA=1, RCODE=3 (NXDOMAIN)
            put16(r, 4, 1)
            put16(r, 6, 0)
            put16(r, 8, 0)
            put16(r, 10, 0)
            return r
        }
    }
}
