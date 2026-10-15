package com.example.proyectofinalgrupo2.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.proyectofinalgrupo2.R
import com.example.proyectofinalgrupo2.databinding.ItemLocalAsistenciaBinding
import com.example.proyectofinalgrupo2.models.Asistencia

class LocalAsistenciaAdapter(
    private var lista: List<Asistencia>,
    private val onEditarClick: (Asistencia) -> Unit,
    private val onEliminarClick: (Asistencia) -> Unit
) : RecyclerView.Adapter<LocalAsistenciaAdapter.AsistenciaViewHolder>() {

    inner class AsistenciaViewHolder(private val binding: ItemLocalAsistenciaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Asistencia) {
            val context = binding.root.context
            binding.tvFecha.text = "Fecha: ${item.fecha}"
            binding.tvAlumnoNombre.text = item.alumnoNombre
            binding.tvCursoNombre.text = "${item.cursoNombre} (${item.cursoCodigo})"
            binding.tvEstadoBadge.text = item.estado.uppercase()

            // Badge de estado con color
            when (item.estado.uppercase()) {
                "PRESENTE" -> {
                    binding.tvEstadoBadge.setBackgroundResource(R.drawable.bg_badge_presente)
                    binding.tvEstadoBadge.setTextColor(ContextCompat.getColor(context, R.color.color_presente_text))
                }
                "TARDANZA" -> {
                    binding.tvEstadoBadge.setBackgroundResource(R.drawable.bg_badge_tardanza)
                    binding.tvEstadoBadge.setTextColor(ContextCompat.getColor(context, R.color.color_tardanza_text))
                }
                "AUSENTE" -> {
                    binding.tvEstadoBadge.setBackgroundResource(R.drawable.bg_badge_ausente)
                    binding.tvEstadoBadge.setTextColor(ContextCompat.getColor(context, R.color.color_ausente_text))
                }
            }

            // Observación
            if (item.observacion.isNotEmpty()) {
                binding.tvObservacion.text = "Obs: ${item.observacion}"
                binding.tvObservacion.visibility = android.view.View.VISIBLE
            } else {
                binding.tvObservacion.visibility = android.view.View.GONE
            }

            // Estado de sincronización
            if (item.sincronizado) {
                binding.tvSyncStatus.text = "● Sincronizado en servidor"
                binding.tvSyncStatus.setTextColor(ContextCompat.getColor(context, R.color.sync_ok))
            } else {
                binding.tvSyncStatus.text = "○ Pendiente de sincronización"
                binding.tvSyncStatus.setTextColor(ContextCompat.getColor(context, R.color.sync_pending))
            }

            // Acciones
            binding.btnEditar.setOnClickListener { onEditarClick(item) }
            binding.btnEliminar.setOnClickListener { onEliminarClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AsistenciaViewHolder {
        val binding = ItemLocalAsistenciaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AsistenciaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AsistenciaViewHolder, position: Int) {
        holder.bind(lista[position])
    }

    override fun getItemCount(): Int = lista.size

    fun actualizarLista(nuevaLista: List<Asistencia>) {
        lista = nuevaLista
        notifyDataSetChanged()
    }
}
