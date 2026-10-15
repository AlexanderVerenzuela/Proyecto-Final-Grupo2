# Asiste+ — Sistema de Control de Asistencias Académico

**Asiste+** es un sistema integral y funcional desarrollado para el curso **Desarrollo de Aplicaciones Móviles I** de **Cibertec** (Carrera de Computación e Informática).

Permite registrar y administrar la asistencia de estudiantes a sus clases, funcionando tanto de manera local sin conexión (almacenamiento en SQLite) como sincronizada en tiempo real con un servidor central (PostgreSQL y API REST en Node.js), además de contar con un portal web administrativo para la gestión integral de cursos, alumnos y reportes.

---

## 🏗️ Arquitectura del Sistema

El proyecto está compuesto por tres componentes principales conectados a una misma base de datos relacional:

1. **Aplicación Móvil Android (Kotlin):**
   - Interfaz construida con `ConstraintLayout` y `Material Design`.
   - Implementación limpia de `ViewBinding`.
   - Listados mediante `RecyclerView` con adaptadores personalizados (`ViewHolder`).
   - Base de datos local mediante `SQLiteOpenHelper` (`DatabaseHelper.kt`) con operaciones CRUD completas (`insert`, `query`, `update`, `delete`).
   - Arquitectura **Offline-First**: las asistencias se guardan siempre de forma segura en SQLite y se sincronizan con la API REST cuando existe conexión.
   - Consumo real de múltiples endpoints REST (`/api/cursos`, `/api/alumnos`, `/api/asistencias`, `/api/health`, `/api/asistencias/sincronizar`).

2. **API REST Backend (Node.js & Express):**
   - Servidor HTTP en Express con conexión a PostgreSQL mediante `pg.Pool`.
   - Endpoints para gestión de alumnos, cursos, asistencias, estadísticas y sincronización en lote.
   - Validaciones estrictas de negocio (prevención de duplicados para un mismo alumno, curso y fecha, valores permitidos `PRESENTE`, `AUSENTE`, `TARDANZA`).
   - Configuración mediante variables de entorno (`.env`).

3. **Portal Web Administrativo (Web Admin):**
   - Panel de control responsivo servido directamente en el puerto 3000.
   - Dashboard con métricas en tiempo real, porcentajes de asistencia y desglose por curso.
   - Módulos CRUD completos para estudiantes y asignaturas.
   - Filtros avanzados de asistencias por curso, fecha, estado y buscador de texto.
   - Modales interactivos y notificaciones Toast para retroalimentación visual.

---

## 📁 Estructura del Repositorio

```text
Proyectofinalgrupo2/
├── app/                          # Aplicación Móvil Android (Kotlin)
│   ├── src/main/java/com/example/proyectofinalgrupo2/
│   │   ├── activities/           # MainActivity, SeleccionCursoActivity, TomarAsistenciaActivity,
│   │   │                         # HistorialActivity, GestionLocalActivity
│   │   ├── adapters/             # CursoAdapter, AlumnoAsistenciaAdapter, LocalAsistenciaAdapter
│   │   ├── api/                  # ApiClient (consumo HTTP y sincronización)
│   │   ├── data/                 # DatabaseHelper (SQLiteOpenHelper y CRUD local)
│   │   └── models/               # Alumno, Curso, Asistencia
│   └── src/main/res/             # Layouts con ConstraintLayout, colores y estilos
├── backend/                      # Servidor API REST (Node.js + Express)
│   ├── routes/                   # Rutas: alumnos, cursos, asistencias, estadisticas
│   ├── db.js                     # Pool de conexiones PostgreSQL
│   ├── index.js                  # Servidor Express y servicio estático web
│   ├── .env                      # Configuración de base de datos
│   └── package.json              # Dependencias (express, pg, cors, dotenv)
├── database/                     # Scripts SQL
│   ├── schema.sql                # DDL para PostgreSQL (tablas, claves, restricciones, índices)
│   ├── seeds.sql                 # Datos de prueba para Cibertec
│   └── schema_sqlite.sql         # Esquema SQLite local de referencia
├── web-admin/                    # Portal Web Administrativo
│   ├── index.html                # Interfaz SPA con Dashboard y tablas de gestión
│   ├── styles.css                # Estilos modernos institucionales
│   └── app.js                    # Conexión asíncrona a la API REST
└── docs/                         # Documentación del Proyecto
    ├── diagrama_er.md            # Diagrama Entidad-Relación y reglas de negocio
    ├── flujo_informacion.md      # Flujo de datos y arquitectura offline-first
    ├── api_endpoints.md          # Documentación detallada de endpoints REST
    └── pruebas_verificacion.md   # Informe de pruebas y matriz de cumplimiento
```

---

## 🚀 Guía de Instalación y Ejecución

### Requisitos Previos
- **Node.js:** v18 o superior.
- **PostgreSQL:** v14 o superior (instalado y ejecutándose en el puerto 5432).
- **Android Studio:** Ladybug / Koala / Jellyfish con JDK 21 y Android SDK 34+.

---

### 1. Configurar la Base de Datos PostgreSQL

1. Crear la base de datos `asiste_plus` en PostgreSQL:
   ```powershell
   psql -U postgres -c "CREATE DATABASE asiste_plus;"
   ```
2. Ejecutar el script DDL de creación de tablas:
   ```powershell
   psql -U postgres -d asiste_plus -f "database/schema.sql"
   ```
3. Insertar los datos de prueba iniciales:
   ```powershell
   psql -U postgres -d asiste_plus -f "database/seeds.sql"
   ```

---

### 2. Iniciar el Servidor API REST y Portal Web

1. Ingresar a la carpeta `backend`:
   ```powershell
   cd backend
   ```
2. Instalar dependencias si no se ha hecho:
   ```powershell
   npm install
   ```
3. Verificar el archivo `.env` con las credenciales de tu PostgreSQL:
   ```env
   PORT=3000
   DB_HOST=localhost
   DB_PORT=5432
   DB_USER=postgres
   DB_PASSWORD=tu_contrasena
   DB_NAME=asiste_plus
   ```
4. Iniciar el servidor:
   ```powershell
   npm start
   ```
   El servidor estará disponible en `http://localhost:3000`.
   - **Salud del servidor:** `http://localhost:3000/api/health`
   - **Portal Web Administrativo:** `http://localhost:3000/`

---

### 3. Ejecutar la Aplicación Móvil Android

1. Abrir la carpeta raíz del proyecto en **Android Studio**.
2. Esperar que Gradle sincronice las dependencias del proyecto.
3. Compilar el APK desde terminal o Android Studio:
   ```powershell
   .\gradlew.bat assembleDebug
   ```
4. Ejecutar la aplicación en el emulador de Android o en un teléfono físico conectado.
5. **Configuración de IP de red:**
   - Si usas el **Emulador Oficial de Android Studio**, la aplicación se conecta automáticamente a `http://10.0.2.2:3000` (alias predeterminado del host).
   - Si usas un **Dispositivo Físico por WiFi**, presiona el botón **"Config. IP"** en la esquina superior derecha de la pantalla de inicio de la aplicación e ingresa la IP local de tu computadora (por ejemplo, `http://192.168.1.50:3000`).

---

## 📱 Pantallas de la Aplicación Android

1. **Pantalla de Inicio (`MainActivity`):**
   - Presentación de Asiste+, indicador de estado del servidor en tiempo real.
   - Contadores de asistencias totales en SQLite y registros pendientes de sincronización.
   - Accesos directos a las 4 funciones principales.
2. **Selección de Curso (`SeleccionCursoActivity`):**
   - Listado de asignaturas habilitadas cargadas desde la API REST o la caché local de SQLite si no hay conexión.
   - Búsqueda en tiempo real por nombre, código o docente.
3. **Lista de Estudiantes y Toma de Asistencia (`TomarAsistenciaActivity`):**
   - Presenta el curso seleccionado, sección, ciclo y selector de fecha de la clase.
   - Botón para marcar a todos los estudiantes presentes con un solo toque.
   - Listado de alumnos con opciones rápidas `Presente`, `Tardanza` y `Ausente`, más campo opcional para observaciones.
   - Guarda primero en SQLite para garantizar persistencia y sincroniza con PostgreSQL si hay red.
4. **Historial de Asistencias (`HistorialActivity`):**
   - Selector para alternar entre ver los registros del **Servidor REST** o de **SQLite Local**.
   - Filtros combinados por curso y por fecha.
5. **Gestión Local SQLite (`GestionLocalActivity`):**
   - CRUD local completo: listar, buscar, modificar estado/observación mediante diálogo interactivo y eliminar con confirmación.
   - Botón **"Sincronizar"** para enviar en bloque todos los registros pendientes al servidor.

---

## 📊 Matriz de Cumplimiento de Requisitos

| Requisito | Estado | Evidencia |
| :--- | :--- | :--- |
| **Android en Kotlin** | **CUMPLIDO** | Código limpio, modular y funcional en `app/src/main/java/` |
| **ConstraintLayout** | **CUMPLIDO** | Utilizado en el 100% de layouts y elementos de lista |
| **ViewBinding** | **CUMPLIDO** | Activado en `build.gradle.kts` e integrado en todas las Activities |
| **RecyclerView + Adaptador** | **CUMPLIDO** | Implementado en `CursoAdapter`, `AlumnoAsistenciaAdapter`, `LocalAsistenciaAdapter` |
| **SQLite con SQLiteOpenHelper** | **CUMPLIDO** | `DatabaseHelper.kt` con métodos `insert`, `query`, `update`, `delete` |
| **API REST Real** | **CUMPLIDO** | Node.js/Express con 12 endpoints y respuestas HTTP estándar |
| **Base de Datos PostgreSQL** | **CUMPLIDO** | DDL relacional en `database/schema.sql` y datos en `database/seeds.sql` |
| **Consumo REST Móvil** | **CUMPLIDO** | La app consume 4 endpoints REST reales (`/cursos`, `/alumnos`, `/asistencias`, `/sincronizar`) |
| **Sincronización Offline-First** | **CUMPLIDO** | Guarda en SQLite local y sincroniza en lote con confirmación remota |
| **Portal Web Administrativo** | **CUMPLIDO** | Panel SPA en `web-admin/` con Dashboard, filtros y CRUD completo |

---

## 👨‍💻 Créditos
- **Proyecto:** Asiste+
- **Institución:** Cibertec
- **Curso:** Desarrollo de Aplicaciones Móviles I
- **Ciclo:** 5to Ciclo
