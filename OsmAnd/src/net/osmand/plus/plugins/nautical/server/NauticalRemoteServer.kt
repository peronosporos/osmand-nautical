package net.osmand.plus.plugins.nautical.server

import android.content.Context
import android.net.wifi.WifiManager
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.Executors

class NauticalRemoteServer(
    private val app: OsmandApplication,
    val port: Int = 8080
) {
    private val log = PlatformUtil.getLog(NauticalRemoteServer::class.java)
    private var httpServer: HttpServer? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var isRunning = false
        private set

    fun start() {
        if (isRunning) return
        try {
            httpServer = HttpServer.create(InetSocketAddress(port), 0).apply {
                executor = Executors.newCachedThreadPool()
                createContext("/", DashboardHandler())
                createContext("/api/telemetry", TelemetryApiHandler(app))
                start()
            }
            isRunning = true
            log.info("Nautical Remote Server started on port $port")
        } catch (e: Exception) {
            log.error("Failed to start Nautical Remote Server: ${e.message}", e)
        }
    }

    fun stop() {
        if (!isRunning) return
        try {
            httpServer?.stop(0)
            httpServer = null
            isRunning = false
            scope.cancel()
            log.info("Nautical Remote Server stopped")
        } catch (e: Exception) {
            log.error("Error stopping remote server: ${e.message}", e)
        }
    }

    fun getServerUrl(context: Context): String {
        val ip = getLocalIpAddress(context)
        return "http://$ip:$port"
    }

    private fun getLocalIpAddress(context: Context): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipInt = wifiManager.connectionInfo.ipAddress
            if (ipInt != 0) {
                InetAddress.getByAddress(
                    ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ipInt).array()
                ).hostAddress ?: "127.0.0.1"
            } else "127.0.0.1"
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }

    private inner class DashboardHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>OsmAnd Nautical Remote Berth Display</title>
                    <style>
                        body {
                            background-color: #0b0f19;
                            color: #f1f5f9;
                            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                            margin: 0;
                            padding: 16px;
                            display: flex;
                            flex-direction: column;
                            align-items: center;
                        }
                        .night { background-color: #120000; color: #ff1744; }
                        .night .card { background-color: #200000; border-color: #8b0000; color: #ff1744; }
                        .header { font-size: 20px; font-weight: bold; margin-bottom: 16px; text-transform: uppercase; }
                        .grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
                            gap: 16px;
                            width: 100%;
                            max-width: 600px;
                        }
                        .card {
                            background-color: #1e293b;
                            border: 1px solid #334155;
                            border-radius: 12px;
                            padding: 16px;
                            text-align: center;
                        }
                        .label { font-size: 11px; text-transform: uppercase; opacity: 0.7; letter-spacing: 1px; }
                        .value { font-size: 28px; font-weight: bold; margin-top: 4px; }
                        .unit { font-size: 14px; opacity: 0.8; }
                        .badge {
                            display: inline-block;
                            padding: 4px 8px;
                            border-radius: 6px;
                            font-size: 12px;
                            font-weight: bold;
                            margin-top: 8px;
                        }
                        .btn-night {
                            margin-top: 24px;
                            padding: 10px 20px;
                            background-color: #e11d48;
                            color: white;
                            border: none;
                            border-radius: 8px;
                            font-weight: bold;
                            cursor: pointer;
                        }
                    </style>
                </head>
                <body>
                    <div class="header">OsmAnd Berth Remote</div>
                    <div class="grid">
                        <div class="card">
                            <div class="label">Speed (SOG)</div>
                            <div class="value" id="sog">--</div>
                            <div class="unit">knots</div>
                        </div>
                        <div class="card">
                            <div class="label">Course (COG)</div>
                            <div class="value" id="cog">--</div>
                            <div class="unit">degrees</div>
                        </div>
                        <div class="card">
                            <div class="label">Depth Below Keel</div>
                            <div class="value" id="depth">--</div>
                            <div class="unit">meters</div>
                        </div>
                        <div class="card">
                            <div class="label">True Wind Angle</div>
                            <div class="value" id="twa">--</div>
                            <div class="unit">degrees</div>
                        </div>
                        <div class="card">
                            <div class="label">True Wind Speed</div>
                            <div class="value" id="tws">--</div>
                            <div class="unit">knots</div>
                        </div>
                        <div class="card">
                            <div class="label">Anchor Watch</div>
                            <div class="value" id="anchor">HOLDING</div>
                            <div class="unit" id="anchor_radius">Radius: -- m</div>
                        </div>
                    </div>
                    <button class="btn-night" onclick="document.body.classList.toggle('night')">Night Vision Toggle</button>

                    <script>
                        async function updateTelemetry() {
                            try {
                                const res = await fetch('/api/telemetry');
                                const data = await res.json();
                                document.getElementById('sog').innerText = (data.sogKn || 0).toFixed(1);
                                document.getElementById('cog').innerText = Math.round(data.cogDeg || 0) + '°';
                                document.getElementById('depth').innerText = (data.depthM || 0).toFixed(1);
                                document.getElementById('twa').innerText = Math.round(data.twaDeg || 0) + '°';
                                document.getElementById('tws').innerText = (data.twsKn || 0).toFixed(1);
                                document.getElementById('anchor_radius').innerText = 'Radius: ' + (data.swingRadiusM || 0) + ' m';
                            } catch(e) {}
                        }
                        setInterval(updateTelemetry, 1000);
                        updateTelemetry();
                    </script>
                </body>
                </html>
            """.trimIndent()

            val bytes = html.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "text/html; charset=UTF-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private inner class TelemetryApiHandler(private val app: OsmandApplication) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val state = NauticalPlugin.engine?.getCurrentState()
            val sogKn = (state?.speedOverGround ?: 0.0) * 1.94384
            val cogDeg = state?.courseOverGroundTrue?.let { Math.toDegrees(it) } ?: 0.0
            val depthM = state?.depthBelowKeel ?: state?.depthBelowTransducer ?: 0.0
            val twaDeg = state?.trueWindAngle?.let { Math.toDegrees(it) } ?: 0.0
            val twsKn = (state?.windSpeedTrue ?: 0.0) * 1.94384
            val swingRadius = app.settings.NAUTICAL_ANCHOR_RADIUS.get()

            val json = String.format(
                Locale.US,
                """{"sogKn":%.2f,"cogDeg":%.1f,"depthM":%.2f,"twaDeg":%.1f,"twsKn":%.2f,"swingRadiusM":%d}""",
                sogKn, cogDeg, depthM, twaDeg, twsKn, swingRadius
            )

            val bytes = json.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}
