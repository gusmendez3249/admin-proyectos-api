# Bitacora de incidencias — Unidad III (IaC, nube, monitoreo y rendimiento)

Formato por incidencia: **problema → evidencia → diagnostico → correccion → nueva ejecucion → resultado.**
Las incidencias de calidad y seguridad del pipeline CI (SonarQube, Semgrep, JaCoCo) se documentaron en la Practica 3.

| # | Fecha | Fase | Problema | Severidad | Estado |
|---|---|---|---|---|---|
| 1 | 2026-11-23 | Herramientas | `terraform init` no descarga proveedores | Baja | Resuelta |
| 2 | 2026-11-23 | Nube | `az login` cancelado por el broker de cuentas de Windows | Baja | Resuelta |
| 3 | 2026-11-23 | Nube | La suscripcion solo permite 5 regiones | Media | Resuelta |
| 4 | 2026-11-23 | Nube | Recursos ajenos en la suscripcion compartida | Alta | Mitigada |
| 5 | 2026-11-23 | Despliegue | Ansible falla: Docker Desktop apagado | Baja | Resuelta |
| 6 | 2026-11-23 | Red | SSH bloqueado tras cambio de IP publica | Media | Resuelta (recurrente) |
| 7 | 2026-11-23 | Despliegue | Riesgo: nueva contrasena rompe la conexion a PostgreSQL | Alta | Prevenida |
| 8 | 2026-11-23 | Validacion | Rutas convertidas por Git Bash al validar configuraciones | Baja | Resuelta |
| 9 | 2026-11-23 | Operacion | `docker compose` sin permisos sobre `.env` en la VM | Baja | Por diseno |
| 10 | 2026-11-23 | Rendimiento | k6 no compila el escenario (sintaxis no soportada) | Baja | Resuelta |
| 11 | 2026-11-23 | Rendimiento / codigo | **100% de los inicios de sesion fallan bajo carga: `Transfer-Encoding` duplicado** | **Alta** | **Corregida en codigo** |
| 12 | 2026-11-23 | Monitoreo | cAdvisor no reporta metricas por contenedor | Media | Resuelta (Ansible) |
| 13 | 2026-11-23 | Observabilidad / codigo | El front responde 503 sin dejar rastro en los logs | Media | Corregida en codigo |

---

### 1. `terraform init` no descarga proveedores
- **Evidencia:** `Error while installing hashicorp/azurerm v4.81.0 ... Client.Timeout exceeded while awaiting headers` y `An established connection was aborted by the software in your host machine`.
- **Diagnostico:** corte transitorio de red/antivirus al descargar desde `registry.terraform.io`; la configuracion no tenia errores.
- **Correccion:** reintentar `terraform init`. Se versiona `.terraform.lock.hcl` para fijar las versiones exactas de los proveedores.
- **Nueva ejecucion / resultado:** `terraform init` exitoso; `terraform validate` → *Success! The configuration is valid.*

### 2. `az login` cancelado
- **Evidencia:** `ERROR: User cancelled the Accounts Control Operation ... Status_UserCanceled`.
- **Diagnostico:** Azure CLI 2.9x usa por defecto el broker WAM de Windows; la ventana se cerro antes de seleccionar la cuenta.
- **Correccion:** `az config set core.enable_broker_on_windows=false` para autenticarse en el navegador.
- **Resultado:** sesion iniciada; `az account show` → suscripcion *Azure for Students*, estado *Enabled*.

### 3. Restriccion de regiones
- **Evidencia:** politica asignada *Allowed resource deployment regions*: `chilecentral, swedencentral, norwayeast, canadacentral, northcentralus`. La region inicial del codigo (`southcentralus`) no estaba permitida.
- **Diagnostico:** las suscripciones de estudiante tienen una politica de Azure que limita las regiones.
- **Correccion:** variable `ubicacion = "northcentralus"` (la mas cercana a Mexico) y documentacion de las regiones permitidas en `variables.tf`. Se cambio el tamano a `Standard_B2als_v2`, disponible y mas economico que `B2s`.
- **Resultado:** `terraform apply` → *9 added, 0 changed, 0 destroyed*.

### 4. Recursos ajenos en la suscripcion compartida
- **Evidencia:** `az vm list` mostro una VM `admin-proyectos-servidor` en el grupo `admin-proyectos-rg`, creada por la titular de la suscripcion.
- **Diagnostico:** la suscripcion es prestada; un error de nombres o un `destroy` podria afectar recursos de otra persona. Ademas la cuota de vCPU y el credito son compartidos.
- **Correccion:** aislamiento total: grupo de recursos propio `rg-gusmendez-devops`, prefijo unico `gusmendez-devops` en todos los recursos, y revision obligatoria de `terraform plan` (solo creaciones, 0 cambios / 0 destrucciones fuera del grupo). Tamano de VM minimo viable; la VM se apaga (`az vm deallocate`) cuando no se usa.
- **Resultado:** ningun recurso ajeno modificado. Cuota de la familia B: 2 de 4 vCPU.

### 5. Ansible falla: Docker Desktop apagado
- **Evidencia:** `failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`.
- **Diagnostico:** Ansible se ejecuta dentro de un contenedor (no corre nativo en Windows) y Docker Desktop no estaba iniciado. El script continuaba despues del error.
- **Correccion:** iniciar Docker Desktop y agregar en `desplegar-ansible.ps1` la validacion `if ($LASTEXITCODE -ne 0) { throw ... }` despues de `docker build` y de `ansible-playbook`.
- **Resultado:** `PLAY RECAP ... ok=17 changed=9 unreachable=0 failed=0`.

### 6. SSH bloqueado tras cambio de IP publica (recurrente)
- **Evidencia:** IP del administrador registrada en el NSG `<IP-anterior>/32`; IP actual `<IP-nueva-1>` (y despues `<IP-nueva-2>`). Ansible no podia conectarse.
- **Diagnostico:** el NSG limita SSH (22), API (8081) y Prometheus (9090) a la IP del administrador, y el proveedor de internet asigna IP dinamica.
- **Correccion:** Terraform detecta la IP actual con el proveedor `http` (`api.ipify.org`); basta con `terraform plan` + `terraform apply`, que solo actualiza la regla.
- **Nueva ejecucion / resultado:** `Plan: 0 to add, 1 to change, 0 to destroy` → `Apply complete! Resources: 0 added, 1 changed, 0 destroyed`. Acceso restablecido manteniendo el minimo privilegio.

### 7. Riesgo: nueva contrasena rompe la conexion a PostgreSQL (prevenida)
- **Evidencia:** revision del flujo antes del segundo despliegue: el script generaba una contrasena aleatoria en cada ejecucion.
- **Diagnostico:** la imagen de PostgreSQL solo aplica `POSTGRES_PASSWORD` al inicializar el volumen; Grafana igual con la contrasena de admin. Una contrasena nueva dejaria a la API sin acceso a la BD (error de autenticacion).
- **Correccion:** el playbook lee el `.env` existente en la VM y conserva las contrasenas; solo genera aleatorias en una instalacion nueva (`lookup('password', ...)`). Las tareas con secretos usan `no_log: true`.
- **Nueva ejecucion / resultado:** segundo despliegue `ok=21 changed=3 failed=0`; login de punta a punta correcto con la contrasena conservada. Demuestra idempotencia: solo cambiaron repo, `.env` y contenedores.

### 8. Rutas convertidas por Git Bash
- **Evidencia:** `promtool: error: path 'P:/alertas.yml' does not exist`.
- **Diagnostico:** Git Bash (MSYS) convierte argumentos tipo `/p/...` en rutas de Windows antes de pasarlos a Docker.
- **Correccion:** `export MSYS_NO_PATHCONV=1`.
- **Resultado:** `promtool check config` → *SUCCESS: 7 rules found*; `amtool check-config` → OK; `alloy fmt` → OK; `ansible-playbook --syntax-check` → OK.

### 9. `docker compose` sin permisos sobre `.env` (por diseno)
- **Evidencia:** `open /opt/admin-proyectos/deploy/.env: permission denied` al ejecutar `docker compose ps` como `azureuser`.
- **Diagnostico:** el `.env` contiene secretos y tiene permisos `600` de `root`, a proposito.
- **Correccion:** operar con `sudo docker compose ...`. Se mantiene el permiso restrictivo.
- **Resultado:** 10 servicios en estado *Up* (los de la aplicacion *healthy*).

### 10. k6 no compila el escenario
- **Evidencia:** `SyntaxError: file:///scripts/escenario-carga.js: Unexpected token (59:8)` en `{ ...JSON_HDR, tags: ... }`; codigo de salida 107.
- **Diagnostico:** el compilador de k6 0.52 no soporta el operador *spread* en objetos.
- **Correccion:** reemplazar por `Object.assign({}, JSON_HDR, {...})` y validar con `k6 inspect` antes de ejecutar.
- **Resultado:** `k6 inspect` correcto; el escenario se ejecuto completo (9 min, 2 502 iteraciones). Esa ejecucion revelo la incidencia 11.

### 11. Inicios de sesion fallan bajo carga: `Transfer-Encoding` duplicado
- **Problema:** en la primera ejecucion de k6 **fallaron 2 502 de 2 502 peticiones a `/verificarLogin`** (25% del total de peticiones); los umbrales `http_req_failed`, `checks` y `login_fallido` se rompieron (k6 salio con codigo 99). El resto de endpoints respondio bien (p95 de `consultarProyectos` = 86 ms).
- **Evidencia:** `pruebas-carga/resultados/1-antes-correccion/` — `Request Failed ... net/http: HTTP/1.x transport connection broken: too many transfer encodings: ["chunked" "chunked"]`. Confirmado con `curl -D -`: la respuesta traia **dos** cabeceras `Transfer-Encoding: chunked`.
- **Diagnostico:** en el front, `verificarLogin` (y tambien `saveComentario`, `verificarTarea` y `cambiosTareas`) devolvia **la misma `ResponseEntity` que recibio de la API**, incluidas sus cabeceras de transporte (`Transfer-Encoding`, `Content-Length`, `Date`). Tomcat agrega las suyas al escribir la respuesta, quedando duplicadas. Los navegadores lo toleran (por eso no se habia detectado en pruebas manuales ni en las pruebas unitarias), pero un cliente HTTP estricto, un proxy o un balanceador pueden rechazarla. Es un defecto de interoperabilidad, no de rendimiento.
- **Correccion:** metodo `reenviar()` en `GestorController` que devuelve solo estado y cuerpo: `ResponseEntity.status(r.getStatusCode()).body(r.getBody())`, aplicado a los 4 endpoints. Prueba de regresion **AUT-E17** que verifica que no se copian `Transfer-Encoding`, `Content-Length` ni `Date`: **falla sin la correccion** (`Response should not contain header 'Content-Length'`) y pasa con ella. `mvn verify`: 39 pruebas OK, JaCoCo 100%. Commit `71c9f73` en `admin-proyectos-front`.
- **Nueva ejecucion:** redespliegue con Ansible (`ok=21 changed=1 failed=0`); `curl -D -` muestra una sola cabecera `Transfer-Encoding`. Segunda ejecucion de k6 en `pruebas-carga/resultados/2-despues-correccion/`.
- **Resultado:** ver la comparacion antes/despues en `pruebas-carga/README.md`.
- **Leccion:** la prueba de carga con un cliente distinto al navegador encontro un defecto que las pruebas funcionales no detectaban; la prueba de regresion evita que vuelva a aparecer.

### 12. cAdvisor no reporta metricas por contenedor
- **Problema:** los paneles *Memoria por contenedor* y *CPU por contenedor* de Grafana salian vacios; la consulta `count(container_memory_working_set_bytes)` devolvia 0 series etiquetadas.
- **Evidencia:** en `/metrics` de cAdvisor solo existia `container_memory_working_set_bytes{id="/"}` (la raiz). Log de cAdvisor: `Failed to process watch event ... docker-<id>.scope: failed to identify the read-write layer ID for container`. `docker info` → `driver=overlayfs [[driver-type io.containerd.snapshotter.v1]]`.
- **Diagnostico:** Docker 29 usa por defecto el almacen de imagenes de containerd (*containerd snapshotter*); cAdvisor 0.49 no sabe ubicar las capas de los contenedores en ese almacen y descarta todos los contenedores.
- **Correccion:** gestion de configuracion con Ansible: tarea que escribe `/etc/docker/daemon.json` con `"features": {"containerd-snapshotter": false}` (almacen clasico `overlay2`) mas rotacion de logs (`max-size 10m`, `max-file 3`), y un *handler* que reinicia Docker solo si el archivo cambia. Los volumenes (BD, Prometheus, Grafana) no se afectan. Commit `412d1d8`.
- **Nueva ejecucion:** `PLAY RECAP ok=23 changed=5 failed=0` (handler `Reiniciar Docker` ejecutado, imagenes reconstruidas en `overlay2`).
- **Resultado:** `docker info` → `overlay2`; Prometheus reporta memoria de los 10 contenedores (api 216 MB, front 129 MB, db 45 MB, ...). El login sigue funcionando: los datos de la BD se conservaron.

### 13. El front responde 503 sin dejar rastro en los logs
- **Problema:** en la prueba de alertas (API detenida) el front respondio **HTTP 503** de forma controlada, pero Loki no tenia ninguna linea WARN/ERROR del front en esa ventana.
- **Evidencia:** `docs/evidencias/prueba-alerta.log`; consulta a Loki `{servicio="front"} |~ "WARN|ERROR"` sin resultados durante la caida, aunque Loki si recibia logs de los 10 servicios (`count_over_time` por servicio).
- **Diagnostico:** el `@ExceptionHandler(RestClientException.class)` del front construia la respuesta 503 sin registrar el error. La falla solo se veia como metrica (`status=503`), no en los logs, lo que dificulta el diagnostico.
- **Correccion:** `log.warn("Backend no disponible, se responde 503: {}", e.getMessage())` en el manejador. `mvn verify` OK (39 pruebas, JaCoCo 100%). Commit `36cc74b` en `admin-proyectos-front`; redespliegue con Ansible (`ok=22 changed=1 failed=0`).
- **Nueva ejecucion:** caida corta de la API (2 peticiones → 503).
- **Resultado:** Loki devuelve `WARN ... GestorController : Backend no disponible, se responde 503: I/O error on POST request for "http://api:8081/buscarProyectos"`. Observacion: los logs de un contenedor recien creado tardan hasta ~1 min en llegar a Loki porque Alloy redescubre los contenedores cada minuto (comportamiento esperado de `discovery.docker`).

---

## Resumen

| Tipo | Cantidad | Incidencias |
|---|---|---|
| Defectos de codigo encontrados por pruebas de carga / alertas | 2 | 11, 13 |
| Configuracion de infraestructura / monitoreo | 4 | 3, 6, 7, 12 |
| Entorno y herramientas | 5 | 1, 2, 5, 8, 10 |
| Riesgo de gobierno (suscripcion compartida) | 1 | 4 |
| Por diseno (seguridad) | 1 | 9 |

Todas las incidencias quedaron resueltas, mitigadas o justificadas; las correcciones estan versionadas en Git y verificadas con una nueva ejecucion.
