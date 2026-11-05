package com.conreo.couchytv.premium

import java.io.BufferedInputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** One foreground-only, ten-minute LAN session. No external resources or port forwarding. */
internal class PairingServer(
    bindAddress: InetAddress = localAddress() ?: error("Connect the Fire TV to your home Wi-Fi first"),
    lifetimeSeconds: Long = 600,
    private val onImage: (ByteArray) -> Unit,
) : AutoCloseable {
    private val random = SecureRandom()
    val code: String = (100000 + random.nextInt(900000)).toString()
    private val token = ByteArray(24).also(random::nextBytes).joinToString("") { "%02x".format(it) }
    private val address = bindAddress
    private val socket = ServerSocket(0, 4, address)
    val url = "http://${address.hostAddress}:${socket.localPort}"
    private val worker = Executors.newSingleThreadExecutor()
    private val timer = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var active = true
    private var attempts = 0
    init {
        timer.schedule({ close() }, lifetimeSeconds, TimeUnit.SECONDS)
        worker.execute {
            while (active) {
                try { socket.accept().use { serve(it) } }
                catch (_: Exception) { /* malformed/slow clients cannot crash the launcher */ }
            }
        }
    }
    private fun serve(client: Socket) {
        if (!client.inetAddress.isSiteLocalAddress && !(address.isLoopbackAddress && client.inetAddress.isLoopbackAddress)) return
        client.soTimeout = 5000
        val input = BufferedInputStream(client.getInputStream())
        var headerBytes = 0
        fun line(): String {
            val b = StringBuilder()
            while (true) {
                val c = input.read()
                require(c >= 0 && ++headerBytes <= 8192)
                if (c == 10) return b.toString().trimEnd('\r')
                b.append(c.toChar())
            }
        }
        val request = line().split(' ')
        if (request.size != 3) return
        val headers = mutableMapOf<String, String>()
        while (true) {
            val l = line(); if (l.isEmpty()) break
            val i = l.indexOf(':'); require(i > 0)
            headers[l.substring(0, i).lowercase()] = l.substring(i + 1).trim()
        }
        fun reply(status: String, body: String, type: String = "text/plain; charset=utf-8") {
            val data = body.toByteArray(Charsets.UTF_8)
            val head = "HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${data.size}\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src blob: data:; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'\r\nConnection: close\r\n\r\n"
            client.getOutputStream().apply { write(head.toByteArray()); write(data); flush() }
        }
        if (headers["host"] != "${address.hostAddress}:${socket.localPort}") { reply("403 Forbidden", "Invalid host"); return }
        if (request[0] == "GET" && request[1] == "/") { reply("200 OK", PAGE, "text/html; charset=utf-8"); return }
        if (request[0] != "POST" || headers["origin"] != url) { reply("403 Forbidden", "Open the address shown on the TV"); return }
        val size = headers["content-length"]?.toIntOrNull()
        if (size == null || size !in 1..4 * 1024 * 1024 || headers.containsKey("transfer-encoding")) { reply("413 Payload Too Large", "Image must be smaller than 4 MB"); return }
        fun body(): ByteArray {
            val bytes = ByteArray(size)
            var read = 0
            while (read < size) { val n = input.read(bytes, read, size - read); require(n > 0); read += n }
            return bytes
        }
        if (request[1] == "/pair") {
            if (++attempts > 8) { reply("429 Too Many Requests", "Close the TV upload screen and try again"); close(); return }
            if (size > 16 || body().toString(Charsets.UTF_8) != code) { reply("403 Forbidden", "Incorrect pairing code"); return }
            reply("200 OK", token); return
        }
        if (request[1] != "/icon" || headers["authorization"] != "Bearer $token") { reply("403 Forbidden", "Pair with the TV first"); return }
        try {
            onImage(body())
            reply("200 OK", "Saved on your TV. You can close this page.")
            close()
        } catch (e: Exception) { reply("400 Bad Request", e.message ?: "Invalid image") }
    }
    override fun close() {
        active = false
        runCatching { socket.close() }
        timer.shutdownNow(); worker.shutdown()
    }
    companion object {
        fun localAddress(): InetAddress? = NetworkInterface.getNetworkInterfaces()?.toList()
            ?.filter { it.isUp && !it.isLoopback }?.flatMap { it.inetAddresses.toList() }
            ?.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        private val PAGE = """<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Apple TV Home • Icon Studio</title>
<style>body{background:#101117;color:#fff;font:17px system-ui;margin:0;padding:28px;max-width:560px;margin:auto}h1{font-size:32px}label{display:block;margin:22px 0}input,button{font:inherit;padding:12px;border-radius:12px;border:0;max-width:95%}button{background:#b9d5ff;color:#101117;margin:10px 0;min-height:48px}canvas{width:240px;height:240px;background:repeating-conic-gradient(#333 0% 25%,#222 0% 50%) 50%/20px 20px;display:block;margin:24px auto}input[type=range]{width:100%;padding:0}small{color:#bdc2d1}#editor{display:none}</style>
<h1>Icon Studio</h1><p>Your image stays on your home network.</p><section id="pair"><label>Code on your television <input id="code" inputmode="numeric" maxlength="6" autocomplete="off"></label><button id="connect">Pair with TV</button></section>
<section id="editor"><label>Choose PNG, JPG or WebP <input id="file" type="file" accept="image/png,image/jpeg,image/webp"></label><canvas id="preview" width="512" height="512"></canvas><label>Size <input id="zoom" type="range" min="0.2" max="3" step="0.01" value="1"></label><label>Horizontal crop <input id="x" type="range" min="-1" max="1" step="0.01" value="0"></label><label>Vertical crop <input id="y" type="range" min="-1" max="1" step="0.01" value="0"></label><small>The preview uses your TV's adjustable rounded icon shape. Transparency is preserved.</small><button id="send" disabled>Save icon to TV</button></section><p id="status" role="status"></p>
<script>let token='',img;const el=id=>document.getElementById(id),c=el('preview'),ctx=c.getContext('2d');c.style.borderRadius='18%';function draw(){ctx.clearRect(0,0,512,512);if(!img)return;let scale=Math.max(512/img.width,512/img.height)*Number(el('zoom').value),w=img.width*scale,h=img.height*scale;ctx.drawImage(img,(512-w)/2+Number(el('x').value)*256,(512-h)/2+Number(el('y').value)*256,w,h)}['zoom','x','y'].forEach(id=>el(id).oninput=draw);el('connect').onclick=async()=>{try{let r=await fetch('/pair',{method:'POST',body:el('code').value});let t=await r.text();if(!r.ok)throw Error(t);token=t;el('pair').hidden=true;el('editor').style.display='block';el('status').textContent='Paired. Choose an image.'}catch(e){el('status').textContent=e.message}};el('file').onchange=()=>{const f=el('file').files[0];if(!f)return;if(f.size>4194304){el('status').textContent='Choose an image smaller than 4 MB';return}const u=URL.createObjectURL(f),next=new Image;next.onload=()=>{img=next;URL.revokeObjectURL(u);draw();el('send').disabled=false};next.onerror=()=>{URL.revokeObjectURL(u);el('status').textContent='Unsupported image'};next.src=u};el('send').onclick=()=>{el('send').disabled=true;c.toBlob(async b=>{try{let r=await fetch('/icon',{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'image/png'},body:b});let t=await r.text();if(!r.ok)throw Error(t);el('status').textContent=t;el('editor').hidden=true}catch(e){el('status').textContent=e.message;el('send').disabled=false}},'image/png')};</script></html>"""
    }
}
