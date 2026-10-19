
package com.example.proyectofinalgrupo2.models

data class SesionAsistencia(
    val id: Int,
    val cursoId: Int,
    val fecha: String,
    val nombre: String,
    val inicioAt: String,
    val finAt: String,
    val estado: String,
    val segundosRestantes: Int
)
