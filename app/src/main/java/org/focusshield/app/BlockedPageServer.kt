package org.focusshield.app

import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder

/**
 * A tiny web server on 127.0.0.1 that only serves the "Blocked" page. Blocked tabs are sent
 * here, so the browser stays open on a harmless page instead of being closed. Nothing
 * outside the phone can reach it.
 */
class BlockedPageServer(private val line: () -> String) {
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
                    val body = if (path.startsWith("/favicon")) "" else page(param(path, "site"), param(path, "n").toIntOrNull() ?: 1)
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

    private fun param(path: String, name: String): String {
        val q = path.substringAfter("?", "")
        val raw = q.split('&').firstOrNull { it.startsWith("$name=") }?.removePrefix("$name=") ?: ""
        return try {
            URLDecoder.decode(raw, "UTF-8")
        } catch (e: Exception) {
            ""
        }
    }

    private fun esc(v: String) =
        v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun page(site: String, attempt: Int): String {
        val safe = esc(site)
        val what = if (safe.isEmpty()) "This site" else "<b>$safe</b>"
        val taunt = esc(try { line() } catch (e: Exception) { "Denied." })
        val next = urlFor(site) + "&n=" + (attempt + 1)
        val counter = if (attempt > 1) "<p class=\"n\">Attempt $attempt. The Guardian is keeping count.</p>" else ""
        val anim = listOf("peek", "spin", "wobble", "stomp")[(attempt + site.length) % 4]
        return """<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Blocked by Threshold</title>
<style>
html,body{margin:0;min-height:100%;background:#0e0f15;color-scheme:dark}
body{min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;
padding:40px 28px;box-sizing:border-box;color:#ecebf3;font-family:system-ui,-apple-system,Roboto,sans-serif;
background:radial-gradient(120% 70% at 50% 38%,#2a1820 0%,#14151e 60%,#0e0f15 100%)}
.top{position:absolute;top:26px;left:0;right:0;font-size:12px;letter-spacing:.16em;color:#9a9cb2}
.ogre{font-size:150px;line-height:1;filter:drop-shadow(0 18px 30px rgba(0,0,0,.6));animation:$anim .8s cubic-bezier(.2,1.4,.4,1)}
h1{font-family:"Noto Serif",Georgia,serif;font-size:30px;line-height:1.15;margin:22px 0 0;max-width:16ch}
p{color:#9a9cb2;font-size:14px;margin:12px 0 0}p b{color:#f4bccb;font-family:monospace}
.a{display:flex;gap:10px;margin-top:28px;flex-wrap:wrap;justify-content:center}
.a a{padding:10px 18px;border-radius:999px;text-decoration:none;font-size:14px;font-weight:500;border:1px solid #2e3144;background:#232636;color:#ecebf3}
.a a.p{background:#ead6a6;border-color:#ead6a6;color:#1a1712}
.n{position:absolute;bottom:24px;left:0;right:0;font-size:12px}
@keyframes peek{from{transform:translateY(90%) rotate(-12deg)}}
@keyframes spin{from{transform:scale(0) rotate(-540deg)}}
@keyframes wobble{20%{transform:rotate(-14deg)}40%{transform:rotate(12deg)}60%{transform:rotate(-8deg)}80%{transform:rotate(5deg)}}
@keyframes stomp{0%{transform:translateY(-140%) scale(.8)}60%{transform:translateY(0) scale(1.08,.9)}}
@media (prefers-reduced-motion:reduce){.ogre{animation:none}}
</style></head>
<body>
<div class="top">THRESHOLD</div>
<div class="ogre">${Ui.OGRE}</div>
<h1>$taunt</h1>
<p>$what is behind the threshold.</p>
<div class="a"><a href="$next">Try again</a><a class="p" href="about:blank">Close this tab</a></div>
$counter
</body></html>"""
    }

    companion object {
        const val PORT = 48231

        fun urlFor(site: String): String =
            "http://127.0.0.1:$PORT/?site=" + java.net.URLEncoder.encode(site, "UTF-8")
    }
}
