package com.example.proyectofinalgrupo2

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

class TomarAsistenciaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTomarAsistenciaBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var apiClient: ApiClient
    private lateinit var adapter: AlumnoAsistenciaAdapter

    private var cursoActual: Curso? = null
    private var fechaSeleccionada: String = ""

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
        configurarDatosCurso()
        configurarFecha()
        configurarRecyclerView()
        configurarBotones()

        cargarAlumnos()
    }

    private fun configurarToolbar() {
        binding.toolbarTomarAsistencia.setNavigationOnClickListener {
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
        binding.btnMarcarTodosPresentes.setOnClickListener {
            adapter.marcarTodos("PRESENTE")
            Toast.makeText(this, "Todos los alumnos marcados como PRESENTE", Toast.LENGTH_SHORT).show()
        }

        binding.btnGuardarAsistencia.setOnClickListener {
            guardarAsistencias()
        }
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

    private fun guardarAsistencias() {
        val curso = cursoActual ?: return
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
        for (f in filas) {
            val asistencia = Asistencia(
                id = 0,
                alumnoId = f.alumno.id,
                alumnoCodigo = f.alumno.codigo,
                alumnoNombre = f.alumno.nombreCompleto,
                cursoId = curso.id,
                cursoCodigo = curso.codigo,
                cursoNombre = curso.nombre,
                fecha = fechaSeleccionada,
                estado = f.estado,
                observacion = f.observacion.trim(),
                sincronizado = false
            )

            val localId = dbHelper.insertarAsistencia(asistencia)
            if (localId > 0) {
                guardadosLocalCount++
                asistenciasParaGuardar.add(asistencia.copy(id = localId))
            }
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

                        AlertDialog.Builder(this)
                            .setTitle("Guardado Localmente")
                            .setMessage(
                                "Las $guardadosLocalCount asistencias se guardaron correctamente en SQLite, " +
                                        "pero ninguna pudo sincronizarse con el servidor.\n\n" +
                                        "Quedarán pendientes para una próxima sincronización."
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
}
