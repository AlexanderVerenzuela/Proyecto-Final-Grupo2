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
        Toast.makeText(this, "Iniciando sincronización...", Toast.LENGTH_SHORT).show()

        // 1. Obtener registros pendientes de SQLite
        val pendientes = dbHelper.listarPendientesSincronizacion()

        // 2. Enviar pendientes al servidor REST
        apiClient.sincronizarLote(pendientes) { successSync, totalSync, errSync ->
            if (successSync) {
                // Marcar en SQLite como sincronizados
                pendientes.forEach { item ->
                    dbHelper.marcarComoSincronizado(item.id, (item.remotoId ?: 1))
                }

                // 3. Descargar cursos actualizados del servidor
                apiClient.getCursos { successCursos, cursos, _ ->
                    if (successCursos && cursos.isNotEmpty()) {
                        dbHelper.guardarCursosCache(cursos)
                    }

                    // 4. Descargar alumnos actualizados del servidor
                    apiClient.getAlumnos { successAlumnos, alumnos, _ ->
                        if (successAlumnos && alumnos.isNotEmpty()) {
                            dbHelper.guardarAlumnosCache(alumnos)
                        }

                        binding.progressBarMain.visibility = View.GONE
                        actualizarContadoresLocales()
                        comprobarEstadoServidor()

                        AlertDialog.Builder(this)
                            .setTitle("Sincronización Completada")
                            .setMessage("Se sincronizaron $totalSync registro(s) con el servidor PostgreSQL y se actualizaron los catálogos locales.")
                            .setPositiveButton("Aceptar", null)
                            .show()
                    }
                }
            } else {
                binding.progressBarMain.visibility = View.GONE
                actualizarContadoresLocales()

                AlertDialog.Builder(this)
                    .setTitle("Aviso de Sincronización")
                    .setMessage("No se pudo conectar con el servidor ($errSync). Los registros permanecerán seguros en SQLite local hasta la próxima sincronización.")
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