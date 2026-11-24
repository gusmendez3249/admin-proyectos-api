# Informe técnico — Circuito DevOps con IaC y despliegue en la nube

|  |  |
|---|---|
| **Asignatura** | Gestión del Proceso de Desarrollo de Software — Unidad III: Proceso de liberación y monitoreo |
| **Proyecto** | admin-proyectos (Gestor de Proyectos) |
| **Estudiante** | _[nombre completo]_ |
| **Grupo / Periodo** | _[grupo]_ / _[periodo]_ |
| **Repositorios** | [admin-proyectos-api](https://github.com/gusmendez3249/admin-proyectos-api) (código, IaC, despliegue, monitoreo, pruebas de carga, documentación) · [admin-proyectos-front](https://github.com/gusmendez3249/admin-proyectos-front) |
| **Entorno en la nube** | Microsoft Azure — `rg-gusmendez-devops`, North Central US |
| **Fecha** | Noviembre 2026 |

---

## 1. Objetivo

Diseñar, implementar y demostrar un circuito DevOps funcional para el sistema *admin-proyectos* (front Spring Boot + API REST Spring Boot + PostgreSQL) que integre control de versiones, integración continua con pruebas, calidad y seguridad, infraestructura como código para aprovisionar y configurar un servidor en la nube, despliegue automatizado, monitoreo y observabilidad, y evaluación del rendimiento, documentando decisiones, resultados e incidencias con evidencias verificables.

## 2. Alcance

**Incluye:**
- Contenerización de la API y el front (Docker) y orquestación local/nube con Docker Compose.
- Pipeline CI en Jenkins: compilación, pruebas (Surefire), cobertura (JaCoCo), calidad (SonarQube + Quality Gate) y SAST (Semgrep).
- Aprovisionamiento en Azure con **Terraform** (red, firewall, IP pública, VM) y configuración/despliegue con **Ansible**.
- Monitoreo y observabilidad: métricas (Prometheus), tablero (Grafana), alertas (reglas + Alertmanager) y logs centralizados (Loki + Alloy).
- Prueba de carga controlada con **k6** y análisis de latencia, throughput y errores.

**No incluye:** alta disponibilidad (una sola VM), HTTPS con dominio propio, trazas distribuidas (ver §9) ni despliegue continuo disparado automáticamente por Jenkins (el despliegue se ejecuta con un comando reproducible; ver §9).

## 3. Tecnologías seleccionadas y justificación

| Capacidad | Herramienta | Versión | Justificación |
|---|---|---|---|
| Versionado | Git + GitHub | — | Historial verificable, dos repositorios (API y front). |
| Lenguaje / framework | Java + Spring Boot | 21 / 3.5.16 | Base del proyecto; LTS. |
| Contenedores | Docker + Docker Compose | 29 | El mismo stack se ejecuta en local y en la nube. |
| CI | Jenkins (en Docker) | LTS JDK 17 + JDK 21 | Pipeline como código (`Jenkinsfile`), usado en Unidades II y III. |
| Pruebas y cobertura | Maven Surefire + JaCoCo | 3.5.6 / 0.8.15 | Reportes estándar; regla `jacoco:check` al 100 % de métodos. |
| Calidad | SonarQube + SonarScanner Maven | 5.5.0.6356 | Quality Gate y deuda técnica. |
| SAST | Semgrep | OWASP Top 10, `p/java`, `p/secrets` + reglas propias | Ligero, reglas versionadas en `.semgrep/`. |
| Nube | Microsoft Azure (VM IaaS) | — | Suscripción *Azure for Students* disponible; una VM permite demostrar aprovisionamiento **y** configuración (un PaaS ocultaría la configuración). |
| Aprovisionamiento | Terraform + proveedor `azurerm` | 1.17 / ~> 4.0 | Declarativo, con `plan` previo y estado; detecta deriva. |
| Configuración y despliegue | Ansible (`ansible-core`) | 2.17 | Idempotente, sin agente (SSH). Se ejecuta en un contenedor porque no corre nativo en Windows. |
| Métricas | Prometheus + Micrometer/Actuator + node-exporter + cAdvisor | 2.53 / — / 1.8.1 / 0.49.1 | Estándar abierto; métricas de aplicación, VM y contenedores. |
| Alertas | Reglas de Prometheus + Alertmanager | 0.27 | Alertas como código, con inhibición. |
| Logs | Loki + Grafana Alloy | 3.1 / 1.2 | Logs de todos los contenedores, consultables en Grafana. |
| Tablero | Grafana | 11.1 | Datasources y dashboard aprovisionados como código. |
| Rendimiento | k6 | 0.52 | Escenario como código, umbrales automáticos, reporte HTML. |

**Terraform vs. Ansible.** Tienen alcances distintos y se usaron juntos: Terraform **crea** los recursos de Azure (qué máquina existe y cómo se conecta a la red) y genera el inventario; Ansible **define el estado interno** de la VM (paquetes, Docker, configuración del demonio, aplicación). Ver la justificación ampliada en [arquitectura.md §4](arquitectura.md#4-justificacion-de-decisiones).

## 4. Arquitectura y flujo

El diagrama de componentes, conexiones, puertos y la explicación del flujo desde el repositorio hasta el despliegue, el monitoreo y la retroalimentación están en **[docs/arquitectura.md](arquitectura.md)**.

Resumen del circuito:

```
git push → GitHub → Jenkins (build, Surefire, JaCoCo, SonarQube+QG, Semgrep)
        → terraform plan/apply (Azure: RG, VNet, NSG, IP, VM) → inventario
        → Ansible (Docker, daemon.json, git clone, .env 600, compose up, verificación)
        → VM: PostgreSQL + API + front  ←  usuarios / k6
        → Prometheus + Alertmanager + Loki → Grafana → incidencias → nuevo commit
```

## 5. Código, IaC e instrucciones de reproducción

### 5.1 Estructura (repo `admin-proyectos-api`)

| Ruta | Contenido |
|---|---|
| `Dockerfile`, `.dockerignore` | Imagen multi-etapa de la API (usuario sin privilegios, HEALTHCHECK). El front tiene el suyo en su repositorio. |
| `deploy/docker-compose.yml` | PostgreSQL 16 + API + front (construido desde su repositorio de GitHub). |
| `deploy/docker-compose.monitoreo.yml`, `deploy/monitoreo/` | Prometheus (+7 reglas), Alertmanager, Grafana (datasources y tablero), Loki, Alloy, node-exporter, cAdvisor. |
| `infra/terraform/` | `versions.tf`, `variables.tf`, `main.tf`, `outputs.tf`, `.terraform.lock.hcl`, `terraform.tfvars.example`. |
| `infra/ansible/` | `playbook.yml`, `ansible.cfg`, `Dockerfile` (imagen de Ansible). |
| `infra/desplegar-ansible.ps1` | Ejecuta el playbook dentro de un contenedor. |
| `pruebas-carga/` | Escenario k6, resultados y análisis. |
| `Jenkinsfile`, `.semgrep/`, `database/` | Pipeline CI, reglas SAST propias y pipeline de BD (Práctica 3). |
| `docs/` | Este informe, arquitectura, bitácora y evidencias. |

### 5.2 Reproducción desde cero

Requisitos: Azure CLI, Terraform ≥ 1.6, Docker Desktop, una suscripción de Azure.

```powershell
# 1. Llave SSH (una vez)
ssh-keygen -t ed25519 -N '""' -f $env:USERPROFILE\.ssh\admin_proyectos_azure
# 2. Sesión de Azure
az login
# 3. Aprovisionamiento
cd infra\terraform
copy terraform.tfvars.example terraform.tfvars    # pegar subscription_id
terraform init
terraform plan -out plan.tfplan                   # revisar: solo creaciones
terraform apply plan.tfplan
# 4. Configuración y despliegue
cd ..\..
powershell -ExecutionPolicy Bypass -File infra\desplegar-ansible.ps1
# 5. Verificación
terraform -chdir=infra\terraform output url_front
terraform -chdir=infra\terraform output url_grafana
```

Ejecutar solo la aplicación en local: `cd deploy; copy .env.example .env; docker compose up -d --build`.

### 5.3 Configuración segura

- **Sin secretos en Git:** `.env`, `terraform.tfvars`, `*.tfstate*`, `.terraform/` e `inventario.ini` están en `.gitignore`; solo se versionan plantillas (`*.example`).
- **Contraseñas generadas en la VM:** Ansible genera contraseñas aleatorias de 24 caracteres para PostgreSQL y Grafana en la primera instalación y las **conserva** en despliegues posteriores; el `.env` queda con permisos `600 root`. Las tareas con secretos usan `no_log: true`.
- **Autenticación sin llaves en archivos:** Terraform usa la sesión de Azure CLI; la VM solo acepta llave SSH (`disable_password_authentication = true`).
- **Mínimo privilegio en red:** solo 8082 (front) y 3000 (Grafana, con usuario y contraseña) abiertos a Internet; 22 y 443 (SSH alternativo para redes que bloquean el 22), 8081 y 9090 solo desde la IP del administrador (detectada automáticamente); PostgreSQL sin puerto publicado.
- **Aplicación:** contraseñas de usuarios con BCrypt, DTOs para evitar *mass assignment* (CWE-915), Actuator expone solo `health` y `prometheus` y en un **puerto interno de administración** (9081/9082) que no se publica fuera de Docker (hallazgo de Semgrep, bitácora #14), contenedores sin root.
- **Suscripción compartida:** todos los recursos en un grupo propio con prefijo único; revisión de `terraform plan` antes de cada `apply` (bitácora #4).

## 6. Servicio en la nube y verificación

**Servicio:** Azure Virtual Machine `vm-gusmendez-devops`, `Standard_B2als_v2` (2 vCPU AMD, 4 GB RAM), Ubuntu 24.04 LTS, disco de 30 GB, IP pública estática con DNS `gusmendez-devops-fa6883.northcentralus.cloudapp.azure.com`, red `10.10.0.0/16` con NSG. 6 recursos de Azure gestionados por Terraform; `terraform plan` reporta *No changes* (sin deriva). Evidencia: [`evidencias/infraestructura-azure.txt`](evidencias/infraestructura-azure.txt).

**Verificación de integración** ([`evidencias/verificacion-servicios.txt`](evidencias/verificacion-servicios.txt)):

| Componente | Verificación | Resultado |
|---|---|---|
| Front | `GET :8082/login` desde Internet | HTTP 200 |
| Back (API) | `/actuator/health` por el puerto interno 9081 (HEALTHCHECK de Docker y Prometheus) | `UP` / *healthy* |
| Actuator no expuesto | `GET :8082/actuator/prometheus` y `:8081/actuator/prometheus` desde Internet | HTTP 404 |
| Front → API → BD | `POST /verificarLogin` con usuario demo | Usuario devuelto (BCrypt validado contra PostgreSQL) |
| Front → API → BD | `POST /consultarProyectos` | Proyectos leídos de PostgreSQL |
| Base de datos | `psql` dentro de la VM | 5 tablas con datos (3 usuarios, 1 proyecto, 1 tarea, 2 comentarios) |
| Aislamiento de la BD | Conexión a 5432 desde Internet | Bloqueada |
| Contenedores | `docker compose ps` | 10 servicios *Up*; api, front, db y cadvisor *healthy* |

## 7. Reportes

### 7.1 Pipeline CI, pruebas, cobertura, calidad y seguridad

Ejecutados en Jenkins durante las Unidades II–III (evidencias originales en el portafolio, carpeta `evidencia-practica3/`: logs de Jenkins, reportes JaCoCo HTML/XML, Surefire, SonarQube y Semgrep antes/después).

| Reporte | Resultado |
|---|---|
| Pipelines Jenkins sobre el **código final** (24 nov) | Los 3 con **SUCCESS**: `admin-proyectos-api #8` (`0ab3c00`), `admin-proyectos-front #6` (`58d0e9b`), `admin-proyectos-bd #3`. Antes: api #6 y front #4 FAILURE por token de SonarQube vencido (bitácora #17). Logs en el portafolio `evidencia-unidad3/04-ci-calidad-seguridad/` |
| Pruebas (Surefire) | API: 63 pruebas · front: 39 pruebas (38 + regresión AUT-E17 de esta unidad), 0 fallos; BD: BD-01…BD-08 OK |
| Cobertura (JaCoCo) | **100 %** de métodos, líneas y ramas en ambos; la regla `jacoco:check` rompe el build si baja. Sin exclusiones (el `main` se prueba con `mockStatic`). |
| SonarQube (Community Build 26.10) | Re-ejecución final: Quality Gate **OK** en ambos; 0 bugs, 0 vulnerabilidades, 0 code smells, 0 hotspots; calificaciones A. Antes de la corrección: 18 vulnerabilidades `java:S4684` (CWE-915, calificación D), corregidas con DTOs |
| Semgrep (SAST) | OWASP Top 10 + `p/java` + `p/secrets` + reglas propias, excluyendo `target/`. Ejecución final: 1 hallazgo por repositorio, `spring-actuator-dangerous-endpoints-enabled`, **mitigado** moviendo Actuator a un puerto interno (bitácora #14); BD: 0. En la Práctica 3, junto con SonarQube y las pruebas se clasificaron 17 incidencias: 12 corregidas, 1 falso positivo justificado, 3 aceptadas con justificación (p. ej. hashes BCrypt de la contraseña demo documentada) y 1 informativa |

### 7.2 Infraestructura como código

| Ejecución | Resultado |
|---|---|
| `terraform validate` | *Success! The configuration is valid.* |
| `terraform apply` inicial | *9 added, 0 changed, 0 destroyed* |
| `terraform apply` por cambio de IP (×2) | *0 added, 1 changed, 0 destroyed* (solo la regla del NSG) |
| `terraform plan` final | *No changes. Your infrastructure matches the configuration.* |
| Ansible, primera ejecución | `ok=17 changed=9 failed=0` |
| Ansible, con monitoreo | `ok=21 changed=3 failed=0` (idempotencia: solo cambian repo, `.env` y contenedores) |
| Ansible, configuración de Docker | `ok=23 changed=5 failed=0` (handler *Reiniciar Docker*) |
| Ansible, redespliegues posteriores | `ok=21–22 changed=1 failed=0` |
| Ansible por SSH en 443 (red que bloquea el 22) | `ok=25 changed=4 failed=0` |

### 7.3 Monitoreo y observabilidad

| Elemento | Implementación | Evidencia |
|---|---|---|
| Métricas de aplicación | Actuator + Micrometer (`http_server_requests` con histograma, JVM, Tomcat) en API y front, leídas por el puerto interno 9081/9082 | 5/5 objetivos *up* en Prometheus |
| Métricas de infraestructura | node-exporter (CPU, memoria, disco, swap de la VM) y cAdvisor (CPU y memoria por contenedor) | Paneles *Recursos* del tablero |
| Disponibilidad | `up{job}` y `avg_over_time(up[24h])` | Paneles *API/Front arriba* y *Disponibilidad 24 h* |
| Errores | Tasa de 4xx/5xx por servicio | Panel *Errores* y stat *Errores 5xx* |
| Logs | Alloy → Loki, etiquetados por servicio | Panel *Logs de API y front* y *Líneas con ERROR/WARN* |
| Tablero | Grafana `admin-proyectos - Aplicacion e infraestructura` (21 paneles, aprovisionado como código) | `deploy/monitoreo/grafana/dashboards/admin-proyectos.json` |
| Alertas | 7 reglas: `ServicioCaido`, `TasaErrores5xxAlta`, `LatenciaP95Alta`, `HeapJVMAlto`, `CPUAltaVM`, `MemoriaAltaVM`, `DiscoCasiLleno` → Alertmanager (con inhibición) | `promtool check rules` → *SUCCESS: 7 rules found* |

**Prueba de alerta** ([`evidencias/prueba-alerta.log`](evidencias/prueba-alerta.log)): se detuvo el contenedor de la API de forma controlada.

| Tiempo (UTC) | Evento |
|---|---|
| 03:54:05 | API detenida; el front responde **503** controlado |
| 03:54:41 | `ServicioCaido{job="api"}` → **pending** |
| 03:55:25 | Tras el `for: 1m` → **firing**; Alertmanager la recibe (*active*) |
| 03:56:31 | API levantada |
| 03:57:32 | Alerta **resuelta** automáticamente; Alertmanager sin alertas; front HTTP 200 |

La prueba se repitió el 24/11 durante la toma de capturas (`A-07`: *firing*; `A-07b`: resuelta). Además, los 503 de la caída dispararon de forma natural `TasaErrores5xxAlta` (> 5 % de respuestas 5xx en 5 min). La prueba reveló que el front no registraba la falla en sus logs (bitácora #13), lo que se corrigió y verificó en Loki.

**Trazas:** no se implementaron trazas distribuidas (ver §9).

### 7.4 Rendimiento

Detalle completo en **[pruebas-carga/README.md](../pruebas-carga/README.md)**. Escenario de 9 minutos con hasta **50 usuarios concurrentes** (login con BCrypt, consulta de proyectos y tareas).

| Métrica | Ejecución 1 (antes) | Ejecución 2 (después de corregir) |
|---|---|---|
| Throughput | 18.41 req/s | **18.74 req/s** |
| Tasa de error | 25.00 % ✗ | **0.00 %** ✓ |
| Latencia p95 / p99 | 195.8 / 320.2 ms | **190.0 / 301.7 ms** ✓ |
| Umbrales k6 | 3 rotos (exit 99) | **Todos OK (exit 0)** |
| CPU VM máx. / RAM máx. (Prometheus) | — | 53.1 % / 34.0 % |

**Análisis:** la latencia está dominada por la red México–Illinois (≈ 50–70 ms); dentro del servidor el p95 es de 122 ms (front) y 128 ms (API). El login es la operación más costosa por BCrypt (p95 188 ms en la API), un costo de seguridad aceptado. Con 50 usuarios la CPU llegó a 53 %: hay holgura. La primera ejecución encontró un defecto real de interoperabilidad (cabecera `Transfer-Encoding` duplicada) que las pruebas funcionales no detectaban.

## 8. Bitácora de incidencias

Registro completo con problema, evidencia, diagnóstico, corrección, nueva ejecución y resultado en **[docs/bitacora-incidencias.md](bitacora-incidencias.md)** (19 incidencias). Las más relevantes:

| # | Incidencia | Corrección |
|---|---|---|
| 11 | 100 % de los logins fallan bajo carga por `Transfer-Encoding` duplicado | Reenviar solo estado y cuerpo; prueba de regresión AUT-E17; k6 pasa a 0 % de errores |
| 12 | cAdvisor sin métricas por contenedor (Docker 29 + containerd snapshotter) | `daemon.json` gestionado por Ansible con handler |
| 13 | El front respondía 503 sin dejar rastro en los logs | `log.warn` en el manejador; verificado en Loki |
| 14 | Semgrep: `/actuator/prometheus` accesible desde Internet | Actuator en puerto interno 9081/9082; ahora HTTP 404 desde fuera |
| 16 | La red bloquea SSH (22): riesgo para la demo en vivo | SSH alternativo en 443 gestionado por Terraform + Ansible |
| 18 | Falso positivo de `LatenciaP95Alta` con poco tráfico (recursos estáticos) | La regla excluye `/**` y exige ≥ 0.1 req/s |
| 19 | Disco al 80 % (imágenes viejas + 12 GB huérfanos de containerd) y cAdvisor ciego tras cada despliegue | Limpieza automática en Ansible, reinicio de cAdvisor; disco 80 % → 44 % |
| 7 | Riesgo de romper la conexión a la BD al regenerar contraseñas | El playbook conserva los secretos existentes |
| 6 | SSH bloqueado por IP dinámica | `terraform apply` re-detecta la IP (solo cambia la regla) |
| 4 | Suscripción compartida con recursos ajenos | Grupo y prefijo propios; revisión de `plan` |

## 9. Conclusiones, limitaciones y trabajo futuro

### Conclusiones
1. El circuito DevOps quedó **completo y verificable**: cada cambio pasa por pruebas, cobertura, calidad y SAST; la infraestructura se crea y configura con código versionado y reproducible; la aplicación corre en Azure integrada (front → API → PostgreSQL) y está monitoreada con métricas, logs, tablero y alertas.
2. **Terraform y Ansible se complementan:** Terraform hizo visible cada cambio antes de aplicarlo (`plan`) y detecta deriva; Ansible permitió corregir la configuración del servidor (Docker, secretos) de forma idempotente, sin entrar a mano a la VM.
3. **La retroalimentación funcionó en la práctica:** la prueba de carga, la prueba de alertas y el SAST encontraron tres defectos reales (#11, #13, #14) que volvieron al repositorio como commits con su prueba de regresión, y el monitoreo encontró un problema de infraestructura (#12).
4. El sistema cumple los criterios de rendimiento con margen (0 % de errores, p95 de 190 ms, CPU máx. 53 % con 50 usuarios).

### Limitaciones
- **Una sola VM:** no hay alta disponibilidad ni balanceo; una falla de la VM detiene el servicio.
- **HTTP sin TLS:** no se configuró HTTPS (requeriría dominio y certificado, p. ej. Caddy + Let's Encrypt).
- **Despliegue continuo semiautomático:** Jenkins corre en el equipo local (Docker) y el despliegue se ejecuta con `desplegar-ansible.ps1`; no se conectó Jenkins a la VM para no exponer la llave SSH en un servidor CI sin endurecer.
- **Trazas distribuidas:** no se implementaron; con dos servicios y llamadas síncronas simples, las métricas por endpoint y los logs fueron suficientes para diagnosticar. Sería el siguiente paso con OpenTelemetry + Tempo.
- **Alertas sin canal externo:** Alertmanager registra las alertas pero no envía correo/Slack (requeriría credenciales).
- **Suscripción prestada y con política de regiones:** limita la región y obliga a apagar la VM cuando no se usa.
- **Estado de Terraform local:** para trabajo en equipo debería guardarse en un backend remoto (Azure Storage) con bloqueo.

### Trabajo futuro
Etapa de despliegue en Jenkins con credencial SSH, backend remoto de Terraform, HTTPS, receptor de alertas por correo, trazas con OpenTelemetry y prueba de estrés hasta el punto de quiebre.

## 10. Referencias

- Repositorio API: https://github.com/gusmendez3249/admin-proyectos-api — commits de esta unidad: `f1f6215`, `e5e293c`, `ef65b53`, `412d1d8`, `7c040c3`, `0ab3c00` (y la documentación).
- Repositorio front: https://github.com/gusmendez3249/admin-proyectos-front — commits: `981d14a`, `71c9f73`, `36cc74b`, `58d0e9b`.
- Terraform AzureRM provider: https://registry.terraform.io/providers/hashicorp/azurerm/latest/docs
- Ansible: https://docs.ansible.com/ansible/latest/
- Docker Compose: https://docs.docker.com/compose/
- Spring Boot Actuator / Micrometer Prometheus: https://docs.spring.io/spring-boot/reference/actuator/metrics.html
- Prometheus (alerting rules): https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/
- Grafana provisioning: https://grafana.com/docs/grafana/latest/administration/provisioning/
- Grafana Loki y Alloy: https://grafana.com/docs/loki/latest/ · https://grafana.com/docs/alloy/latest/
- cAdvisor: https://github.com/google/cadvisor
- k6: https://grafana.com/docs/k6/latest/
- Jenkins Pipeline, SonarQube, JaCoCo, Semgrep: ver informe de la Práctica 3.

## 11. Anexos — capturas identificadas

Capturas tomadas el 24/11/2026 sobre el entorno real; se incluyen en el **Anexo D** de este documento y en el portafolio (`evidencia-unidad3/capturas/`).

| ID | Captura | Criterio |
|---|---|---|
| A-01 | Portal de Azure: grupo `rg-gusmendez-devops` con sus 6 recursos | Despliegue cloud / IaC |
| A-02 | `terraform plan` → *No changes* (sin deriva) | IaC |
| A-03 | Ansible por SSH 443: `PLAY RECAP ok=25 changed=4 failed=0` | IaC |
| A-04 / A-04b / A-04c | Login de la app, "Mis Proyectos" y tablero de tareas (front → API → BD) | Integración |
| A-05 / A-05b | Grafana durante la prueba de carga y tablero actual corregido | Monitoreo / rendimiento |
| A-06 | Grafana Explore + Loki: WARN del front durante la caída | Observabilidad |
| A-07 / A-07b / A-07c | Alerta `ServicioCaido` en *firing*, alertas resueltas y falso positivo corregido (#18) | Alertas |
| A-08 | Reporte HTML de k6 (ejecución 2) | Rendimiento |
| A-09 | Prometheus: 5/5 objetivos *UP* | Monitoreo |
| A-10a / A-10b / A-10c | Jenkins (3 pipelines en verde, etapas) y SonarQube (Quality Gate *Passed*) | CI/CD, calidad y seguridad |
