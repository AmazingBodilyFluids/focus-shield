package org.focusshield.app

import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder

/**
 * A tiny web server on 127.0.0.1 that only serves the "Blocked" page. Blocked tabs are sent
 * here, so the browser stays open on a harmless page instead of being closed. Nothing
 * outside the phone can reach it.
 */
class BlockedPageServer {
    @Volatile
    private var socket: ServerSocket? = null

    val isRunning: Boolean get() = socket != null

    fun start() {
        if (socket != null) return
        val s = try {
            ServerSocket(PORT, 16, InetAddress.getByName("127.0.0.1"))
        } catch (e: Exception) {
            return
        }
        socket = s
        Thread({
            while (!s.isClosed) {
                val c = try {
                    s.accept()
                } catch (e: Exception) {
                    break
                }
                try {
                    c.soTimeout = 3000
                    val line = c.getInputStream().bufferedReader().readLine() ?: ""
                    val path = line.split(' ').getOrNull(1) ?: "/"
                    val body = if (path.startsWith("/favicon")) "" else page(siteFrom(path))
                    val status = if (body.isEmpty()) "404 Not Found" else "200 OK"
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    val head = "HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\n" +
                        "Content-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
                    c.getOutputStream().apply {
                        write(head.toByteArray())
                        write(bytes)
                        flush()
                    }
                } catch (e: Exception) {
                } finally {
                    try {
                        c.close()
                    } catch (e: Exception) {
                    }
                }
            }
        }, "blocked-page").apply { isDaemon = true }.start()
    }

    fun stop() {
        try {
            socket?.close()
        } catch (e: Exception) {
        }
        socket = null
    }

    private fun siteFrom(path: String): String {
        val q = path.substringAfter("?", "")
        val raw = q.split('&').firstOrNull { it.startsWith("site=") }?.removePrefix("site=") ?: ""
        return try {
            URLDecoder.decode(raw, "UTF-8")
        } catch (e: Exception) {
            ""
        }
    }

    private fun page(site: String): String {
        val safe = site.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val what = if (safe.isEmpty()) "This site" else "<b>$safe</b>"
        return """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Blocked by Focus Shield</title>
<style>
body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;
background:#1b5e20;color:#fff;font-family:system-ui,sans-serif;text-align:center;padding:24px;box-sizing:border-box}
h1{font-size:30px;margin:0 0 16px}p{font-size:18px;line-height:1.5;margin:8px 0;opacity:.95}
</style></head>
<body><div>
<h1>Blocked by Focus Shield</h1>
<p>$what is blocked right now.</p>
<p>To unlock, open Focus Shield and meet your unlock conditions.</p>
</div></body></html>"""
    }

    companion object {
        const val PORT = 48231

        fun urlFor(site: String): String =
            "http://127.0.0.1:$PORT/?site=" + java.net.URLEncoder.encode(site, "UTF-8")
    }
}
