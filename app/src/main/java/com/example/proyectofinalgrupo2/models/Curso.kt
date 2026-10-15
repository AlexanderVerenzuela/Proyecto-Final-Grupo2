package com.example.proyectofinalgrupo2.models

import java.io.Serializable

data class Curso(
    val id: Int,
    val codigo: String,
    val nombre: String,
    val docente: String,
    val seccion: String = "T5BN",
    val ciclo: String = "V",
    val creditos: Int = 4,
    val activo: Boolean = true
) : Serializable
