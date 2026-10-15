package com.example.proyectofinalgrupo2.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.proyectofinalgrupo2.models.Alumno
import com.example.proyectofinalgrupo2.models.Asistencia
import com.example.proyectofinalgrupo2.models.Curso

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "asiste_plus_local.db"
        const val DATABASE_VERSION = 1

        // Tabla Asistencias
        const val TABLE_ASISTENCIAS = "asistencias"
        const val COL_ASIST_ID = "id"
        const val COL_ASIST_REMOTO_ID = "remoto_id"
        const val COL_ASIST_ALUMNO_ID = "alumno_id"
        const val COL_ASIST_ALUMNO_COD = "alumno_codigo"
        const val COL_ASIST_ALUMNO_NOM = "alumno_nombre"
        const val COL_ASIST_CURSO_ID = "curso_id"
        const val COL_ASIST_CURSO_COD = "curso_codigo"
        const val COL_ASIST_CURSO_NOM = "curso_nombre"
        const val COL_ASIST_FECHA = "fecha"
        const val COL_ASIST_ESTADO = "estado"
        const val COL_ASIST_OBS = "observacion"
        const val COL_ASIST_SINCRONIZADO = "sincronizado"
        const val COL_ASIST_FECHA_REG = "fecha_registro"

        // Tabla Cursos (Caché local)
        const val TABLE_CURSOS = "cursos"
        const val COL_CURSO_ID = "id"
        const val COL_CURSO_CODIGO = "codigo"
        const val COL_CURSO_NOMBRE = "nombre"
        const val COL_CURSO_DOCENTE = "docente"
        const val COL_CURSO_SECCION = "seccion"
        const val COL_CURSO_CICLO = "ciclo"
        const val COL_CURSO_CREDITOS = "creditos"

        // Tabla Alumnos (Caché local)
        const val TABLE_ALUMNOS = "alumnos"
        const val COL_ALUMNO_ID = "id"
        const val COL_ALUMNO_CODIGO = "codigo"
        const val COL_ALUMNO_NOMBRES = "nombres"
        const val COL_ALUMNO_APELLIDOS = "apellidos"
        const val COL_ALUMNO_CORREO = "correo"
        const val COL_ALUMNO_TELEFONO = "telefono"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Crear tabla de cursos locales
        val createCursos = """
            CREATE TABLE $TABLE_CURSOS (
                $COL_CURSO_ID INTEGER PRIMARY KEY,
                $COL_CURSO_CODIGO TEXT NOT NULL,
                $COL_CURSO_NOMBRE TEXT NOT NULL,
                $COL_CURSO_DOCENTE TEXT NOT NULL,
                $COL_CURSO_SECCION TEXT,
                $COL_CURSO_CICLO TEXT,
                $COL_CURSO_CREDITOS INTEGER
            );
        """.trimIndent()
        db.execSQL(createCursos)

        // Crear tabla de alumnos locales
        val createAlumnos = """
            CREATE TABLE $TABLE_ALUMNOS (
                $COL_ALUMNO_ID INTEGER PRIMARY KEY,
                $COL_ALUMNO_CODIGO TEXT NOT NULL,
                $COL_ALUMNO_NOMBRES TEXT NOT NULL,
                $COL_ALUMNO_APELLIDOS TEXT NOT NULL,
                $COL_ALUMNO_CORREO TEXT,
                $COL_ALUMNO_TELEFONO TEXT
            );
        """.trimIndent()
        db.execSQL(createAlumnos)

        // Crear tabla principal de asistencias
        val createAsistencias = """
            CREATE TABLE $TABLE_ASISTENCIAS (
                $COL_ASIST_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_ASIST_REMOTO_ID INTEGER,
                $COL_ASIST_ALUMNO_ID INTEGER NOT NULL,
                $COL_ASIST_ALUMNO_COD TEXT,
                $COL_ASIST_ALUMNO_NOM TEXT NOT NULL,
                $COL_ASIST_CURSO_ID INTEGER NOT NULL,
                $COL_ASIST_CURSO_COD TEXT,
                $COL_ASIST_CURSO_NOM TEXT NOT NULL,
                $COL_ASIST_FECHA TEXT NOT NULL,
                $COL_ASIST_ESTADO TEXT NOT NULL,
                $COL_ASIST_OBS TEXT,
                $COL_ASIST_SINCRONIZADO INTEGER DEFAULT 0,
                $COL_ASIST_FECHA_REG TEXT DEFAULT (datetime('now', 'localtime')),
                UNIQUE($COL_ASIST_ALUMNO_ID, $COL_ASIST_CURSO_ID, $COL_ASIST_FECHA)
            );
        """.trimIndent()
        db.execSQL(createAsistencias)

        // Poblar datos iniciales académicos para funcionamiento offline inmediato
        insertarDatosIniciales(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ASISTENCIAS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CURSOS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ALUMNOS")
        onCreate(db)
    }

    private fun insertarDatosIniciales(db: SQLiteDatabase) {
        // Cursos por defecto
        val cursos = listOf(
            ContentValues().apply {
                put(COL_CURSO_ID, 1)
                put(COL_CURSO_CODIGO, "DAM-501")
                put(COL_CURSO_NOMBRE, "Desarrollo de Aplicaciones Móviles I")
                put(COL_CURSO_DOCENTE, "Mg. Carlos Mendoza Paredes")
                put(COL_CURSO_SECCION, "T5BN")
                put(COL_CURSO_CICLO, "V")
                put(COL_CURSO_CREDITOS, 4)
            },
            ContentValues().apply {
                put(COL_CURSO_ID, 2)
                put(COL_CURSO_CODIGO, "DSW-502")
                put(COL_CURSO_NOMBRE, "Desarrollo de Servicios Web I")
                put(COL_CURSO_DOCENTE, "Ing. Patricia Villanueva Flores")
                put(COL_CURSO_SECCION, "T5BN")
                put(COL_CURSO_CICLO, "V")
                put(COL_CURSO_CREDITOS, 4)
            },
            ContentValues().apply {
                put(COL_CURSO_ID, 3)
                put(COL_CURSO_CODIGO, "BDA-503")
                put(COL_CURSO_NOMBRE, "Base de Datos Avanzado")
                put(COL_CURSO_DOCENTE, "Lic. Roberto Gómez Salazar")
                put(COL_CURSO_SECCION, "T5BN")
                put(COL_CURSO_CICLO, "V")
                put(COL_CURSO_CREDITOS, 3)
            }
        )
        cursos.forEach { db.insert(TABLE_CURSOS, null, it) }

        // Alumnos por defecto
        val alumnos = listOf(
            ContentValues().apply {
                put(COL_ALUMNO_ID, 1)
                put(COL_ALUMNO_CODIGO, "I20231001")
                put(COL_ALUMNO_NOMBRES, "Alexander")
                put(COL_ALUMNO_APELLIDOS, "Verenzuela López")
                put(COL_ALUMNO_CORREO, "alexander.verenzuela@cibertec.edu.pe")
                put(COL_ALUMNO_TELEFONO, "987654321")
            },
            ContentValues().apply {
                put(COL_ALUMNO_ID, 2)
                put(COL_ALUMNO_CODIGO, "I20231002")
                put(COL_ALUMNO_NOMBRES, "María Fernanda")
                put(COL_ALUMNO_APELLIDOS, "Quispe Ramos")
                put(COL_ALUMNO_CORREO, "maria.quispe@cibertec.edu.pe")
                put(COL_ALUMNO_TELEFONO, "987112233")
            },
            ContentValues().apply {
                put(COL_ALUMNO_ID, 3)
                put(COL_ALUMNO_CODIGO, "I20231003")
                put(COL_ALUMNO_NOMBRES, "Juan Carlos")
                put(COL_ALUMNO_APELLIDOS, "Torres Benítez")
                put(COL_ALUMNO_CORREO, "juan.torres@cibertec.edu.pe")
                put(COL_ALUMNO_TELEFONO, "987223344")
            },
            ContentValues().apply {
                put(COL_ALUMNO_ID, 4)
                put(COL_ALUMNO_CODIGO, "I20231004")
                put(COL_ALUMNO_NOMBRES, "Lucía Andrea")
                put(COL_ALUMNO_APELLIDOS, "Chávez Huamán")
                put(COL_ALUMNO_CORREO, "lucia.chavez@cibertec.edu.pe")
                put(COL_ALUMNO_TELEFONO, "987334455")
            },
            ContentValues().apply {
                put(COL_ALUMNO_ID, 5)
                put(COL_ALUMNO_CODIGO, "I20231005")
                put(COL_ALUMNO_NOMBRES, "Diego Alonso")
                put(COL_ALUMNO_APELLIDOS, "Morales Vega")
                put(COL_ALUMNO_CORREO, "diego.morales@cibertec.edu.pe")
                put(COL_ALUMNO_TELEFONO, "987445566")
            }
        )
        alumnos.forEach { db.insert(TABLE_ALUMNOS, null, it) }
    }

    // =================================================================
    // OPERACIONES CRUD: ASISTENCIAS
    // =================================================================

    /**
     * Crear registro de asistencia local (SQLite insert)
     */
    fun insertarAsistencia(asistencia: Asistencia): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            if (asistencia.remotoId != null) {
                put(COL_ASIST_REMOTO_ID, asistencia.remotoId)
            }
            put(COL_ASIST_ALUMNO_ID, asistencia.alumnoId)
            put(COL_ASIST_ALUMNO_COD, asistencia.alumnoCodigo)
            put(COL_ASIST_ALUMNO_NOM, asistencia.alumnoNombre)
            put(COL_ASIST_CURSO_ID, asistencia.cursoId)
            put(COL_ASIST_CURSO_COD, asistencia.cursoCodigo)
            put(COL_ASIST_CURSO_NOM, asistencia.cursoNombre)
            put(COL_ASIST_FECHA, asistencia.fecha)
            put(COL_ASIST_ESTADO, asistencia.estado.uppercase())
            put(COL_ASIST_OBS, asistencia.observacion)
            put(COL_ASIST_SINCRONIZADO, if (asistencia.sincronizado) 1 else 0)
        }
        return db.insertWithOnConflict(
            TABLE_ASISTENCIAS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /**
     * Consultar y listar todas las asistencias locales (SQLite query)
     */
    fun listarAsistencias(): List<Asistencia> {
        val lista = mutableListOf<Asistencia>()
        val db = readableDatabase
        val cursor: Cursor = db.query(
            TABLE_ASISTENCIAS,
            null,
            null,
            null,
            null,
            null,
            "$COL_ASIST_FECHA DESC, $COL_ASIST_ID DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                lista.add(cursorToAsistencia(it))
            }
        }
        return lista
    }

    /**
     * Buscar asistencias locales por texto (alumno, curso, fecha o estado)
     */
    fun buscarAsistencias(filtro: String): List<Asistencia> {
        val lista = mutableListOf<Asistencia>()
        val db = readableDatabase
        val pattern = "%$filtro%"
        val selection = "$COL_ASIST_ALUMNO_NOM LIKE ? OR $COL_ASIST_CURSO_NOM LIKE ? OR $COL_ASIST_FECHA LIKE ? OR $COL_ASIST_ESTADO LIKE ? OR $COL_ASIST_ALUMNO_COD LIKE ?"
        val selectionArgs = arrayOf(pattern, pattern, pattern, pattern, pattern)

        val cursor = db.query(
            TABLE_ASISTENCIAS,
            null,
            selection,
            selectionArgs,
            null,
            null,
            "$COL_ASIST_FECHA DESC, $COL_ASIST_ID DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                lista.add(cursorToAsistencia(it))
            }
        }
        return lista
    }

    /**
     * Obtener una asistencia por su ID local
     */
    fun obtenerAsistenciaPorId(id: Long): Asistencia? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_ASISTENCIAS,
            null,
            "$COL_ASIST_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) cursorToAsistencia(it) else null
        }
    }

    /**
     * Modificar registro de asistencia local (SQLite update)
     */
    fun actualizarAsistencia(id: Long, estado: String, observacion: String): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_ASIST_ESTADO, estado.uppercase())
            put(COL_ASIST_OBS, observacion)
            put(COL_ASIST_SINCRONIZADO, 0) // Al modificar pasa a pendiente de sincronización
        }
        return db.update(
            TABLE_ASISTENCIAS,
            values,
            "$COL_ASIST_ID = ?",
            arrayOf(id.toString())
        )
    }

    /**
     * Eliminar registro de asistencia local (SQLite delete)
     */
    fun eliminarAsistencia(id: Long): Int {
        val db = writableDatabase
        return db.delete(
            TABLE_ASISTENCIAS,
            "$COL_ASIST_ID = ?",
            arrayOf(id.toString())
        )
    }

    /**
     * Obtener registros pendientes de sincronización (sincronizado == 0)
     */
    fun listarPendientesSincronizacion(): List<Asistencia> {
        val lista = mutableListOf<Asistencia>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_ASISTENCIAS,
            null,
            "$COL_ASIST_SINCRONIZADO = 0",
            null,
            null,
            null,
            "$COL_ASIST_ID ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                lista.add(cursorToAsistencia(it))
            }
        }
        return lista
    }

    /**
     * Marcar asistencia como sincronizada con su ID asignado en el servidor
     */
    fun marcarComoSincronizado(localId: Long, remotoId: Int): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_ASIST_SINCRONIZADO, 1)
            put(COL_ASIST_REMOTO_ID, remotoId)
        }
        return db.update(
            TABLE_ASISTENCIAS,
            values,
            "$COL_ASIST_ID = ?",
            arrayOf(localId.toString())
        )
    }

    /**
     * Contar total de asistencias y pendientes
     */
    fun obtenerContadores(): Pair<Int, Int> {
        val db = readableDatabase
        val cursorTotal = db.rawQuery("SELECT COUNT(*) FROM $TABLE_ASISTENCIAS", null)
        val cursorPendientes = db.rawQuery("SELECT COUNT(*) FROM $TABLE_ASISTENCIAS WHERE $COL_ASIST_SINCRONIZADO = 0", null)

        var total = 0
        var pendientes = 0

        cursorTotal.use { if (it.moveToFirst()) total = it.getInt(0) }
        cursorPendientes.use { if (it.moveToFirst()) pendientes = it.getInt(0) }

        return Pair(total, pendientes)
    }

    // =================================================================
    // GESTIÓN DE CACHÉ LOCAL: CURSOS Y ALUMNOS
    // =================================================================

    fun guardarCursosCache(cursos: List<Curso>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            cursos.forEach { c ->
                val v = ContentValues().apply {
                    put(COL_CURSO_ID, c.id)
                    put(COL_CURSO_CODIGO, c.codigo)
                    put(COL_CURSO_NOMBRE, c.nombre)
                    put(COL_CURSO_DOCENTE, c.docente)
                    put(COL_CURSO_SECCION, c.seccion)
                    put(COL_CURSO_CICLO, c.ciclo)
                    put(COL_CURSO_CREDITOS, c.creditos)
                }
                db.insertWithOnConflict(TABLE_CURSOS, null, v, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun listarCursosLocales(): List<Curso> {
        val lista = mutableListOf<Curso>()
        val db = readableDatabase
        val cursor = db.query(TABLE_CURSOS, null, null, null, null, null, "$COL_CURSO_NOMBRE ASC")
        cursor.use {
            while (it.moveToNext()) {
                lista.add(
                    Curso(
                        id = it.getInt(it.getColumnIndexOrThrow(COL_CURSO_ID)),
                        codigo = it.getString(it.getColumnIndexOrThrow(COL_CURSO_CODIGO)),
                        nombre = it.getString(it.getColumnIndexOrThrow(COL_CURSO_NOMBRE)),
                        docente = it.getString(it.getColumnIndexOrThrow(COL_CURSO_DOCENTE)),
                        seccion = it.getString(it.getColumnIndexOrThrow(COL_CURSO_SECCION)) ?: "T5BN",
                        ciclo = it.getString(it.getColumnIndexOrThrow(COL_CURSO_CICLO)) ?: "V",
                        creditos = it.getInt(it.getColumnIndexOrThrow(COL_CURSO_CREDITOS))
                    )
                )
            }
        }
        return lista
    }

    fun guardarAlumnosCache(alumnos: List<Alumno>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            alumnos.forEach { a ->
                val v = ContentValues().apply {
                    put(COL_ALUMNO_ID, a.id)
                    put(COL_ALUMNO_CODIGO, a.codigo)
                    put(COL_ALUMNO_NOMBRES, a.nombres)
                    put(COL_ALUMNO_APELLIDOS, a.apellidos)
                    put(COL_ALUMNO_CORREO, a.correo)
                    put(COL_ALUMNO_TELEFONO, a.telefono)
                }
                db.insertWithOnConflict(TABLE_ALUMNOS, null, v, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun listarAlumnosLocales(): List<Alumno> {
        val lista = mutableListOf<Alumno>()
        val db = readableDatabase
        val cursor = db.query(TABLE_ALUMNOS, null, null, null, null, null, "$COL_ALUMNO_APELLIDOS ASC")
        cursor.use {
            while (it.moveToNext()) {
                lista.add(
                    Alumno(
                        id = it.getInt(it.getColumnIndexOrThrow(COL_ALUMNO_ID)),
                        codigo = it.getString(it.getColumnIndexOrThrow(COL_ALUMNO_CODIGO)),
                        nombres = it.getString(it.getColumnIndexOrThrow(COL_ALUMNO_NOMBRES)),
                        apellidos = it.getString(it.getColumnIndexOrThrow(COL_ALUMNO_APELLIDOS)),
                        correo = it.getString(it.getColumnIndexOrThrow(COL_ALUMNO_CORREO)),
                        telefono = it.getString(it.getColumnIndexOrThrow(COL_ALUMNO_TELEFONO))
                    )
                )
            }
        }
        return lista
    }

    // =================================================================
    // TRANSFORMADOR CURSOR -> MODELO
    // =================================================================
    private fun cursorToAsistencia(cursor: Cursor): Asistencia {
        val remotoIdIdx = cursor.getColumnIndexOrThrow(COL_ASIST_REMOTO_ID)
        val remotoId = if (cursor.isNull(remotoIdIdx)) null else cursor.getInt(remotoIdIdx)

        return Asistencia(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ASIST_ID)),
            remotoId = remotoId,
            alumnoId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ASIST_ALUMNO_ID)),
            alumnoCodigo = cursor.getString(cursor.getColumnIndexOrThrow(COL_ASIST_ALUMNO_COD)) ?: "",
            alumnoNombre = cursor.getString(cursor.getColumnIndexOrThrow(COL_ASIST_ALUMNO_NOM)),
            cursoId = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ASIST_CURSO_ID)),
            cursoCodigo = cursor.getString(cursor.getColumnIndexOrThrow(COL_ASIST_CURSO_COD)) ?: "",
            cursoNombre = cursor.getString(cursor.getColumnIndexOrThrow(COL_ASIST_CURSO_NOM)),
            fecha = cursor.getString(cursor.getColumnIndexOrThrow(COL_ASIST_FECHA)),
            estado = cursor.getString(cursor.getColumnIndexOrThrow(COL_ASIST_ESTADO)),
            observacion = cursor.getString(cursor.getColumnIndexOrThrow(COL_ASIST_OBS)) ?: "",
            sincronizado = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ASIST_SINCRONIZADO)) == 1,
            fechaRegistro = cursor.getString(cursor.getColumnIndexOrThrow(COL_ASIST_FECHA_REG)) ?: ""
        )
    }
}
