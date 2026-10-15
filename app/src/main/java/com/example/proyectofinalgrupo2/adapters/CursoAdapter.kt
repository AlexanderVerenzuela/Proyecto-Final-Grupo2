package com.example.proyectofinalgrupo2.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.proyectofinalgrupo2.databinding.ItemCursoBinding
import com.example.proyectofinalgrupo2.models.Curso

class CursoAdapter(
    private var listaCursos: List<Curso>,
    private val onCursoClick: (Curso) -> Unit
) : RecyclerView.Adapter<CursoAdapter.CursoViewHolder>() {

    inner class CursoViewHolder(private val binding: ItemCursoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(curso: Curso) {
            binding.tvCodigoCurso.text = curso.codigo
            binding.tvNombreCurso.text = curso.nombre
            binding.tvDocente.text = "Docente: ${curso.docente}"
            binding.tvCicloSeccion.text = "Ciclo ${curso.ciclo} • Sec. ${curso.seccion}"
            binding.tvCreditos.text = "${curso.creditos} Créditos Académicos"

            binding.cardCurso.setOnClickListener {
                onCursoClick(curso)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CursoViewHolder {
        val binding = ItemCursoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CursoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CursoViewHolder, position: Int) {
        holder.bind(listaCursos[position])
    }

    override fun getItemCount(): Int = listaCursos.size

    fun actualizarLista(nuevaLista: List<Curso>) {
        listaCursos = nuevaLista
        notifyDataSetChanged()
    }
}
