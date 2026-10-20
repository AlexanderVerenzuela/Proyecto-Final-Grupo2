package com.example.proyectofinalgrupo2

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.proyectofinalgrupo2.adapters.AlumnoAsistenciaAdapter
import com.example.proyectofinalgrupo2.api.ApiClient
import com.example.proyectofinalgrupo2.data.DatabaseHelper
import com.example.proyectofinalgrupo2.databinding.ActivityTomarAsistenciaBinding
import com.example.proyectofinalgrupo2.models.Alumno
import com.example.proyectofinalgrupo2.models.Asistencia
import com.example.proyectofinalgrupo2.models.Curso
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.widget.EditText
import com.example.proyectofinalgrupo2.models.SesionAsistencia
import android.os.Handler
import android.os.Looper
import java.util.Date
import java.util.TimeZone
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONObject

class TomarAsistenciaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTomarAsistenciaBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var apiClient: ApiClient
    private lateinit var adapter: AlumnoAsistenciaAdapter

    private var cursoActual: Curso? = null
    private var fechaSeleccionada: String = ""

    private var sesionActual: SesionAsistencia? = null

    private val temporizadorHandler = Handler(Looper.getMainLooper())
    private var temporizadorRunnable: Runnable? = null
    private var finSesionMillis: Long = 0L
    private var sesionExpirada = false
    private var verificacionSesionRunnable: Runnable? = null
    private var asistenciaGuardada = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTomarAsistenciaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)
        apiClient = ApiClient.getInstance(this)

        @Suppress("DEPRECATION")
        cursoActual = intent.getSerializableExtra("CURSO_EXTRA") as? Curso

        if (cursoActual == null) {
            Toast.makeText(this, "Error: No se recibió información del curso", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        configurarToolbar()
        configurarManejoAtras()
        configurarDatosCurso()
        configurarFecha()
        configurarRecyclerView()
        configurarBotones()

        cargarAlumnos()
        cargarSesiones()
    }

    private fun configurarToolbar() {
        binding.toolbarTomarAsistencia.setNavigationOnClickListener {
            confirmarSalida()
        }
    }

    private fun configurarManejoAtras() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmarSalida()
            }
        })
    }

    private fun hayCambiosPendientes(): Boolean {
        if (asistenciaGuardada) return false
        if (sesionActual != null) return true
        val filas = if (::adapter.isInitialized) adapter.obtenerFilas() else emptyList()
        return filas.any { it.estado != "PRESENTE" || it.observacion.isNotBlank() }
    }

    private fun confirmarSalida() {
        if (hayCambiosPendientes()) {
            AlertDialog.Builder(this)
                .setTitle("¿Salir sin guardar?")
                .setMessage("Hay una sesión activa o cambios de asistencia no guardados en SQLite.\n\n¿Estás seguro de que deseas salir? Los datos no guardados se perderán.")
                .setPositiveButton("Salir") { _, _ ->
                    detenerTemporizador()
                    finish()
                }
                .setNegativeButton("Continuar aquí", null)
                .show()
        } else {
            detenerTemporizador()
            finish()
        }
    }

    private fun configurarDatosCurso() {
        cursoActual?.let { c ->
            binding.tvCursoTitulo.text = "${c.nombre} [${c.codigo}]"
            binding.tvCursoDocente.text = "Docente: ${c.docente} • Sec. ${c.seccion} • Ciclo ${c.ciclo}"
        }
    }

    private fun configurarFecha() {
        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        fechaSeleccionada = sdf.format(calendar.time)
        binding.tvFechaSeleccionada.text = fechaSeleccionada

        binding.layoutFecha.setOnClickListener {
            val y = calendar.get(Calendar.YEAR)
            val m = calendar.get(Calendar.MONTH)
            val d = calendar.get(Calendar.DAY_OF_MONTH)

            val dpd = DatePickerDialog(this, { _, year, monthOfYear, dayOfMonth ->
                calendar.set(year, monthOfYear, dayOfMonth)
                fechaSeleccionada = sdf.format(calendar.time)
                binding.tvFechaSeleccionada.text = fechaSeleccionada

                detenerTemporizador()
                sesionActual = null
                binding.tvTemporizadorSesion.text = "Sesión: sin seleccionar"
                binding.btnMarcarTodosPresentes.isEnabled = false
                binding.btnGuardarAsistencia.isEnabled = false

                cargarSesiones()
            }, y, m, d)
            dpd.show()
        }
    }

    private fun configurarRecyclerView() {
        adapter = AlumnoAsistenciaAdapter(emptyList())
        binding.rvAlumnosAsistencia.layoutManager = LinearLayoutManager(this)
        binding.rvAlumnosAsistencia.adapter = adapter
    }


    private fun configurarBotones() {
        binding.btnMarcarTodosPresentes.isEnabled = false
        binding.btnGuardarAsistencia.isEnabled = false

        binding.btnMarcarTodosPresentes.setOnClickListener {
            adapter.marcarTodos("PRESENTE")
            Toast.makeText(
                this,
                "Todos los alumnos marcados como PRESENTE",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnGuardarAsistencia.setOnClickListener {
            guardarAsistencias()

        }
        binding.btnResumen.setOnClickListener {
            mostrarResumenAsistencia()
        }

        binding.btnCerrarSesion.setOnClickListener {

            if (!hayConexion()) {
                Toast.makeText(
                    this,
                    "Necesitas conexión a internet para cerrar la sesión.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            val sesion = sesionActual

            if (sesion == null) {
                Toast.makeText(
                    this,
                    "No hay una sesión activa para cerrar.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Cerrar sesión")
                .setMessage(
                    "¿Estás seguro de que deseas cerrar la sesión \"${sesion.nombre}\"?\n\n" +
                            "Esta acción finalizará la sesión inmediatamente. " +
                            "No se podrán registrar nuevas asistencias ni modificar " +
                            "las existentes después del cierre."
                )
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Cerrar sesión") { _, _ ->
                    binding.btnCerrarSesion.isEnabled = false
                    binding.progressBarTomar.visibility = View.VISIBLE

                    apiClient.cerrarSesion(sesion.id) { success, error ->
                        binding.progressBarTomar.visibility = View.GONE

                        if (success) {
                            detenerTemporizador()
                            sesionActual = null
                            sesionExpirada = true

                            binding.tvTemporizadorSesion.text = "Sesión cerrada"
                            bloquearControlesSesion()
                            binding.btnCerrarSesion.visibility = View.GONE

                            Toast.makeText(
                                this,
                                "La sesión se cerró correctamente.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            binding.btnCerrarSesion.isEnabled = true

                            AlertDialog.Builder(this)
                                .setTitle("No se pudo cerrar la sesión")
                                .setMessage(error ?: "Error desconocido")
                                .setPositiveButton("Entendido", null)
                                .show()
                        }
                    }
                }
                .show()
        }
    }
    private fun hayConexion(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        ) && capabilities.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_VALIDATED
        )
    }

    private fun guardarSesionEnCache(sesion: SesionAsistencia) {
        val preferencias = getSharedPreferences(
            "sesion_asistencia_cache",
            Context.MODE_PRIVATE
        )

        val datos = JSONObject().apply {
            put("id", sesion.id)
            put("cursoId", sesion.cursoId)
            put("fecha", sesion.fecha)
            put("nombre", sesion.nombre)
            put("inicioAt", sesion.inicioAt)
            put("finAt", sesion.finAt)
            put("estado", sesion.estado)
            put("segundosRestantes", sesion.segundosRestantes)
            put("guardadaEn", System.currentTimeMillis())
        }

        preferencias.edit()
            .putString("${sesion.cursoId}_$fechaSeleccionada", datos.toString())
            .apply()
    }

    private fun recuperarSesionDeCache(
        cursoId: Int
    ): SesionAsistencia? {
        val preferencias = getSharedPreferences(
            "sesion_asistencia_cache",
            Context.MODE_PRIVATE
        )

        val clave = "${cursoId}_$fechaSeleccionada"
        val texto = preferencias.getString(clave, null) ?: return null

        return try {
            val datos = JSONObject(texto)

            val segundosGuardados = datos.optInt("segundosRestantes", 0)
            val guardadaEn = datos.optLong("guardadaEn", 0L)
            val transcurridos = (
                    (System.currentTimeMillis() - guardadaEn) / 1000L
                    ).coerceAtLeast(0L)

            val restantes = (segundosGuardados - transcurridos)
                .coerceAtLeast(0L)
                .toInt()

            val estado = datos.optString("estado", "CERRADA")

            if (
                !estado.equals("ABIERTA", ignoreCase = true) ||
                restantes <= 0
            ) {
                return null
            }

            SesionAsistencia(
                id = datos.getInt("id"),
                cursoId = datos.getInt("cursoId"),
                fecha = datos.optString("fecha", ""),
                nombre = datos.optString("nombre", "Clase"),
                inicioAt = datos.optString("inicioAt", ""),
                finAt = datos.optString("finAt", ""),
                estado = estado,
                segundosRestantes = restantes
            )
        } catch (_: Exception) {
            null
        }
    }
    private fun cargarSesiones() {
        val curso = cursoActual ?: return

        binding.progressBarTomar.visibility = View.VISIBLE
        binding.btnMarcarTodosPresentes.isEnabled = false
        binding.btnGuardarAsistencia.isEnabled = false

        apiClient.getSesionesActuales(curso.id, fechaSeleccionada) {
                success, sesiones, error ->

            binding.progressBarTomar.visibility = View.GONE

            if (!success) {
                val sesionGuardada = recuperarSesionDeCache(curso.id)

                if (sesionGuardada != null) {
                    seleccionarSesion(sesionGuardada)

                    Toast.makeText(
                        this,
                        "Sin conexión: se recuperó la sesión guardada. " +
                                "Las asistencias quedarán pendientes de sincronización.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    detenerTemporizador()
                    sesionActual = null
                    binding.tvTemporizadorSesion.text =
                        "Sin conexión y sin sesión disponible"
                    bloquearControlesSesion()

                    AlertDialog.Builder(this)
                        .setTitle("No se pudo comprobar la sesión")
                        .setMessage(
                            "No hay conexión y no existe una sesión guardada " +
                                    "que siga dentro del tiempo permitido. " +
                                    "Conéctate para consultar o iniciar una sesión."
                        )
                        .setPositiveButton("Reintentar") { _, _ ->
                            cargarSesiones()
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }

                return@getSesionesActuales
            }

            val sesionesActivas = sesiones.filter {
                it.estado.equals("ABIERTA", ignoreCase = true) &&
                        it.segundosRestantes > 0
            }

            if (sesionesActivas.isNotEmpty()) {
                val sesion = sesionesActivas.maxByOrNull { it.inicioAt }
                if (sesion != null) {
                    seleccionarSesion(sesion)
                }
            } else {
                crearSesionAutomatica(curso.id, sesiones.size + 1)
            }
        }
    }

    private fun crearSesionAutomatica(cursoId: Int, numeroSesion: Int) {
        binding.progressBarTomar.visibility = View.VISIBLE
        binding.btnMarcarTodosPresentes.isEnabled = false
        binding.btnGuardarAsistencia.isEnabled = false

        val nombreSesion = "Clase $numeroSesion"

        apiClient.crearSesion(
            cursoId = cursoId,
            fecha = fechaSeleccionada,
            nombre = nombreSesion
        ) { success, sesion, error ->
            binding.progressBarTomar.visibility = View.GONE

            if (success && sesion != null) {
                seleccionarSesion(sesion)
                Toast.makeText(
                    this,
                    "Se inició automáticamente la sesión $nombreSesion",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                detenerTemporizador()
                sesionActual = null
                binding.tvTemporizadorSesion.text = "No se pudo crear la sesión"
                binding.btnMarcarTodosPresentes.isEnabled = false
                binding.btnGuardarAsistencia.isEnabled = false

                AlertDialog.Builder(this)
                    .setTitle("No se pudo iniciar la clase")
                    .setMessage(
                        "La aplicación no pudo crear la sesión automáticamente. " +
                                "Comprueba la conexión e inténtalo de nuevo.\n\n" +
                                (error ?: "Error desconocido")
                    )
                    .setPositiveButton("Reintentar") { _, _ -> cargarSesiones() }
                    .setNegativeButton("Cancelar", null)
                    .setCancelable(true)
                    .show()
            }
        }
    }

    private fun seleccionarSesion(sesion: SesionAsistencia) {
        if (!sesion.estado.equals("ABIERTA", ignoreCase = true) ||
            sesion.segundosRestantes <= 0
        ) {
            detenerTemporizador()
            sesionActual = null
            binding.tvTemporizadorSesion.text = "Sesión: sin seleccionar"
            binding.btnMarcarTodosPresentes.isEnabled = false
            binding.btnGuardarAsistencia.isEnabled = false
            binding.btnCerrarSesion.visibility = View.GONE

            Toast.makeText(
                this,
                "Esta sesión ya no está activa. Selecciona otra.",
                Toast.LENGTH_LONG
            ).show()

            cargarSesiones()
            return
        }

        sesionActual = sesion
        guardarSesionEnCache(sesion)
        iniciarTemporizador(sesion)

        binding.btnMarcarTodosPresentes.isEnabled = true
        binding.btnGuardarAsistencia.isEnabled = true
        binding.btnCerrarSesion.visibility = View.VISIBLE
        binding.btnCerrarSesion.isEnabled = true

        Toast.makeText(
            this,
            "Sesión seleccionada: ${sesion.nombre}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun mostrarDialogoCrearSesion() {
        val entradaNombre = EditText(this).apply {
            hint = "Ej.: Clase, Recuperación, Laboratorio"
            setSingleLine(true)
            setText("Clase")
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Crear sesión")
            .setMessage(
                "La sesión tendrá una duración de tres horas desde su creación."
            )
            .setView(entradaNombre)
            .setPositiveButton("Crear") { _, _ ->
                val curso = cursoActual ?: return@setPositiveButton
                val nombre = entradaNombre.text.toString().trim()

                if (nombre.isBlank()) {
                    Toast.makeText(
                        this,
                        "Escribe un nombre para la sesión.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                binding.btnMarcarTodosPresentes.isEnabled = false
                binding.btnGuardarAsistencia.isEnabled = false
                binding.progressBarTomar.visibility = View.VISIBLE

                apiClient.crearSesion(
                    cursoId = curso.id,
                    fecha = fechaSeleccionada,
                    nombre = nombre
                ) { success, sesion, error ->

                    binding.progressBarTomar.visibility = View.GONE

                    if (success && sesion != null) {
                        seleccionarSesion(sesion)
                    } else {
                        detenerTemporizador()
                        sesionActual = null
                        binding.tvTemporizadorSesion.text = "Sesión: sin seleccionar"

                        Toast.makeText(
                            this,
                            "No se pudo crear la sesión: " +
                                    (error ?: "Error desconocido"),
                            Toast.LENGTH_LONG
                        ).show()

                        cargarSesiones()
                    }
                }
            }
            .setNegativeButton("Cancelar") { _, _ ->
                detenerTemporizador()
                sesionActual = null
                binding.tvTemporizadorSesion.text = "Sesión: sin seleccionar"
                binding.btnMarcarTodosPresentes.isEnabled = false
                binding.btnGuardarAsistencia.isEnabled = false
                binding.btnCerrarSesion.visibility = View.GONE
            }
            .setCancelable(false)
            .show()
    }

    private fun cargarAlumnos() {
        binding.progressBarTomar.visibility = View.VISIBLE
        binding.tvAlumnosVacios.visibility = View.GONE

        // Intento 1: Consumo real de la API REST (GET /api/alumnos)
        apiClient.getAlumnos { success, alumnosRemotos, error ->
            binding.progressBarTomar.visibility = View.GONE

            if (success && alumnosRemotos.isNotEmpty()) {
                dbHelper.guardarAlumnosCache(alumnosRemotos)
                adapter.actualizarAlumnos(alumnosRemotos)
            } else {
                // Fallback offline: Cargar desde SQLite local
                val alumnosLocales = dbHelper.listarAlumnosLocales()
                if (alumnosLocales.isNotEmpty()) {
                    adapter.actualizarAlumnos(alumnosLocales)
                    Toast.makeText(this, "Alumnos cargados desde SQLite (Offline)", Toast.LENGTH_SHORT).show()
                } else {
                    binding.tvAlumnosVacios.visibility = View.VISIBLE
                    binding.tvAlumnosVacios.text = "No hay alumnos disponibles (${error ?: "Sin datos"})"
                }
            }
        }
    }


    private fun mostrarResumenAsistencia() {

        val filas = adapter.obtenerFilas()

        if (filas.isEmpty()) {
            android.widget.Toast.makeText(
                this,
                "No hay alumnos para mostrar",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        val presentes = filas.count {
            it.estado == "PRESENTE"
        }

        val tardanzas = filas.count {
            it.estado == "TARDANZA"
        }

        val ausentes = filas.count {
            it.estado == "AUSENTE"
        }

        val vista = layoutInflater.inflate(
            R.layout.dialog_resumen_asistencia,
            null
        )

        val tvTotal = vista.findViewById<
                android.widget.TextView
                >(R.id.tvTotalResumen)

        val tvPresentes = vista.findViewById<
                android.widget.TextView
                >(R.id.tvPresentesResumen)

        val tvTardanzas = vista.findViewById<
                android.widget.TextView
                >(R.id.tvTardanzasResumen)

        val tvAusentes = vista.findViewById<
                android.widget.TextView
                >(R.id.tvAusentesResumen)

        tvTotal.text = "Total de alumnos: ${filas.size}"
        tvPresentes.text = "✓  Presentes: $presentes"
        tvTardanzas.text = "◷  Tardanzas: $tardanzas"
        tvAusentes.text = "✕  Ausentes: $ausentes"

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(vista)
            .setPositiveButton("Aceptar", null)
            .show()
    }


    private fun guardarAsistencias() {
        val curso = cursoActual ?: return
        val sesion = sesionActual


        if (sesionExpirada) {
            bloquearControlesSesion()

            Toast.makeText(
                this,
                "La sesión ha expirado. Selecciona o crea otra sesión.",
                Toast.LENGTH_LONG
            ).show()

            cargarSesiones()
            return
        }

        if (sesion == null ||
            !sesion.estado.equals("ABIERTA", ignoreCase = true) ||
            sesion.segundosRestantes <= 0
        ) {
            Toast.makeText(
                this,
                "Selecciona una sesión activa antes de guardar.",
                Toast.LENGTH_LONG
            ).show()

            cargarSesiones()
            return
        }

        val filas = adapter.obtenerFilas()

        if (filas.isEmpty()) {
            Toast.makeText(this, "No hay estudiantes en la lista para registrar", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnGuardarAsistencia.isEnabled = false
        binding.progressBarTomar.visibility = View.VISIBLE

        val asistenciasParaGuardar = mutableListOf<Asistencia>()
        var guardadosLocalCount = 0

        // 1. Guardar primero en SQLite local garantizando que no se pierdan datos
        val formatoFechaRegistro = SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        for (f in filas) {
            val fechaRegistro = formatoFechaRegistro.format(Date())

            val asistencia = Asistencia(
                id = 0,
                alumnoId = f.alumno.id,
                alumnoCodigo = f.alumno.codigo,
                alumnoNombre = f.alumno.nombreCompleto,
                cursoId = curso.id,
                cursoCodigo = curso.codigo,
                cursoNombre = curso.nombre,
                fecha = fechaSeleccionada,
                sesionId = sesion.id,
                estado = f.estado,
                observacion = f.observacion.trim(),
                sincronizado = false,
                fechaRegistro = fechaRegistro
            )

            val localId = dbHelper.insertarAsistencia(asistencia)

            if (localId > 0) {
                guardadosLocalCount++
                asistenciasParaGuardar.add(
                    asistencia.copy(id = localId)
                )
            }
        }

        if (guardadosLocalCount > 0) {
            asistenciaGuardada = true
        }

        // 2. Intentar sincronización inmediata con la API REST si hay conexión
        apiClient.sincronizarLote(asistenciasParaGuardar) { successSync, resultados, errSync ->

            binding.progressBarTomar.visibility = View.GONE
            binding.btnGuardarAsistencia.isEnabled = true

            if (successSync) {

                val exitosos = resultados.filter { it.success }
                val fallidos = resultados.filter { !it.success }

                // Marcar solamente los registros confirmados por el servidor
                exitosos.forEach { resultado ->

                    if (resultado.remotoId != null) {
                        dbHelper.marcarComoSincronizado(
                            resultado.localId,
                            resultado.remotoId
                        )
                    }
                }

                when {

                    fallidos.isEmpty() -> {

                        AlertDialog.Builder(this)
                            .setTitle("¡Registro Exitoso!")
                            .setMessage(
                                "Se registraron $guardadosLocalCount asistencias " +
                                        "y todas fueron sincronizadas correctamente con PostgreSQL."
                            )
                            .setCancelable(false)
                            .setPositiveButton("Finalizar") { _, _ ->
                                finish()
                            }
                            .show()
                    }

                    exitosos.isNotEmpty() -> {

                        AlertDialog.Builder(this)
                            .setTitle("Registro parcialmente sincronizado")
                            .setMessage(
                                "Se guardaron $guardadosLocalCount asistencias localmente.\n\n" +
                                        "${exitosos.size} fueron sincronizadas correctamente.\n" +
                                        "${fallidos.size} quedaron pendientes de sincronización.\n\n" +
                                        "Puedes sincronizarlas posteriormente desde Gestión Local."
                            )
                            .setCancelable(false)
                            .setPositiveButton("Finalizar") { _, _ ->
                                finish()
                            }
                            .show()
                    }


                    else -> {
                        val motivos = fallidos
                            .take(3)
                            .joinToString("\n") { resultado ->
                                "• ${resultado.message}"
                            }

                        AlertDialog.Builder(this)
                            .setTitle("Guardado Localmente")
                            .setMessage(
                                "Las $guardadosLocalCount asistencias se guardaron " +
                                        "en SQLite, pero ninguna pudo sincronizarse.\n\n" +
                                        "Motivos informados por el servidor:\n" +
                                        (motivos.ifBlank {
                                            "El servidor no proporcionó detalles."
                                        })
                            )
                            .setCancelable(false)
                            .setPositiveButton("Entendido") { _, _ ->
                                finish()
                            }
                            .show()
                    }
                }

            } else {

                AlertDialog.Builder(this)
                    .setTitle("Guardado Localmente en SQLite")
                    .setMessage(
                        "Se guardaron $guardadosLocalCount asistencias en la base de datos local SQLite.\n\n" +
                                "El servidor no respondió:\n$errSync\n\n" +
                                "Todas permanecerán pendientes de sincronización."
                    )
                    .setCancelable(false)
                    .setPositiveButton("Entendido") { _, _ ->
                        finish()
                    }
                    .show()
            }
        }
    }

    private fun iniciarTemporizador(sesion: SesionAsistencia) {
        detenerTemporizador()

        finSesionMillis = System.currentTimeMillis() +
                (sesion.segundosRestantes.coerceAtLeast(0) * 1000L)

        sesionExpirada = false
        actualizarTemporizador()
        programarVerificacionSesion(sesion.id)
    }

    private fun detenerTemporizador() {
        temporizadorRunnable?.let {
            temporizadorHandler.removeCallbacks(it)
        }
        temporizadorRunnable = null

        verificacionSesionRunnable?.let {
            temporizadorHandler.removeCallbacks(it)
        }
        verificacionSesionRunnable = null
    }

    private fun bloquearControlesSesion() {
        binding.btnMarcarTodosPresentes.isEnabled = false
        binding.btnGuardarAsistencia.isEnabled = false
        binding.btnCerrarSesion.visibility = View.GONE
    }

    private fun programarVerificacionSesion(sesionId: Int) {
        verificacionSesionRunnable?.let {
            temporizadorHandler.removeCallbacks(it)
        }

        verificacionSesionRunnable = Runnable {
            if (sesionActual?.id != sesionId) {
                return@Runnable
            }

            apiClient.getSesion(sesionId) { success, sesionServidor, _ ->
                if (sesionActual?.id != sesionId) {
                    return@getSesion
                }

                if (!success || sesionServidor == null) {
                    // Si el servidor no responde, vuelve a comprobarlo en 15 segundos.
                    programarVerificacionSesion(sesionId)
                    return@getSesion
                }

                val sigueActiva =
                    sesionServidor.estado.equals("ABIERTA", ignoreCase = true) &&
                            sesionServidor.segundosRestantes > 0


                if (!sigueActiva) {
                    detenerTemporizador()
                    sesionActual = null
                    sesionExpirada = true

                    binding.tvTemporizadorSesion.text = "Sesión cerrada o expirada"
                    bloquearControlesSesion()

                    Toast.makeText(
                        this@TomarAsistenciaActivity,
                        "La sesión ya no está activa. Selecciona otra sesión.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@getSesion
                }

                // Sincroniza el contador local con el tiempo que informa el servidor.
                finSesionMillis = System.currentTimeMillis() +
                        (sesionServidor.segundosRestantes * 1000L)
                sesionActual = sesionServidor
                programarVerificacionSesion(sesionId)
            }
        }

        temporizadorHandler.postDelayed(verificacionSesionRunnable!!, 15_000L)
    }

    private fun actualizarTemporizador() {
        val restanteMillis = (finSesionMillis - System.currentTimeMillis())
            .coerceAtLeast(0L)

        val segundos = restanteMillis / 1000L
        val horas = segundos / 3600L
        val minutos = (segundos % 3600L) / 60L
        val segundosFinales = segundos % 60L

        val sesion = sesionActual

        if (sesion == null) {
            binding.tvTemporizadorSesion.text = "Sesión: sin seleccionar"
            return
        }

        binding.tvTemporizadorSesion.text = String.format(
            Locale.getDefault(),
            "Sesión: %s | Tiempo restante: %02d:%02d:%02d",
            sesion.nombre,
            horas,
            minutos,
            segundosFinales
        )

        if (restanteMillis <= 0L) {
            detenerTemporizador()
            sesionExpirada = true
            sesionActual = null

            binding.tvTemporizadorSesion.text = "Sesión expirada"
            bloquearControlesSesion()

            Toast.makeText(
                this,
                "La sesión ha expirado. Selecciona o crea otra sesión.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        temporizadorRunnable = Runnable {
            actualizarTemporizador()
        }

        temporizadorHandler.postDelayed(temporizadorRunnable!!, 1000L)
    }

    override fun onDestroy() {
        detenerTemporizador()
        super.onDestroy()
    }
}
