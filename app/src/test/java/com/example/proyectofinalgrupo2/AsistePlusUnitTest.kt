package com.example.proyectofinalgrupo2

import com.example.proyectofinalgrupo2.models.Alumno
import com.example.proyectofinalgrupo2.models.Asistencia
import com.example.proyectofinalgrupo2.models.Curso
import org.junit.Assert.*
import org.junit.Test

class AsistePlusUnitTest {

    @Test
    fun alumno_nombreCompleto_isFormattedCorrectly() {
        val alumno = Alumno(
            id = 1,
            codigo = "I20231001",
            nombres = "Alexander",
            apellidos = "Verenzuela López",
            correo = "alexander.verenzuela@cibertec.edu.pe"
        )
        assertEquals("Verenzuela López, Alexander", alumno.nombreCompleto)
        assertEquals("I20231001", alumno.codigo)
        assertTrue(alumno.activo)
    }

    @Test
    fun curso_valoresPorDefecto_sonCorrectos() {
        val curso = Curso(
            id = 1,
            codigo = "DAM-501",
            nombre = "Desarrollo de Aplicaciones Móviles I",
            docente = "Mg. Carlos Mendoza"
        )
        assertEquals("T5BN", curso.seccion)
        assertEquals("V", curso.ciclo)
        assertEquals(4, curso.creditos)
        assertTrue(curso.activo)
    }

    @Test
    fun asistencia_estadosValidos_seVerificanCorrectamente() {
        val estadosPermitidos = listOf("PRESENTE", "AUSENTE", "TARDANZA")
        val estadoPrueba1 = "presente".uppercase()
        val estadoPrueba2 = "TARDANZA".uppercase()
        val estadoInvalido = "FALTA_INJUSTIFICADA"

        assertTrue(estadosPermitidos.contains(estadoPrueba1))
        assertTrue(estadosPermitidos.contains(estadoPrueba2))
        assertFalse(estadosPermitidos.contains(estadoInvalido))
    }

    @Test
    fun asistencia_modelo_banderasDeSincronizacion() {
        val asistOffline = Asistencia(
            id = 10,
            remotoId = null,
            alumnoId = 1,
            alumnoNombre = "Alexander Verenzuela",
            cursoId = 1,
            cursoNombre = "DAM I",
            fecha = "2026-10-14",
            estado = "PRESENTE",
            sincronizado = false
        )
        assertFalse(asistOffline.sincronizado)
        assertNull(asistOffline.remotoId)

        val asistSync = asistOffline.copy(remotoId = 105, sincronizado = true)
        assertTrue(asistSync.sincronizado)
        assertEquals(105, asistSync.remotoId)
    }

    @Test
    fun reglaNegocio_duplicidad_detectaSesionDuplicada() {
        val listaAsistencias = listOf(
            Asistencia(id = 1, alumnoId = 1, cursoId = 1, fecha = "2026-10-14", estado = "PRESENTE"),
            Asistencia(id = 2, alumnoId = 2, cursoId = 1, fecha = "2026-10-14", estado = "PRESENTE"),
            Asistencia(id = 3, alumnoId = 1, cursoId = 2, fecha = "2026-10-14", estado = "PRESENTE")
        )

        // Intento de registrar al alumno 1 en el curso 1 en la misma fecha (debe detectar duplicado)
        val esDuplicado = listaAsistencias.any { it.alumnoId == 1 && it.cursoId == 1 && it.fecha == "2026-10-14" }
        assertTrue("Debe detectar duplicado para el mismo alumno, curso y fecha", esDuplicado)

        // Registro para una fecha distinta (no debe ser duplicado)
        val noEsDuplicado = listaAsistencias.any { it.alumnoId == 1 && it.cursoId == 1 && it.fecha == "2026-10-15" }
        assertFalse("No debe ser duplicado si la fecha es distinta", noEsDuplicado)
    }
}
