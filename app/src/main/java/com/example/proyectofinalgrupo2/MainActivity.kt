package com.example.proyectofinalgrupo2

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.proyectofinalgrupo2.api.ApiClient
import com.example.proyectofinalgrupo2.data.DatabaseHelper
import com.example.proyectofinalgrupo2.databinding.ActivityMainBinding
import com.example.proyectofinalgrupo2.api.ResultadoSincronizacion

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var apiClient: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)
        apiClient = ApiClient.getInstance(this)

        // Configuración de OnClickListener en onCreate según los estándares de Cibertec
        binding.cardTomarAsistencia.setOnClickListener {
            val intent = Intent(this, SeleccionCursoActivity::class.java)
            startActivity(intent)
        }

        binding.cardGestionLocal.setOnClickListener {
            val intent = Intent(this, GestionLocalActivity::class.java)
            startActivity(intent)
        }

        binding.cardHistorial.setOnClickListener {
            val intent = Intent(this, HistorialActivity::class.java)
            startActivity(intent)
        }

        binding.cardSincronizar.setOnClickListener {
            ejecutarSincronizacionCompleta()
        }

        binding.btnVerificarConexion.setOnClickListener {
            comprobarEstadoServidor()
        }

        binding.btnConfigUrl.setOnClickListener {
            mostrarDialogoConfigUrl()
        }
    }

    override fun onResume() {
        super.onResume()
        actualizarContadoresLocales()
        comprobarEstadoServidor()
    }

    private fun actualizarContadoresLocales() {
        val (total, pendientes) = dbHelper.obtenerContadores()
        binding.tvTotalAsistenciasLocal.text = total.toString()
        binding.tvPendientesSyncLocal.text = pendientes.toString()
    }

    private fun comprobarEstadoServidor() {
        binding.tvServerStatus.text = "Verificando conexión..."
        binding.tvStatusDot.setTextColor(ContextCompat.getColor(this, R.color.text_muted))

        apiClient.checkHealth { isOnline, message ->
            if (isOnline) {
                binding.tvServerStatus.text = "Servidor en línea (PostgreSQL)"
                binding.tvStatusDot.setTextColor(ContextCompat.getColor(this, R.color.color_presente))
            } else {
                binding.tvServerStatus.text = "Modo Local Offline (SQLite activo)"
                binding.tvStatusDot.setTextColor(ContextCompat.getColor(this, R.color.color_ausente))
            }
        }
    }

    private fun ejecutarSincronizacionCompleta() {
        binding.progressBarMain.visibility = View.VISIBLE

        Toast.makeText(
            this,
            "Iniciando sincronización...",
            Toast.LENGTH_SHORT
        ).show()

        val pendientes = dbHelper.listarPendientesSincronizacion()

        if (pendientes.isEmpty()) {
            binding.progressBarMain.visibility = View.GONE

            Toast.makeText(
                this,
                "No hay registros pendientes de sincronización.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        apiClient.sincronizarLote(pendientes) { successSync, resultados, errSync ->

            if (successSync) {

                val exitosos = resultados.filter { it.success }
                val fallidos = resultados.filter { !it.success }

                // Marcar SOLO los registros confirmados por el servidor
                exitosos.forEach { resultado ->

                    if (resultado.remotoId != null) {
                        dbHelper.marcarComoSincronizado(
                            resultado.localId,
                            resultado.remotoId
                        )
                    }
                }

                // Actualizar catálogos locales
                apiClient.getCursos { successCursos, cursos, _ ->

                    if (successCursos && cursos.isNotEmpty()) {
                        dbHelper.guardarCursosCache(cursos)
                    }

                    apiClient.getAlumnos { successAlumnos, alumnos, _ ->

                        if (successAlumnos && alumnos.isNotEmpty()) {
                            dbHelper.guardarAlumnosCache(alumnos)
                        }

                        binding.progressBarMain.visibility = View.GONE

                        actualizarContadoresLocales()
                        comprobarEstadoServidor()

                        val mensaje = when {
                            fallidos.isEmpty() -> {
                                "Se sincronizaron ${exitosos.size} registro(s) correctamente con PostgreSQL.\n\n" +
                                        "Los catálogos locales también fueron actualizados."
                            }

                            exitosos.isNotEmpty() -> {
                                "${exitosos.size} registro(s) se sincronizaron correctamente.\n\n" +
                                        "${fallidos.size} registro(s) quedaron pendientes y podrán intentarse nuevamente."
                            }

                            else -> {
                                "Ningún registro pudo sincronizarse.\n\n" +
                                        "Los registros permanecen guardados localmente."
                            }
                        }

                        AlertDialog.Builder(this)
                            .setTitle(
                                if (fallidos.isEmpty()) {
                                    "Sincronización completada"
                                } else {
                                    "Sincronización parcial"
                                }
                            )
                            .setMessage(mensaje)
                            .setPositiveButton("Aceptar", null)
                            .show()
                    }
                }

            } else {

                binding.progressBarMain.visibility = View.GONE

                actualizarContadoresLocales()

                AlertDialog.Builder(this)
                    .setTitle("Aviso de Sincronización")
                    .setMessage(
                        "No se pudo conectar con el servidor.\n\n" +
                                "$errSync\n\n" +
                                "Los registros permanecen guardados en SQLite local y se podrán sincronizar posteriormente."
                    )
                    .setPositiveButton("Entendido", null)
                    .show()
            }
        }
    }

    private fun mostrarDialogoConfigUrl() {
        val currentUrl = apiClient.getBaseUrl()
        val input = EditText(this).apply {
            setText(currentUrl)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_muted))
            hint = "http://10.0.2.2:3000 o IP local"
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("Configurar URL de la API")
            .setMessage("Usa 10.0.2.2 para emulador o tu IP local (ej. http://192.168.1.50:3000) si usas celular físico:")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val nuevaUrl = input.text.toString().trim()
                if (nuevaUrl.isNotEmpty()) {
                    apiClient.setBaseUrl(nuevaUrl)
                    Toast.makeText(this, "URL actualizada", Toast.LENGTH_SHORT).show()
                    comprobarEstadoServidor()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}