package app.woorichadiary

import android.content.Context
import android.util.Log
import app.woorichadiary.core.PhotoMeta
import app.woorichadiary.core.PhotoSelector
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A local page the parent's phone opens by QR code.
 *
 * Runs only while the pairing screen is open, and every request needs that session's random
 * token. Timestamps reach the car first; only the photos the parent approves are uploaded.
 */
class PhotoServer(
    private val context: Context,
    private val store: DiaryStore,
    private val onDone: (outingId: String) -> Unit,
) {
    private var server: Http? = null

    /** @return the address to show as a QR code. */
    @Synchronized
    fun start(): String {
        stop()
        val token = ByteArray(16).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        server = Http(token).also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true) }
        return "http://${localAddress()}:$PORT/?t=$token"
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
    }

    private fun localAddress(): String = NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.asSequence() }
        .firstOrNull { it is Inet4Address }?.hostAddress ?: "127.0.0.1"

    private inner class Http(private val token: String) : NanoHTTPD(PORT) {
        override fun serve(session: IHTTPSession): Response = try {
            val given = session.parameters["t"]?.firstOrNull().orEmpty()
            if (!MessageDigest.isEqual(given.toByteArray(), token.toByteArray())) {
                text(Response.Status.FORBIDDEN, "forbidden")
            } else {
                route(session)
            }
        } catch (e: Exception) {
            Log.w(TAG, "request failed", e)
            text(Response.Status.INTERNAL_ERROR, "error")
        }

        private fun route(session: IHTTPSession): Response {
            val outingId = session.parameters["outing"]?.firstOrNull().orEmpty()
            return when (session.method to session.uri) {
                Method.GET to "/" -> newFixedLengthResponse(
                    Response.Status.OK,
                    "text/html; charset=utf-8",
                    context.assets.open("companion.html").bufferedReader().readText(),
                )
                Method.GET to "/api/outings" -> json(outingsJson())
                Method.POST to "/api/select" -> json(select(outingId, body(session, MAX_META_BYTES)))
                Method.POST to "/api/begin" -> {
                    // Approving again replaces the earlier set instead of adding to it.
                    store.photoDir(outingId).deleteRecursively()
                    json(JSONObject().put("ok", true).toString())
                }
                Method.POST to "/api/photo" -> savePhoto(session, outingId)
                Method.POST to "/api/done" -> {
                    onDone(outingId)
                    json(JSONObject().put("ok", true).toString())
                }
                else -> text(Response.Status.NOT_FOUND, "not found")
            }
        }

        private fun outingsJson(): String {
            val rows = JSONArray()
            store.allLogs().asReversed().forEach { log ->
                PhotoSelector.outings(log.windows).forEach { outing ->
                    rows.put(
                        JSONObject()
                            .put("id", outing.id)
                            .put("start", outing.waiting.start)
                            .put("end", outing.waiting.end)
                            .put("label", log.outing ?: JSONObject.NULL),
                    )
                }
            }
            return rows.toString()
        }

        private fun select(outingId: String, payload: ByteArray): String {
            val outing = store.allLogs().flatMap { PhotoSelector.outings(it.windows) }
                .firstOrNull { it.id == outingId } ?: return JSONObject().put("error", "no outing").toString()
            val meta = JSONArray(String(payload)).let { a ->
                List(a.length()) { i ->
                    val o = a.getJSONObject(i)
                    PhotoMeta(o.getString("id"), o.getLong("takenAt"), o.getBoolean("driverPhone"))
                }
            }
            val result = PhotoSelector.select(outing, meta)
            return JSONObject()
                .put("chosen", JSONArray(result.chosen.map { it.id }))
                .put("matched", result.matched)
                .put("excludedForSafety", result.excludedForSafety)
                .toString()
        }

        private fun savePhoto(session: IHTTPSession, outingId: String): Response {
            val bytes = body(session, MAX_PHOTO_BYTES)
            val isJpeg = bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
            if (!isJpeg || outingId.isEmpty()) return text(Response.Status.BAD_REQUEST, "jpeg only")

            val dir = store.photoDir(outingId).apply { mkdirs() }
            val index = dir.listFiles()?.size ?: 0
            if (index >= MAX_PHOTOS_PER_OUTING) return text(Response.Status.BAD_REQUEST, "too many")
            File(dir, "%03d.jpg".format(index)).writeBytes(bytes)
            return json(JSONObject().put("ok", true).toString())
        }

        private fun body(session: IHTTPSession, limit: Int): ByteArray {
            val length = session.headers["content-length"]?.toIntOrNull() ?: 0
            require(length in 0..limit) { "body too large" }
            val out = ByteArrayOutputStream(length)
            val buffer = ByteArray(16 * 1024)
            var left = length
            while (left > 0) {
                val read = session.inputStream.read(buffer, 0, minOf(buffer.size, left))
                if (read < 0) break
                out.write(buffer, 0, read)
                left -= read
            }
            return out.toByteArray()
        }

        private fun json(body: String) = newFixedLengthResponse(Response.Status.OK, "application/json", body)
        private fun text(status: Response.Status, body: String) = newFixedLengthResponse(status, "text/plain", body)
    }

    private companion object {
        const val TAG = "PhotoServer"
        const val PORT = 8765
        const val MAX_META_BYTES = 1_000_000
        const val MAX_PHOTO_BYTES = 8_000_000
        const val MAX_PHOTOS_PER_OUTING = 40
    }
}
