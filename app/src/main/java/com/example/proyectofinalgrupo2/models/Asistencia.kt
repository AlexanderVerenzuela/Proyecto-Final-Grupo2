
package com.example.proyectofinalgrupo2.models

import java.io.Serializable

data class Asistencia(
    val id: Long = 0,
    val remotoId: Int? = null,
    val alumnoId: Int,
    val alumnoCodigo: String = "",
    val alumnoNombre: String = "",
    val cursoId: Int,
    val cursoCodigo: String = "",
    val cursoNombre: String = "",
    val fecha: String,
    val estado: String,
    val observacion: String = "",
    val sincronizado: Boolean = false,
    val fechaRegistro: String = "",
    val sesionId: Int? = null
) : Serializable

