package com.example.proyectofinalgrupo2.adapters

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.proyectofinalgrupo2.R
import com.example.proyectofinalgrupo2.databinding.ItemAlumnoAsistenciaBinding
import com.example.proyectofinalgrupo2.models.Alumno

data class FilaAsistencia(
    val alumno: Alumno,
    var estado: String = "PRESENTE",
    var observacion: String = ""
)

class AlumnoAsistenciaAdapter(
    alumnos: List<Alumno>
) : RecyclerView.Adapter<AlumnoAsistenciaAdapter.AlumnoViewHolder>() {

    private val filas: MutableList<FilaAsistencia> = alumnos.map {
        FilaAsistencia(alumno = it, estado = "PRESENTE", observacion = "")
    }.toMutableList()

    inner class AlumnoViewHolder(private val binding: ItemAlumnoAsistenciaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var textWatcher: TextWatcher? = null

        fun bind(fila: FilaAsistencia) {
            binding.tvAlumnoCodigo.text = fila.alumno.codigo
            binding.tvAlumnoNombre.text = fila.alumno.nombreCompleto

            // Eliminar listener previo para evitar bucles durante reciclaje
            binding.rgEstado.setOnCheckedChangeListener(null)

            when (fila.estado) {
                "PRESENTE" -> binding.rbPresente.isChecked = true
                "TARDANZA" -> binding.rbTardanza.isChecked = true
                "AUSENTE" -> binding.rbAusente.isChecked = true
                else -> binding.rbPresente.isChecked = true
            }

            binding.rgEstado.setOnCheckedChangeListener { _, checkedId ->
                when (checkedId) {
                    R.id.rbPresente -> fila.estado = "PRESENTE"
                    R.id.rbTardanza -> fila.estado = "TARDANZA"
                    R.id.rbAusente -> fila.estado = "AUSENTE"
                }
            }

            // Manejo de observación
            textWatcher?.let { binding.etObservacion.removeTextChangedListener(it) }
            binding.etObservacion.setText(fila.observacion)
            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    fila.observacion = s?.toString() ?: ""
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            binding.etObservacion.addTextChangedListener(textWatcher)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlumnoViewHolder {
        val binding = ItemAlumnoAsistenciaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AlumnoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlumnoViewHolder, position: Int) {
        holder.bind(filas[position])
    }

    override fun getItemCount(): Int = filas.size

    fun obtenerFilas(): List<FilaAsistencia> = filas

    fun marcarTodos(nuevoEstado: String) {
        filas.forEach { it.estado = nuevoEstado }
        notifyDataSetChanged()
    }

    fun actualizarAlumnos(nuevosAlumnos: List<Alumno>) {
        filas.clear()
        filas.addAll(nuevosAlumnos.map { FilaAsistencia(alumno = it, estado = "PRESENTE") })
        notifyDataSetChanged()
    }
}
