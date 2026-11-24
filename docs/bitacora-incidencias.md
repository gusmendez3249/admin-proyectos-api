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
| 14 | 2026-11-24 | Seguridad (SAST) | Semgrep: endpoints de Actuator expuestos en el puerto publico | Media | Mitigada (puerto interno) |
| 15 | 2026-11-24 | Despliegue / monitoreo | Prometheus conserva la configuracion vieja tras redesplegar | Media | Resuelta |
| 16 | 2026-11-24 | Red | La red del administrador bloquea el puerto 22 de salida | Alta (para la demo) | Resuelta (SSH en 443) |
| 17 | 2026-11-24 | CI | SonarQube rechaza el analisis: token de Jenkins vencido (HTTP 401) | Media | Resuelta |
| 18 | 2026-11-24 | Monitoreo / alertas | Falso positivo de `LatenciaP95Alta` con poco trafico | Media | Resuelta |
| 19 | 2026-11-24 | Operacion / monitoreo | Disco al 80 %, cAdvisor sin contenedores recreados y panel de disponibilidad duplicado | Alta | Resuelta |

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

### 14. Semgrep: endpoints de Actuator expuestos en el puerto publico
- **Problema:** al re-ejecutar los pipelines con el codigo de la Unidad III, Semgrep reporto 1 hallazgo en cada repositorio (antes 0): `java.spring.security.audit.spring-actuator-non-health-enabled.spring-actuator-dangerous-endpoints-enabled` (WARNING) en `application.properties`.
- **Evidencia:** `admin-proyectos-api #7` y `admin-proyectos-front #5` → `Ran 264 rules on 81 files: 1 finding` / `Ran 203 rules on 78 files: 1 finding`; desde Internet `http://<dns>:8082/actuator/prometheus` respondia HTTP 200 con metricas internas (rutas, JVM, memoria).
- **Diagnostico:** hallazgo **real** introducido en esta unidad: al agregar Actuator para Prometheus, `health`, `info` y `prometheus` quedaron en el mismo puerto que la aplicacion, y el 8082 es publico.
- **Correccion:** `management.server.port` (9081 API, 9082 front), puerto interno que no se publica fuera de la red de Docker; se retira `info`; Prometheus y el `HEALTHCHECK` usan el puerto interno; Ansible verifica la salud con el estado del HEALTHCHECK. Commits `7c040c3` (API) y `58d0e9b` (front). `mvn verify`: 63 y 39 pruebas OK, JaCoCo 100 %.
- **Nueva ejecucion / resultado:** desde Internet `:8082/actuator/prometheus`, `:8082/actuator/health` y `:8081/actuator/prometheus` → **HTTP 404**; Prometheus 5/5 objetivos *up* por `api:9081` y `front:9082`. La regla de Semgrep sigue marcando la propiedad (detecta que `prometheus` esta habilitado, no en que puerto), por lo que el hallazgo se **acepta con mitigacion documentada**: el endpoint es necesario para el monitoreo y ya no es accesible desde fuera.

### 15. Prometheus conserva la configuracion vieja tras redesplegar
- **Problema:** despues de mover Actuator al puerto interno, Prometheus mostraba `api` y `front` en estado *down*.
- **Evidencia:** `/api/v1/targets` seguia apuntando a `http://api:8081/...` y `http://front:8082/...` aunque `prometheus.yml` en la VM ya decia 9081/9082.
- **Diagnostico:** el compose montaba **archivos individuales** (`prometheus.yml`, `alertas.yml`, `alertmanager.yml`, `config.alloy`). Un montaje de archivo queda ligado al inodo original; `git` reemplaza el archivo (nuevo inodo), asi que el contenedor seguia viendo la version anterior. Ademas Prometheus solo lee la configuracion al arrancar.
- **Correccion:** montar **carpetas** de configuracion y agregar en Ansible la tarea *Recargar la configuracion de monitoreo si cambio el repositorio* (`docker compose restart prometheus alertmanager alloy`, solo `when: repo.changed`). Commit `0ab3c00`.
- **Nueva ejecucion / resultado:** `PLAY RECAP ok=25 changed=4 failed=0`; la verificacion final del playbook confirma 5/5 objetivos *up*.

### 16. La red del administrador bloquea el puerto 22 de salida
- **Problema:** Ansible fallo con `ssh: connect to host 20.221.204.187 port 22: Connection timed out` (primero a mitad del playbook y despues desde *Gathering Facts*).
- **Evidencia:** la IP del administrador coincidia con la regla del NSG; la VM respondia en 8082, 3000 y 8081 (tambien solo-admin), pero no en 22. Prueba de control: `github.com:22` tambien bloqueado, `ssh.github.com:443` abierto → bloqueo en la red local, no en Azure.
- **Diagnostico:** la red (institucional) filtra el puerto 22 de salida. Riesgo alto para la **defensa presencial**, donde se demuestra Ansible en vivo.
- **Correccion:** SSH alternativo en **443**: Terraform agrega 443 a la regla de administracion (solo IP del administrador; `0 added, 1 changed`); Ansible gestiona el *drop-in* `/etc/systemd/system/ssh.socket.d/puerto-443.conf` con un handler que reinicia `ssh.socket` (se conserva el 22). Como en ese momento no habia SSH, la primera configuracion se aplico una sola vez con `az vm run-command` (API de Azure por HTTPS); a partir de ahi Ansible es el responsable. `desplegar-ansible.ps1 -PuertoSsh 443`. Commit `0ab3c00`.
- **Nueva ejecucion / resultado:** `ssh -p 443` OK; despliegue completo por 443: `ok=25 changed=4 failed=0`.

### 17. SonarQube rechaza el analisis: token de Jenkins vencido
- **Problema:** `admin-proyectos-api #6` y `admin-proyectos-front #4` terminaron en **FAILURE** en la etapa *Analisis SonarQube*; Quality Gate y Semgrep se omitieron. Compilacion, pruebas y cobertura si pasaron.
- **Evidencia:** `[ERROR] Failed to query server version: GET http://sonarqube:9000/api/v2/analysis/version failed with HTTP 401. Please check the property sonar.token`.
- **Diagnostico:** la credencial `sonarqube-token` de Jenkins tenia un token que ya no era valido en SonarQube (generado en la Unidad II).
- **Correccion:** el administrador genero un token nuevo (*Global Analysis Token*) en SonarQube y actualizo la credencial en Jenkins (los secretos no se escriben en el repositorio ni en la bitacora). Ademas se restablecio la contrasena del usuario de Jenkins con `restablecer-password-jenkins.ps1` (BCrypt, con respaldo de la configuracion).
- **Nueva ejecucion / resultado:** `admin-proyectos-api #7` y `admin-proyectos-front #5` → **SUCCESS**, `ANALYSIS SUCCESSFUL`, Quality Gate `SUCCESS`.
- **Leccion:** los tokens de integracion vencen; conviene registrar su fecha de expiracion y revisarlos antes de una entrega.

### 18. Falso positivo de `LatenciaP95Alta` con poco trafico
- **Problema:** durante la toma de capturas, `LatenciaP95Alta{job="front"}` paso a *pending* y despues a *firing* sin que hubiera un problema en la aplicacion.
- **Evidencia:** captura `A-07c`; alerta activa desde 16:58:32 UTC con valor **6.73 s**; en los 10 minutos previos solo hubo **~12 peticiones**, casi todas a `uri="/**"` (recursos estaticos: imagen de fondo y videos de varios MB descargados por el navegador de capturas).
- **Diagnostico:** con muy poco trafico, el p95 lo dominan un par de descargas grandes; la regla no distinguia recursos estaticos ni exigia un volumen minimo. Sin estaticos, el p95 real del front era **6 ms**.
- **Correccion:** la regla excluye `uri=~"/actuator.*|/[*][*]"` y agrega `and on (job) sum by (job)(rate(...[5m])) > 0.1` (minimo 0.1 peticiones/s). `promtool check rules` → *SUCCESS: 7 rules found*. Commit `b000b14`.
- **Nueva ejecucion / resultado:** redespliegue con Ansible (`failed=0`, reinicio de Prometheus por cambio de repositorio) → **0 alertas activas**; la consulta nueva no devuelve resultados con el mismo trafico.

### 19. Disco al 80 %, cAdvisor sin contenedores recreados y disponibilidad duplicada
- **Problema:** el tablero actual (revisado para la captura `A-05b`) mostraba disco raiz al **79.8 %** (umbral de alerta 85 %), los paneles *Memoria/CPU por contenedor* sin datos y el panel *Front arriba* con **CAIDO y ARRIBA** a la vez.
- **Evidencia:** `docker system df` → 10 imagenes de la aplicacion (una por commit desplegado: 5 del front de 673 MB y 5 de la API) y **4.3 GB** de cache de compilacion; `du -sh /var/lib/containerd` → **12 GB**; `count(container_memory_working_set_bytes)` vacio hasta reiniciar cAdvisor (despues 11–12 series); en Prometheus coexistian `up{instance="front:8082"}` (vieja) y `up{instance="front:9082"}` (nueva).
- **Diagnostico:**
  1. Cada despliegue etiqueta las imagenes con el commit y nunca se borraban las anteriores.
  2. Los 12 GB eran el almacen de imagenes de containerd que quedo **huerfano** al cambiar Docker a `overlay2` (incidencia #12): 12 imagenes (`ctr -n moby images ls`) que Docker ya no usa.
  3. cAdvisor no detecta los contenedores que Docker recrea despues de su arranque (Docker 29 + factoria de containerd), asi que cada despliegue lo dejaba "ciego".
  4. El panel mostraba una serie por instancia; al mover Actuator al puerto 9082 quedaron dos.
- **Correccion:**
  - Ansible: *Eliminar imagenes de versiones anteriores de la aplicacion* (conserva solo la del commit actual), *Eliminar imagenes huerfanas y cache de compilacion de mas de 3 dias* y *Reiniciar cAdvisor si se recrearon contenedores* (`when: compose.changed`). Commit `86f82e2`.
  - Grafana: paneles de disponibilidad con `max(up{job=...})` y `avg_over_time(max(up{job="front"})[24h:1m])`; paneles de latencia sin recursos estaticos (igual que la alerta). Commit `86f82e2`.
  - Limpieza unica del almacen huerfano de containerd con `ctr` (imagenes, snapshots y blobs del espacio `moby`), despues de verificar que **ningun contenedor en ejecucion lo usaba** (`ctr containers info` → `Snapshotter` vacio).
- **Nueva ejecucion / resultado:** Ansible `ok=28 changed=6 failed=0`; queda una sola version de cada imagen; `/var/lib/containerd` 12 GB → 1.2 GB; **disco 80 % → 44 %**; 10 contenedores *Up* y la app responde 200; cAdvisor reporta 12 series; el tablero muestra una sola serie "ARRIBA" y memoria/CPU por contenedor (captura `A-05b`).
- **Leccion:** las capturas y la revision del tablero son tambien una forma de prueba: aqui revelaron un riesgo operativo (disco lleno) antes de que disparara la alerta.

---

## Resumen

| Tipo | Cantidad | Incidencias |
|---|---|---|
| Defectos de codigo encontrados por pruebas de carga, alertas o SAST | 3 | 11, 13, 14 |
| Configuracion de infraestructura / monitoreo / operacion | 8 | 3, 6, 7, 12, 15, 16, 18, 19 |
| Entorno y herramientas (incluye CI) | 6 | 1, 2, 5, 8, 10, 17 |
| Riesgo de gobierno (suscripcion compartida) | 1 | 4 |
| Por diseno (seguridad) | 1 | 9 |

Todas las incidencias quedaron resueltas, mitigadas o justificadas; las correcciones estan versionadas en Git y verificadas con una nueva ejecucion.
