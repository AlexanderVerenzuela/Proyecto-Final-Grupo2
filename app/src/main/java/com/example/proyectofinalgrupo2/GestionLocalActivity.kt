package com.example.proyectofinalgrupo2

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.proyectofinalgrupo2.adapters.LocalAsistenciaAdapter
import com.example.proyectofinalgrupo2.api.ApiClient
import com.example.proyectofinalgrupo2.data.DatabaseHelper
import com.example.proyectofinalgrupo2.databinding.ActivityGestionLocalBinding
import com.example.proyectofinalgrupo2.models.Asistencia

class GestionLocalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGestionLocalBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var apiClient: ApiClient
    private lateinit var adapter: LocalAsistenciaAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGestionLocalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)
        apiClient = ApiClient.getInstance(this)

        configurarToolbar()
        configurarRecyclerView()
        configurarBuscador()
        configurarBotonSincronizar()

        cargarRegistrosLocales()
    }

    override fun onResume() {
        super.onResume()
        cargarRegistrosLocales()
    }

    private fun configurarToolbar() {
        binding.toolbarGestionLocal.setNavigationOnClickListener {
            finish()
        }
    }

    private fun configurarRecyclerView() {
        adapter = LocalAsistenciaAdapter(
            emptyList(),
            onEditarClick = { asistencia ->
                mostrarDialogoEditar(asistencia)
            },
            onEliminarClick = { asistencia ->
                mostrarDialogoEliminar(asistencia)
            }
        )
        binding.rvLocalAsistencias.layoutManager = LinearLayoutManager(this)
        binding.rvLocalAsistencias.adapter = adapter
    }

    private fun configurarBuscador() {
        binding.etBuscarLocal.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty()) {
                    cargarRegistrosLocales()
                } else {
                    val resultados = dbHelper.buscarAsistencias(query)
                    actualizarListaUI(resultados)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun configurarBotonSincronizar() {
        binding.btnSincronizarLocal.setOnClickListener {
            sincronizarPendientes()
        }
    }

    private fun cargarRegistrosLocales() {
        val lista = dbHelper.listarAsistencias()
        actualizarListaUI(lista)
    }

    private fun actualizarListaUI(lista: List<Asistencia>) {
        val (_, pendientes) = dbHelper.obtenerContadores()
        binding.tvPendientesTexto.text = "$pendientes registro(s) pendiente(s) de sincronización"

        if (lista.isEmpty()) {
            binding.tvLocalVacio.visibility = View.VISIBLE
        } else {
            binding.tvLocalVacio.visibility = View.GONE
        }
        adapter.actualizarLista(lista)
    }

    // =================================================================
    // OPERACIÓN SQLITE: MODIFICAR REGISTRO (UPDATE)
    // =================================================================
    private fun mostrarDialogoEditar(asistencia: Asistencia) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_editar_asistencia, null)
        val rgEstado = view.findViewById<RadioGroup>(R.id.rgDialogEstado)
        val rbPresente = view.findViewById<RadioButton>(R.id.rbDialogPresente)
        val rbTardanza = view.findViewById<RadioButton>(R.id.rbDialogTardanza)
        val rbAusente = view.findViewById<RadioButton>(R.id.rbDialogAusente)
        val etObs = view.findViewById<EditText>(R.id.etDialogObs)

        when (asistencia.estado.uppercase()) {
            "PRESENTE" -> rbPresente.isChecked = true
            "TARDANZA" -> rbTardanza.isChecked = true
            "AUSENTE" -> rbAusente.isChecked = true
        }
        etObs.setText(asistencia.observacion)

        AlertDialog.Builder(this)
            .setTitle("Modificar Asistencia #${asistencia.id}")
            .setMessage("Estudiante: ${asistencia.alumnoNombre}\nFecha: ${asistencia.fecha}")
            .setView(view)
            .setPositiveButton("Actualizar") { _, _ ->
                val nuevoEstado = when (rgEstado.checkedRadioButtonId) {
                    R.id.rbDialogPresente -> "PRESENTE"
                    R.id.rbDialogTardanza -> "TARDANZA"
                    R.id.rbDialogAusente -> "AUSENTE"
                    else -> "PRESENTE"
                }
                val nuevaObs = etObs.text.toString().trim()

                // Actualizar directamente en SQLite
                val rowsAffected = dbHelper.actualizarAsistencia(asistencia.id, nuevoEstado, nuevaObs)
                if (rowsAffected > 0) {
                    Toast.makeText(this, "Registro actualizado en SQLite", Toast.LENGTH_SHORT).show()
                    cargarRegistrosLocales()
                } else {
                    Toast.makeText(this, "Error al actualizar en SQLite", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // =================================================================
    // OPERACIÓN SQLITE: ELIMINAR REGISTRO (DELETE)
    // =================================================================
    private fun mostrarDialogoEliminar(asistencia: Asistencia) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Desea eliminar el registro local #${asistencia.id} de ${asistencia.alumnoNombre} del día ${asistencia.fecha}?\n\nEsta operación modificará la base de datos SQLite.")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Eliminar") { _, _ ->

                // Si el registro ya existe en PostgreSQL,
                // primero debemos eliminarlo del servidor.
                if (asistencia.remotoId != null) {

                    binding.progressBarLocal.visibility = View.VISIBLE

                    apiClient.eliminarAsistenciaRemota(
                        asistencia.remotoId
                    ) { success, error ->

                        binding.progressBarLocal.visibility = View.GONE

                        if (success) {

                            val rows = dbHelper.eliminarAsistencia(asistencia.id)

                            if (rows > 0) {
                                Toast.makeText(
                                    this,
                                    "Asistencia eliminada correctamente.",
                                    Toast.LENGTH_SHORT
                                ).show()

                                cargarRegistrosLocales()
                            } else {
                                Toast.makeText(
                                    this,
                                    "Se eliminó del servidor, pero no del almacenamiento local.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                        } else {

                            AlertDialog.Builder(this)
                                .setTitle("No se pudo eliminar")
                                .setMessage(
                                    "La asistencia no se eliminó porque el servidor no confirmó la operación.\n\n" +
                                            "$error"
                                )
                                .setPositiveButton("Entendido", null)
                                .show()
                        }
                    }

                } else {

                    // Nunca llegó al servidor: solo existe localmente.
                    val rows = dbHelper.eliminarAsistencia(asistencia.id)

                    if (rows > 0) {
                        Toast.makeText(
                            this,
                            "Registro local eliminado.",
                            Toast.LENGTH_SHORT
                        ).show()

                        cargarRegistrosLocales()
                    } else {
                        Toast.makeText(
                            this,
                            "No se pudo eliminar el registro.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // =================================================================
    // SINCRONIZACIÓN DE PENDIENTES CON EL SERVIDOR
    // =================================================================
    private fun sincronizarPendientes() {

        val pendientes = dbHelper.listarPendientesSincronizacion()

        if (pendientes.isEmpty()) {
            Toast.makeText(
                this,
                "No hay registros pendientes de sincronización",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        binding.progressBarLocal.visibility = View.VISIBLE
        binding.btnSincronizarLocal.isEnabled = false

        apiClient.sincronizarLote(pendientes) { success, resultados, error ->

            binding.progressBarLocal.visibility = View.GONE
            binding.btnSincronizarLocal.isEnabled = true

            if (success) {

                val exitosos = resultados.filter { it.success }
                val fallidos = resultados.filter { !it.success }

                // Marcar SOLO los registros confirmados por PostgreSQL
                exitosos.forEach { resultado ->

                    if (resultado.remotoId != null) {
                        dbHelper.marcarComoSincronizado(
                            resultado.localId,
                            resultado.remotoId
                        )
                    }
                }

                cargarRegistrosLocales()

                when {
                    fallidos.isEmpty() -> {
                        Toast.makeText(
                            this,
                            "Se sincronizaron ${exitosos.size} registros correctamente con PostgreSQL.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    exitosos.isNotEmpty() -> {
                        Toast.makeText(
                            this,
                            "${exitosos.size} sincronizados. ${fallidos.size} quedaron pendientes.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    else -> {
                        AlertDialog.Builder(this)
                            .setTitle("Sincronización no realizada")
                            .setMessage(
                                "Ningún registro pudo sincronizarse.\n\n" +
                                        "Los registros siguen guardados localmente."
                            )
                            .setPositiveButton("Entendido", null)
                            .show()
                    }
                }

            } else {

                AlertDialog.Builder(this)
                    .setTitle("Error de Sincronización")
                    .setMessage(
                        "No se pudo conectar con el servidor:\n\n" +
                                "$error\n\n" +
                                "Los registros siguen guardados en SQLite local."
                    )
                    .setPositiveButton("Entendido", null)
                    .show()
            }
        }
    }
}
