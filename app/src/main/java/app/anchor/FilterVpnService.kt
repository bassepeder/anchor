package app.anchor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.concurrent.thread

/**
 * Only DNS goes through the tunnel: the phone is given a fake DNS server, and only that address plus
 * well-known public resolvers are routed in. Plain DNS to those is filtered; DoH/DoT to them is dropped,
 * so apps can't hard-code their way around the filter.
 */
class FilterVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private var updater: Thread? = null
    private val pool = Executors.newFixedThreadPool(8)
    private lateinit var filter: DnsFilter

    private val packageAdded = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = Policies.syncApps(context)
    }

    override fun onCreate() {
        super.onCreate()
        filter = DnsFilter(applicationContext, ::forward)
        val intentFilter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
        ContextCompat.registerReceiver(this, packageAdded, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        updater = thread(name = "anchor-lists", isDaemon = true) { updateLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        BlockList.reload(this)
        if (tun == null) establish()
        return START_STICKY
    }

    private fun establish() {
        val builder = Builder()
            .setSession("Anchor filter")
            .addAddress(TUN_V4, 24)
            .addAddress(TUN_V6, 64)
            .addDnsServer(FAKE_DNS)
            .addRoute(FAKE_DNS, 32)
            .setBlocking(true)
        HIJACKED_RESOLVERS.forEach { (ip, prefix) -> builder.addRoute(ip, prefix) }
        val pfd = builder.establish() ?: return
        tun = pfd
        running = true
        reader = thread(name = "anchor-dns", isDaemon = true) { readLoop(pfd) }
    }

    private fun readLoop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buf = ByteArray(Short.MAX_VALUE.toInt())
        while (!Thread.currentThread().isInterrupted) {
            val n = try {
                input.read(buf)
            } catch (e: IOException) {
                break
            }
            if (n <= 0) continue
            val query = DnsPacket.parse(buf, n) ?: continue
            try {
                pool.execute { handle(query, output) }
            } catch (e: RejectedExecutionException) {
                break
            }
        }
    }

    private fun handle(query: DnsQuery, out: FileOutputStream) {
        val answer = filter.answer(query.dns) ?: return
        val packet = DnsPacket.reply(query, answer)
        try {
            synchronized(out) { out.write(packet) }
        } catch (_: IOException) {
        }
    }

    private fun forward(dns: ByteArray): ByteArray? {
        for (server in UPSTREAMS) {
            try {
                DatagramSocket().use { socket ->
                    // Without protect() our own lookup would be routed back into the tunnel.
                    protect(socket)
                    socket.soTimeout = 4000
                    socket.send(DatagramPacket(dns, dns.size, InetAddress.getByName(server), 53))
                    val buf = ByteArray(4096)
                    val response = DatagramPacket(buf, buf.size)
                    socket.receive(response)
                    return buf.copyOf(response.length)
                }
            } catch (_: IOException) {
            }
        }
        return null
    }

    private fun updateLoop() {
        // The first check waits a minute so the tunnel is up before we download through it.
        var delayMs = 60_000L
        try {
            while (true) {
                Thread.sleep(delayMs)
                ListUpdater.updateIfDue(applicationContext)
                delayMs = 6 * 60 * 60_000L
            }
        } catch (_: InterruptedException) {
        }
    }

    private fun shutdown() {
        running = false
        reader?.interrupt()
        reader = null
        try {
            tun?.close()
        } catch (_: IOException) {
        }
        tun = null
    }

    override fun onRevoke() {
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        unregisterReceiver(packageAdded)
        updater?.interrupt()
        shutdown()
        pool.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "app.anchor.STOP_FILTER"
        private const val TUN_V4 = "10.111.222.1"
        private const val TUN_V6 = "fd00:a9c4:7e11::1"
        private const val FAKE_DNS = "10.111.222.2"

        // Cloudflare for Families: blocks adult content we haven't listed.
        private val UPSTREAMS = listOf("1.1.1.3", "1.0.0.3")

        private val HIJACKED_RESOLVERS = listOf(
            "8.8.8.8" to 32, "8.8.4.4" to 32,
            "1.1.1.1" to 32, "1.0.0.1" to 32,
            "1.1.1.2" to 32, "1.0.0.2" to 32,
            "9.9.9.9" to 32, "149.112.112.112" to 32,
            "208.67.222.222" to 32, "208.67.220.220" to 32,
            "94.140.14.14" to 32, "94.140.15.15" to 32,
            "45.90.28.0" to 24, "45.90.30.0" to 24,
            "2001:4860:4860::8888" to 128, "2001:4860:4860::8844" to 128,
            "2606:4700:4700::1111" to 128, "2606:4700:4700::1001" to 128,
            "2620:fe::fe" to 128, "2620:fe::9" to 128,
        )

        fun start(ctx: Context) {
            if (!Rules.dnsFilter(ctx)) return
            runCatching { ctx.startService(Intent(ctx, FilterVpnService::class.java)) }
        }

        @Volatile
        var running = false
            private set

        fun stop(ctx: Context) {
            if (!running) return
            runCatching { ctx.startService(Intent(ctx, FilterVpnService::class.java).setAction(ACTION_STOP)) }
        }
    }
}
