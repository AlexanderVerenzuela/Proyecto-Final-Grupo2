package com.example.proyectofinalgrupo2

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.proyectofinalgrupo2.adapters.CursoAdapter
import com.example.proyectofinalgrupo2.api.ApiClient
import com.example.proyectofinalgrupo2.data.DatabaseHelper
import com.example.proyectofinalgrupo2.databinding.ActivitySeleccionCursoBinding
import com.example.proyectofinalgrupo2.models.Curso

class SeleccionCursoActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySeleccionCursoBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var apiClient: ApiClient
    private lateinit var adapter: CursoAdapter
    private var listaCursosCompleta: List<Curso> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySeleccionCursoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)
        apiClient = ApiClient.getInstance(this)

        configurarToolbar()
        configurarRecyclerView()
        configurarBuscador()

        cargarCursos()
    }

    private fun configurarToolbar() {
        binding.toolbarCursos.setNavigationOnClickListener {
            finish()
        }
    }

    private fun configurarRecyclerView() {
        adapter = CursoAdapter(emptyList()) { cursoSeleccionado ->
            val intent = Intent(this, TomarAsistenciaActivity::class.java).apply {
                putExtra("CURSO_EXTRA", cursoSeleccionado)
            }
            startActivity(intent)
        }
        binding.rvCursos.layoutManager = LinearLayoutManager(this)
        binding.rvCursos.adapter = adapter
    }

    private fun configurarBuscador() {
        binding.etBuscarCurso.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrarCursos(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun cargarCursos() {
        binding.progressBarCursos.visibility = View.VISIBLE
        binding.tvCursosVacios.visibility = View.GONE

        // Intento 1: Consumo real de la API REST (GET /api/cursos)
        apiClient.getCursos { success, cursosRemotos, error ->
            binding.progressBarCursos.visibility = View.GONE

            if (success && cursosRemotos.isNotEmpty()) {
                listaCursosCompleta = cursosRemotos
                // Guardar en SQLite como caché local
                dbHelper.guardarCursosCache(cursosRemotos)
                mostrarCursos(listaCursosCompleta)
            } else {
                // Fallback offline: Cargar de SQLite local
                val cursosLocales = dbHelper.listarCursosLocales()
                if (cursosLocales.isNotEmpty()) {
                    listaCursosCompleta = cursosLocales
                    mostrarCursos(listaCursosCompleta)
                    Toast.makeText(this, "Cargado desde SQLite (Modo Offline)", Toast.LENGTH_SHORT).show()
                } else {
                    binding.tvCursosVacios.visibility = View.VISIBLE
                    binding.tvCursosVacios.text = "No se encontraron cursos (${error ?: "Sin datos"})"
                }
            }
        }
    }

    private fun mostrarCursos(cursos: List<Curso>) {
        if (cursos.isEmpty()) {
            binding.tvCursosVacios.visibility = View.VISIBLE
        } else {
            binding.tvCursosVacios.visibility = View.GONE
        }
        adapter.actualizarLista(cursos)
    }

    private fun filtrarCursos(filtro: String) {
        if (filtro.trim().isEmpty()) {
            mostrarCursos(listaCursosCompleta)
            return
        }

        val filtrados = listaCursosCompleta.filter {
            it.nombre.contains(filtro, ignoreCase = true) ||
            it.codigo.contains(filtro, ignoreCase = true) ||
            it.docente.contains(filtro, ignoreCase = true)
        }
        mostrarCursos(filtrados)
    }
}
