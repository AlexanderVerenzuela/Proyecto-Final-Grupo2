package com.example.proyectofinalgrupo2.models

import java.io.Serializable

data class Alumno(
    val id: Int,
    val codigo: String,
    val nombres: String,
    val apellidos: String,
    val correo: String,
    val telefono: String? = null,
    val activo: Boolean = true
) : Serializable {
    val nombreCompleto: String
        get() = "$apellidos, $nombres"
}
