# ==============================================================================
# SCRIPT DE PRUEBA AUTOMATIZADA DE ENDPOINTS - Ejercicio5MyAPIGestor
# API REST running on http://localhost:8081
# ==============================================================================

$baseUrl = "http://localhost:8081"
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " 🚀 PROBANDO ENDPOINTS DE LA API REST ($baseUrl)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

function Test-Endpoint {
    param (
        [string]$Nombre,
        [string]$Metodo,
        [string]$Path,
        [string]$Body = $null
    )
    $url = "$baseUrl$Path"
    try {
        if ($Body) {
            $response = Invoke-RestMethod -Uri $url -Method $Metodo -Body $Body -ContentType "application/json" -ErrorAction Stop
        } else {
            $response = Invoke-RestMethod -Uri $url -Method $Metodo -ErrorAction Stop
        }
        Write-Host "[OK 200] $Nombre ($Metodo $Path)" -ForegroundColor Green
        return $response
    } catch {
        Write-Host "[FAIL] $Nombre ($Metodo $Path) - Error: $_" -ForegroundColor Red
        return $null
    }
}

Write-Host "`n--- 1. ENDPOINTS DE USUARIOS ---" -ForegroundColor Yellow
$usuarios = Test-Endpoint -Nombre "Obtener todos los usuarios" -Metodo "POST" -Path "/buscarUsuarios"
$usuarioUnit = Test-Endpoint -Nombre "Buscar usuario por ID" -Metodo "GET" -Path "/buscarUsuario?id=131"
$login = Test-Endpoint -Nombre "Login de usuario" -Metodo "POST" -Path "/getUsuarioLogin" -Body '{"correo_electronico":"ana22533@cbtis75.edu.mx","contrasena":"0722"}'

Write-Host "`n--- 2. ENDPOINTS DE PROYECTOS ---" -ForegroundColor Yellow
$proyectos = Test-Endpoint -Nombre "Obtener todos los proyectos" -Metodo "POST" -Path "/buscarProyectos"
$proyectoUnit = Test-Endpoint -Nombre "Buscar proyecto por ID" -Metodo "GET" -Path "/buscarProyecto?id_proyecto=197"

Write-Host "`n--- 3. ENDPOINTS DE TAREAS ---" -ForegroundColor Yellow
$tareas = Test-Endpoint -Nombre "Obtener todas las tareas" -Metodo "POST" -Path "/buscarTareas"
$tareaUnit = Test-Endpoint -Nombre "Buscar tarea por ID" -Metodo "GET" -Path "/buscarTarea?id_tarea=199"
$tareasProyecto = Test-Endpoint -Nombre "Obtener tareas por proyecto" -Metodo "POST" -Path "/getTarea" -Body '{"id_proyecto":197}'

Write-Host "`n--- 4. ENDPOINTS DE COMENTARIOS ---" -ForegroundColor Yellow
$comentarios = Test-Endpoint -Nombre "Obtener todos los comentarios" -Metodo "POST" -Path "/buscarComentarios"

Write-Host "`n--- 5. ENDPOINTS DE ASIGNACIONES ---" -ForegroundColor Yellow
$asignaciones = Test-Endpoint -Nombre "Obtener todas las asignaciones" -Metodo "POST" -Path "/buscarAsignaciones"

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " ✅ PRUEBAS FINALIZADAS EXITOSAMENTE " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
