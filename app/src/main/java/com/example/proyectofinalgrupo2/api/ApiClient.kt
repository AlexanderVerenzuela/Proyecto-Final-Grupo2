package com.example.proyectofinalgrupo2.api

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.example.proyectofinalgrupo2.models.Alumno
import com.example.proyectofinalgrupo2.models.Asistencia
import com.example.proyectofinalgrupo2.models.Curso
import com.example.proyectofinalgrupo2.models.SesionAsistencia
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
                        put("alumno_id", a.alumnoId)
                        put("curso_id", a.cursoId)
                        put("fecha", a.fecha)
                        put("estado", a.estado)
                        put("observacion", a.observacion)
                        put("fecha_registro", a.fechaRegistro)
                        put("sesion_id", a.sesionId)
                        put("remoto_id", a.remotoId ?: JSONObject.NULL)
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

                if (code in 200..299) {

                    val reader = BufferedReader(
                        InputStreamReader(conn.inputStream)
                    )

                    val response = reader.readText()
                    reader.close()

                    val json = JSONObject(response)
                    val itemsResponse = json.optJSONArray("items")

                    val resultados = mutableListOf<ResultadoSincronizacion>()

                    if (itemsResponse != null) {

                        for (i in 0 until itemsResponse.length()) {

                            val item = itemsResponse.getJSONObject(i)

                            val localId = item.optLong("local_id", -1L)

                            val remotoId = if (
                                item.has("remoto_id") &&
                                !item.isNull("remoto_id")
                            ) {
                                item.optInt("remoto_id")
                            } else {
                                null
                            }

                            val success = item.optBoolean(
                                "success",
                                false
                            )

                            val message = item.optString(
                                "message",
                                if (success) {
                                    "Sincronizado correctamente"
                                } else {
                                    "No se pudo sincronizar"
                                }
                            )

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

                    mainHandler.post {
                        callback(
                            true,
                            resultados,
                            null
                        )
                    }

                } else {

                    val errorStream =
                        conn.errorStream ?: conn.inputStream

                    val reader = BufferedReader(
                        InputStreamReader(errorStream)
                    )

                    val errResp = reader.readText()
                    reader.close()

                    mainHandler.post {
                        callback(
                            false,
                            emptyList(),
                            "Error del servidor ($code): $errResp"
                        )
                    }
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
                        InputStreamReader(errorStream)
                    )

                    val response = reader.readText()
                    reader.close()

                    mainHandler.post {
                        callback(
                            false,
                            "Error del servidor ($code): $response"
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

    // ================================================================
    // 6. SESIONES DE ASISTENCIA
    // ================================================================

    private fun parseSesion(obj: JSONObject): SesionAsistencia {
        return SesionAsistencia(
            id = obj.getInt("id"),
            cursoId = obj.getInt("curso_id"),
            fecha = obj.optString("fecha", ""),
            nombre = obj.optString("nombre", "Clase"),
            inicioAt = obj.optString("inicio_at", ""),
            finAt = obj.optString("fin_at", ""),
            estado = obj.optString("estado", "CERRADA"),
            segundosRestantes = obj.optInt("segundos_restantes", 0)
        )
    }

    // GET /api/sesiones/actual?curso_id=3&fecha=2026-10-18
    fun getSesionesActuales(
        cursoId: Int,
        fecha: String,
        callback: (success: Boolean, sesiones: List<SesionAsistencia>, error: String?) -> Unit
    ) {
        executor.execute {
            var conn: HttpURLConnection? = null

            try {
                val url = URL(
                    "${getBaseUrl()}/api/sesiones/actual" +
                            "?curso_id=$cursoId&fecha=$fecha"
                )

                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream
                }

                val response = stream?.bufferedReader()?.use { it.readText() } ?: ""

                if (code in 200..299) {
                    val json = JSONObject(response)
                    val array = json.optJSONArray("data") ?: JSONArray()
                    val sesiones = mutableListOf<SesionAsistencia>()

                    for (i in 0 until array.length()) {
                        sesiones.add(parseSesion(array.getJSONObject(i)))
                    }

                    mainHandler.post {
                        callback(true, sesiones, null)
                    }
                } else {
                    mainHandler.post {
                        callback(false, emptyList(), "Error HTTP $code: $response")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback(false, emptyList(), "Error de conexión: ${e.localizedMessage}")
                }
            } finally {
                conn?.disconnect()
            }
        }
    }

    // GET /api/sesiones/:id
    fun getSesion(
        sesionId: Int,
        callback: (success: Boolean, sesion: SesionAsistencia?, error: String?) -> Unit
    ) {
        executor.execute {
            var conn: HttpURLConnection? = null

            try {
                val url = URL("${getBaseUrl()}/api/sesiones/$sesionId")

                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream
                }

                val response = stream?.bufferedReader()?.use { it.readText() } ?: ""

                if (code in 200..299) {
                    val json = JSONObject(response)
                    val sesion = parseSesion(json.getJSONObject("data"))

                    mainHandler.post {
                        callback(true, sesion, null)
                    }
                } else {
                    mainHandler.post {
                        callback(false, null, "Error HTTP $code: $response")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback(false, null, "Error de conexión: ${e.localizedMessage}")
                }
            } finally {
                conn?.disconnect()
            }
        }
    }

    // POST /api/sesiones
    fun crearSesion(
        cursoId: Int,
        fecha: String,
        nombre: String,
        callback: (success: Boolean, sesion: SesionAsistencia?, error: String?) -> Unit
    ) {
        executor.execute {
            var conn: HttpURLConnection? = null

            try {
                val url = URL("${getBaseUrl()}/api/sesiones")

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

                val payload = JSONObject().apply {
                    put("curso_id", cursoId)
                    put("fecha", fecha)
                    put("nombre", nombre)
                }

                OutputStreamWriter(conn.outputStream, "UTF-8").use {
                    it.write(payload.toString())
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream
                }

                val response = stream?.bufferedReader()?.use { it.readText() } ?: ""

                if (code in 200..299) {
                    val json = JSONObject(response)
                    val sesion = parseSesion(json.getJSONObject("data"))

                    mainHandler.post {
                        callback(true, sesion, null)
                    }
                } else {
                    mainHandler.post {
                        callback(false, null, "Error HTTP $code: $response")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback(false, null, "Error de conexión: ${e.localizedMessage}")
                }
            } finally {
                conn?.disconnect()
            }
        }
    }

    // POST /api/sesiones/:id/cerrar
    fun cerrarSesion(
        sesionId: Int,
        callback: (success: Boolean, error: String?) -> Unit
    ) {
        executor.execute {
            var conn: HttpURLConnection? = null

            try {
                val url = URL(
                    "${getBaseUrl()}/api/sesiones/$sesionId/cerrar"
                )

                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                }

                conn.outputStream.use { it.flush() }

                val code = conn.responseCode
                val stream = if (code in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream
                }

                val response = stream?.bufferedReader()?.use { it.readText() } ?: ""

                if (code in 200..299) {
                    mainHandler.post {
                        callback(true, null)
                    }
                } else {
                    mainHandler.post {
                        callback(false, "Error HTTP $code: $response")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback(false, "Error de conexión: ${e.localizedMessage}")
                }
            } finally {
                conn?.disconnect()
            }
        }
    }

}
