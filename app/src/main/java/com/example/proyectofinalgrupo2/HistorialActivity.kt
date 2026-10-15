package com.example.proyectofinalgrupo2

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.proyectofinalgrupo2.adapters.LocalAsistenciaAdapter
import com.example.proyectofinalgrupo2.api.ApiClient
import com.example.proyectofinalgrupo2.data.DatabaseHelper
import com.example.proyectofinalgrupo2.databinding.ActivityHistorialBinding
import com.example.proyectofinalgrupo2.models.Asistencia
import com.example.proyectofinalgrupo2.models.Curso
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HistorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistorialBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var apiClient: ApiClient
    private lateinit var adapter: LocalAsistenciaAdapter

    private var cursosDisponibles: List<Curso> = emptyList()
    private var cursoFiltroId: Int? = null
    private var fechaFiltro: String? = null
    private var consultarRemoto: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistorialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)
        apiClient = ApiClient.getInstance(this)

        configurarToolbar()
        configurarRecyclerView()
        configurarFiltros()

        cargarCursosParaFiltro()
    }

    private fun configurarToolbar() {
        binding.toolbarHistorial.setNavigationOnClickListener {
            finish()
        }
    }

    private fun configurarRecyclerView() {
        adapter = LocalAsistenciaAdapter(
            emptyList(),
            onEditarClick = { item ->
                Toast.makeText(this, "Para modificar, use la pantalla de Gestión Local SQLite", Toast.LENGTH_SHORT).show()
            },
            onEliminarClick = { item ->
                Toast.makeText(this, "Para eliminar, use la pantalla de Gestión Local SQLite", Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvHistorial.layoutManager = LinearLayoutManager(this)
        binding.rvHistorial.adapter = adapter
    }

    private fun configurarFiltros() {
        // Switch de origen de datos: Servidor vs SQLite
        binding.switchOrigenDatos.setOnCheckedChangeListener { _, isChecked ->
            consultarRemoto = isChecked
            binding.switchOrigenDatos.text = if (isChecked) "Servidor REST" else "SQLite Local"
            cargarHistorial()
        }

        // Filtro de Fecha
        binding.btnFiltroFecha.setOnClickListener {
            val calendar = Calendar.getInstance()
            val y = calendar.get(Calendar.YEAR)
            val m = calendar.get(Calendar.MONTH)
            val d = calendar.get(Calendar.DAY_OF_MONTH)

            val dpd = DatePickerDialog(this, { _, year, monthOfYear, dayOfMonth ->
                calendar.set(year, monthOfYear, dayOfMonth)
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                fechaFiltro = sdf.format(calendar.time)
                binding.btnFiltroFecha.text = "📅 $fechaFiltro"
                cargarHistorial()
            }, y, m, d)
            dpd.show()
        }

        // Botón Limpiar Filtros
        binding.btnLimpiarFiltros.setOnClickListener {
            cursoFiltroId = null
            fechaFiltro = null
            binding.btnFiltroFecha.text = "📅 Todas las fechas"
            if (cursosDisponibles.isNotEmpty()) {
                binding.spFiltroCurso.setSelection(0)
            }
            cargarHistorial()
        }
    }

    private fun cargarCursosParaFiltro() {
        // Cargar cursos desde SQLite local o API
        val cursosLocales = dbHelper.listarCursosLocales()
        if (cursosLocales.isNotEmpty()) {
            configurarSpinnerCursos(cursosLocales)
            cargarHistorial()
        } else {
            apiClient.getCursos { success, cursosRemotos, _ ->
                if (success && cursosRemotos.isNotEmpty()) {
                    dbHelper.guardarCursosCache(cursosRemotos)
                    configurarSpinnerCursos(cursosRemotos)
                }
                cargarHistorial()
            }
        }
    }

    private fun configurarSpinnerCursos(cursos: List<Curso>) {
        cursosDisponibles = cursos
        val nombres = mutableListOf("Todos los cursos")
        nombres.addAll(cursos.map { "${it.codigo} - ${it.nombre}" })

        val spinnerAdapter = ArrayAdapter(this, R.layout.spinner_item_dark, nombres)
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_item_dark)
        binding.spFiltroCurso.adapter = spinnerAdapter

        binding.spFiltroCurso.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                cursoFiltroId = if (position == 0) null else cursosDisponibles[position - 1].id
                cargarHistorial()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun cargarHistorial() {
        binding.progressBarHistorial.visibility = View.VISIBLE
        binding.tvHistorialVacio.visibility = View.GONE
        binding.tvHistorialContador.text = "Consultando asistencias..."

        if (consultarRemoto) {
            // Consumo Real de la API REST: GET /api/asistencias
            apiClient.getAsistencias(cursoId = cursoFiltroId, fecha = fechaFiltro) { success, lista, error ->
                binding.progressBarHistorial.visibility = View.GONE

                if (success) {
                    mostrarResultados(lista, "del servidor REST")
                } else {
                    // Si falla el servidor, ofrecer fallback automático a SQLite
                    binding.tvHistorialContador.text = "Error al conectar con servidor ($error)"
                    val locales = filtrarLocales()
                    mostrarResultados(locales, "en SQLite local (Modo Offline)")
                }
            }
        } else {
            // Consulta directa a la base de datos local SQLite
            binding.progressBarHistorial.visibility = View.GONE
            val locales = filtrarLocales()
            mostrarResultados(locales, "en SQLite local")
        }
    }

    private fun filtrarLocales(): List<Asistencia> {
        var lista = dbHelper.listarAsistencias()

        cursoFiltroId?.let { cId ->
            lista = lista.filter { it.cursoId == cId }
        }

        fechaFiltro?.let { f ->
            lista = lista.filter { it.fecha == f }
        }

        return lista
    }

    private fun mostrarResultados(lista: List<Asistencia>, fuente: String) {
        if (lista.isEmpty()) {
            binding.tvHistorialVacio.visibility = View.VISIBLE
            binding.tvHistorialContador.text = "0 registros encontrados $fuente"
        } else {
            binding.tvHistorialVacio.visibility = View.GONE
            binding.tvHistorialContador.text = "Mostrando ${lista.size} registro(s) $fuente"
        }
        adapter.actualizarLista(lista)
    }
}
