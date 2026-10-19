package com.example.proyectofinalgrupo2.api

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.example.proyectofinalgrupo2.models.Alumno
import com.example.proyectofinalgrupo2.models.Asistencia
import com.example.proyectofinalgrupo2.models.Curso
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class ResultadoSincronizacion(
    val localId: Long,
    val remotoId: Int?,
    val success: Boolean,
    val message: String
)

class ApiClient private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("asiste_plus_prefs", Context.MODE_PRIVATE)
    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val KEY_BASE_URL = "server_base_url"
        const val DEFAULT_BASE_URL = "http://10.0.2.2:3000" // IP especial del emulador hacia el localhost del host

        @Volatile
        private var INSTANCE: ApiClient? = null

        fun getInstance(context: Context): ApiClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiClient(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun getBaseUrl(): String {
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun setBaseUrl(newUrl: String) {
        val clean = newUrl.trim().removeSuffix("/")
        prefs.edit().putString(KEY_BASE_URL, clean).apply()
    }

    // =================================================================
    // 1. COMPROBAR CONEXIÓN / HEALTH CHECK
    // =================================================================
    fun checkHealth(callback: (isOnline: Boolean, message: String) -> Unit) {
        executor.execute {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("${getBaseUrl()}/api/health")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                val code = conn.responseCode
                if (code == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.readText()
                    reader.close()
                    val json = JSONObject(response)
                    val status = json.optString("status")
                    mainHandler.post { callback(status == "OK", "Conectado al servidor") }
                } else {
                    mainHandler.post { callback(false, "Servidor respondió con código $code") }
                }
            } catch (e: Exception) {
                mainHandler.post { callback(false, "Sin conexión: ${e.message}") }
            } finally {
                conn?.disconnect()
            }
        }
    }

    // =================================================================
    // 2. CONSUMO REST: GET /api/cursos
    // =================================================================
    fun getCursos(callback: (success: Boolean, cursos: List<Curso>, error: String?) -> Unit) {
        executor.execute {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("${getBaseUrl()}/api/cursos")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val json = JSONObject(response)
                    val dataArray = json.getJSONArray("data")
                    val lista = mutableListOf<Curso>()

                    for (i in 0 until dataArray.length()) {
                        val obj = dataArray.getJSONObject(i)
                        lista.add(
                            Curso(
                                id = obj.getInt("id"),
                                codigo = obj.getString("codigo"),
                                nombre = obj.getString("nombre"),
                                docente = obj.getString("docente"),
                                seccion = obj.optString("seccion", "T5BN"),
                                ciclo = obj.optString("ciclo", "V"),
                                creditos = obj.optInt("creditos", 4),
                                activo = obj.optBoolean("activo", true)
                            )
                        )
                    }
                    mainHandler.post { callback(true, lista, null) }
                } else {
                    mainHandler.post { callback(false, emptyList(), "Error HTTP ${conn.responseCode}") }
                }
            } catch (e: Exception) {
                mainHandler.post { callback(false, emptyList(), e.localizedMessage) }
            } finally {
                conn?.disconnect()
            }
        }
    }

    // =================================================================
    // 3. CONSUMO REST: GET /api/alumnos
    // =================================================================
    fun getAlumnos(callback: (success: Boolean, alumnos: List<Alumno>, error: String?) -> Unit) {
        executor.execute {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("${getBaseUrl()}/api/alumnos")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val json = JSONObject(response)
                    val dataArray = json.getJSONArray("data")
                    val lista = mutableListOf<Alumno>()

                    for (i in 0 until dataArray.length()) {
                        val obj = dataArray.getJSONObject(i)
                        lista.add(
                            Alumno(
                                id = obj.getInt("id"),
                                codigo = obj.getString("codigo"),
                                nombres = obj.getString("nombres"),
                                apellidos = obj.getString("apellidos"),
                                correo = obj.getString("correo"),
                                telefono = if (obj.isNull("telefono")) null else obj.getString("telefono"),
                                activo = obj.optBoolean("activo", true)
                            )
                        )
                    }
                    mainHandler.post { callback(true, lista, null) }
                } else {
                    mainHandler.post { callback(false, emptyList(), "Error HTTP ${conn.responseCode}") }
                }
            } catch (e: Exception) {
                mainHandler.post { callback(false, emptyList(), e.localizedMessage) }
            } finally {
                conn?.disconnect()
            }
        }
    }

    // =================================================================
    // 4. CONSUMO REST: GET /api/asistencias
    // =================================================================
    fun getAsistencias(
        cursoId: Int? = null,
        fecha: String? = null,
        callback: (success: Boolean, asistencias: List<Asistencia>, error: String?) -> Unit
    ) {
        executor.execute {
            var conn: HttpURLConnection? = null
            try {
                val params = mutableListOf<String>()
                if (cursoId != null) params.add("curso_id=$cursoId")
                if (!fecha.isNullOrEmpty()) params.add("fecha=$fecha")

                val query = if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
                val url = URL("${getBaseUrl()}/api/asistencias$query")

                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                }
                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val json = JSONObject(response)
                    val dataArray = json.getJSONArray("data")
                    val lista = mutableListOf<Asistencia>()

                    for (i in 0 until dataArray.length()) {
                        val obj = dataArray.getJSONObject(i)
                        lista.add(
                            Asistencia(
                                id = 0,
                                remotoId = obj.getInt("id"),
                                alumnoId = obj.getInt("alumno_id"),
                                alumnoCodigo = obj.optString("alumno_codigo", ""),
                                alumnoNombre = obj.optString("alumno_nombre_completo", "${obj.optString("alumno_apellidos")}, ${obj.optString("alumno_nombres")}"),
                                cursoId = obj.getInt("curso_id"),
                                cursoCodigo = obj.optString("curso_codigo", ""),
                                cursoNombre = obj.optString("curso_nombre", ""),
                                fecha = obj.getString("fecha"),
                                estado = obj.getString("estado"),
                                observacion = obj.optString("observacion", ""),
                                sincronizado = true,
                                fechaRegistro = obj.optString("created_at", "")
                            )
                        )
                    }
                    mainHandler.post { callback(true, lista, null) }
                } else {
                    mainHandler.post { callback(false, emptyList(), "Error HTTP ${conn.responseCode}") }
                }
            } catch (e: Exception) {
                mainHandler.post { callback(false, emptyList(), e.localizedMessage) }
            } finally {
                conn?.disconnect()
            }
        }
    }

    // =================================================================
    // 5. CONSUMO REST: POST /api/asistencias/sincronizar (Sincronización)
    // =================================================================
    fun sincronizarLote(
        pendientes: List<Asistencia>,
        callback: (
            success: Boolean,
            resultados: List<ResultadoSincronizacion>,
            error: String?
        ) -> Unit
    ) {
        if (pendientes.isEmpty()) {
            callback(true, emptyList(), null)
            return
        }

        executor.execute {
            var conn: HttpURLConnection? = null

            try {
                val url = URL("${getBaseUrl()}/api/asistencias/sincronizar")

                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                    )
                }

                val itemsArray = JSONArray()

                pendientes.forEach { a ->
                    val item = JSONObject().apply {
                        put("local_id", a.id)
                        if (a.remotoId != null) {
                            put("remoto_id", a.remotoId)
                        }
                        put("alumno_id", a.alumnoId)
                        put("curso_id", a.cursoId)
                        put("fecha", a.fecha)
                        put("estado", a.estado.uppercase())
                        put("observacion", a.observacion)
                    }

                    itemsArray.put(item)
                }

                val payload = JSONObject().apply {
                    put("items", itemsArray)
                }

                val writer = OutputStreamWriter(
                    conn.outputStream,
                    "UTF-8"
                )

                writer.write(payload.toString())
                writer.flush()
                writer.close()

                val code = conn.responseCode

                val stream = if (code in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream ?: conn.inputStream
                }

                val reader = BufferedReader(
                    InputStreamReader(stream, "UTF-8")
                )

                val responseText = reader.readText()
                reader.close()

                val syncResult = parseSincronizacionResponse(responseText, code)

                mainHandler.post {
                    callback(
                        syncResult.success,
                        syncResult.resultados,
                        syncResult.error
                    )
                }

            } catch (e: Exception) {

                mainHandler.post {
                    callback(
                        false,
                        emptyList(),
                        "Fallo de conexión: ${e.localizedMessage}"
                    )
                }

            } finally {
                conn?.disconnect()
            }
        }
    }

    fun eliminarAsistenciaRemota(
        remotoId: Int,
        callback: (success: Boolean, error: String?) -> Unit
    ) {
        executor.execute {
            var conn: HttpURLConnection? = null

            try {
                val url = URL("${getBaseUrl()}/api/asistencias/$remotoId")

                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                val code = conn.responseCode

                if (code in 200..299 || code == 404) {
                    mainHandler.post {
                        callback(true, null)
                    }
                } else {
                    val errorStream =
                        conn.errorStream ?: conn.inputStream

                    val reader = BufferedReader(
                        InputStreamReader(errorStream, "UTF-8")
                    )

                    val response = reader.readText()
                    reader.close()

                    var parsedJson: JSONObject? = null
                    try {
                        val trimmed = response.trim()
                        if (trimmed.startsWith("{")) {
                            parsedJson = JSONObject(trimmed)
                        }
                    } catch (_: Exception) {
                        // Ignorar
                    }

                    val errorMsg = when {
                        parsedJson != null && parsedJson.has("message") && !parsedJson.isNull("message") -> parsedJson.optString("message")
                        parsedJson != null && parsedJson.has("error") && !parsedJson.isNull("error") -> parsedJson.optString("error")
                        parsedJson != null && parsedJson.has("mensaje") && !parsedJson.isNull("mensaje") -> parsedJson.optString("mensaje")
                        else -> response
                    }

                    mainHandler.post {
                        callback(
                            false,
                            "Error del servidor ($code): $errorMsg"
                        )
                    }
                }

            } catch (e: Exception) {

                mainHandler.post {
                    callback(
                        false,
                        "Fallo de conexión: ${e.localizedMessage}"
                    )
                }

            } finally {
                conn?.disconnect()
            }
        }
    }
}

data class SyncResponseResult(
    val success: Boolean,
    val resultados: List<ResultadoSincronizacion>,
    val error: String?
)

internal fun parseSincronizacionResponse(
    responseText: String,
    code: Int
): SyncResponseResult {
    val isHttp2xx = code in 200..299
    val trimmed = responseText.trim()
    var parsedJson: JSONObject? = null
    var jsonMalformed = false

    if (trimmed.isNotEmpty()) {
        try {
            if (trimmed.startsWith("{")) {
                parsedJson = JSONObject(trimmed)
            } else {
                jsonMalformed = true
            }
        } catch (_: Exception) {
            jsonMalformed = true
        }
    } else {
        jsonMalformed = true
    }

    var itemsFieldTypeInvalid = false
    var itemsArray: JSONArray? = null
    var rawItemsCount = 0

    if (parsedJson != null && parsedJson.has("items")) {
        if (parsedJson.isNull("items")) {
            itemsArray = null
            rawItemsCount = 0
        } else {
            itemsArray = parsedJson.optJSONArray("items")
            if (itemsArray != null) {
                rawItemsCount = itemsArray.length()
            } else {
                itemsFieldTypeInvalid = true
            }
        }
    }

    val resultados = mutableListOf<ResultadoSincronizacion>()

    if (itemsArray != null && rawItemsCount > 0) {

        for (i in 0 until rawItemsCount) {

            val item = itemsArray.optJSONObject(i) ?: continue

            val localId = when {
                item.has("local_id") && !item.isNull("local_id") -> item.optLong("local_id", -1L)
                item.has("localId") && !item.isNull("localId") -> item.optLong("localId", -1L)
                else -> -1L
            }

            val remotoId = when {
                item.has("remoto_id") && !item.isNull("remoto_id") -> item.optInt("remoto_id")
                item.has("remotoId") && !item.isNull("remotoId") -> item.optInt("remotoId")
                item.has("id") && !item.isNull("id") -> item.optInt("id")
                else -> null
            }

            val success = when {
                item.has("success") -> item.optBoolean("success", false)
                item.has("exito") -> item.optBoolean("exito", false)
                item.has("status") -> {
                    val s = item.optString("status")
                    s.equals("OK", ignoreCase = true) ||
                            s.equals("SUCCESS", ignoreCase = true) ||
                            s == "200"
                }
                else -> false
            }

            val message = when {
                item.has("message") && !item.isNull("message") -> item.optString("message")
                item.has("mensaje") && !item.isNull("mensaje") -> item.optString("mensaje")
                item.has("error") && !item.isNull("error") -> item.optString("error")
                else -> if (success) "Sincronizado correctamente" else "No se pudo sincronizar"
            }

            if (localId > 0) {
                resultados.add(
                    ResultadoSincronizacion(
                        localId = localId,
                        remotoId = remotoId,
                        success = success,
                        message = message
                    )
                )
            }
        }
    }

    val topLevelSuccess = parsedJson?.optBoolean("success", isHttp2xx) ?: isHttp2xx

    val overallSuccess = when {
        !isHttp2xx -> false
        jsonMalformed -> false
        itemsFieldTypeInvalid -> false
        rawItemsCount > 0 && resultados.isEmpty() -> false
        resultados.isNotEmpty() -> resultados.all {
            it.success && it.remotoId != null && it.remotoId > 0
        }
        else -> topLevelSuccess
    }
    val errorMsg = if (!overallSuccess) {
        val topMsg = when {
            parsedJson != null && parsedJson.has("message") && !parsedJson.isNull("message") -> parsedJson.optString("message")
            parsedJson != null && parsedJson.has("error") && !parsedJson.isNull("error") -> parsedJson.optString("error")
            parsedJson != null && parsedJson.has("mensaje") && !parsedJson.isNull("mensaje") -> parsedJson.optString("mensaje")
            else -> null
        }

        when {
            isHttp2xx && jsonMalformed -> "Error del servidor ($code): Respuesta malformada o JSON inválido"
            itemsFieldTypeInvalid -> "Error del servidor ($code): El campo 'items' tiene un tipo de dato incorrecto"
            rawItemsCount > 0 && resultados.isEmpty() -> "Error del servidor ($code): La respuesta contiene elementos inválidos o sin local_id válido"
            !topMsg.isNullOrEmpty() -> "Error del servidor ($code): $topMsg"
            resultados.isNotEmpty() -> {
                val firstFailed = resultados.firstOrNull { !it.success }?.message
                    ?: "Ningún registro del lote pudo sincronizarse"
                "Error del servidor ($code): $firstFailed"
            }
            else -> "Error del servidor ($code): $responseText"
        }
    } else {
        null
    }

    return SyncResponseResult(
        success = overallSuccess,
        resultados = resultados,
        error = errorMsg
    )
}
