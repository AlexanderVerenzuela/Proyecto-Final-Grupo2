# Asiste+ — Control de Asistencia Académico

Proyecto final para el curso **Desarrollo de Aplicaciones Móviles I** — **Cibertec**.

---

### 👥 Integrantes del Grupo (Grupo 2)
* **Carrera:** Computación e Informática (5to Ciclo)
* **Curso:** Desarrollo de Aplicaciones Móviles I

| Rol | Apellidos y Nombres | Código |
| :--- | :--- | :--- |
| **Coordinador** | Alexander Verenzuela Daboin | `i202500911` |
| Integrante | Vide Andre Alexander Jimenez Quispe | `i202312139` |
| Integrante | Maria de los Angeles Calderon Navarro | `i202419559` |
| Integrante | Erick Franco Velásquez Mamani | `i202120270` |
| Integrante | Henry Wilson Caruapoma Castro | `i201614176` |
| Integrante | Carlos Francisco Justiniano Valderrama | `i202402162` |
| Integrante | Carlos Riveros Gomez | `i202505681` |

---

### 📱 Descripción de la Aplicación
**Asiste+** es una aplicación móvil desarrollada en **Kotlin** para el registro y gestión de asistencias de estudiantes.

* **Arquitectura Offline-First:** Permite registrar la asistencia en SQLite local sin necesidad de internet en el aula.
* **Sincronización:** Conexión y sincronización en tiempo real con la base de datos central PostgreSQL mediante API REST.
* **Módulos:**
  * Selección de curso y docente.
  * Toma de asistencia rápida (Presente / Tardanza / Ausente) con opción "Todos Presentes".
  * Historial de asistencias registradas.
  * Gestión local SQLite (CRUD de registros guardados y sincronización manual).

---

### 🛠️ Tecnologías Utilizadas
* Kotlin & Android SDK
* ConstraintLayout & Material Design
* ViewBinding & RecyclerView
* SQLite (SQLiteOpenHelper)
* API REST (HTTP / JSON)
