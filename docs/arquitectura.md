# Arquitectura DevOps — admin-proyectos

## 1. Diagrama de componentes y flujo

```mermaid
flowchart LR
    dev["Desarrollador<br/>(Windows)"]

    subgraph GH["GitHub (control de versiones)"]
        repoApi["admin-proyectos-api<br/>codigo + IaC + deploy + monitoreo"]
        repoFront["admin-proyectos-front<br/>codigo"]
    end

    subgraph IAC["Infraestructura como codigo"]
        tf["Terraform<br/>aprovisionamiento"]
        ans["Ansible<br/>configuracion + despliegue"]
    end

    subgraph AZCI["Microsoft Azure — Chile Central (servidor de CI)"]
        nsgci{{"NSG CI<br/>8080, 9000, 22/443<br/>solo admin"}}
        subgraph CI["VM CI · B2as_v2 (2 vCPU / 8 GB) · Docker Compose"]
            jenkins["Jenkins 2.587<br/>Jenkinsfile (front, API, BD)"]
            jacoco["Maven + Surefire + JaCoCo<br/>pruebas y cobertura"]
            semgrep["Semgrep<br/>SAST OWASP + reglas propias"]
            sonar["SonarQube 26.10<br/>Quality Gate"]
            pgci[("PostgreSQL CI<br/>BD efimera")]
        end
    end

    subgraph AZ["Microsoft Azure — North Central US (aplicacion)"]
        nsg{{"NSG / firewall<br/>8082, 3000 publicos<br/>22/443, 8081, 9090 solo admin"}}
        subgraph VM["VM Ubuntu 24.04 · B2als_v2 (2 vCPU / 4 GB) · Docker Compose"]
            front["front<br/>Spring Boot + Thymeleaf :8082"]
            api["api<br/>Spring Boot REST :8081"]
            db[("PostgreSQL 16<br/>sin puerto publico")]
            subgraph MON["Monitoreo y observabilidad"]
                prom["Prometheus<br/>metricas + 7 reglas"]
                am["Alertmanager"]
                graf["Grafana<br/>tablero"]
                loki["Loki<br/>logs"]
                alloy["Alloy"]
                node["node-exporter<br/>VM"]
                cad["cAdvisor<br/>contenedores"]
            end
        end
    end

    usuario["Usuario<br/>(navegador)"]
    k6["k6<br/>prueba de carga"]

    dev -- "git push" --> GH
    GH -- "checkout" --> jenkins
    jenkins --> jacoco & sonar & semgrep & pgci
    dev -- "navegador :8080 / :9000" --> nsgci --> jenkins
    dev -- "terraform apply" --> tf
    tf -- "crea RG, VNet, NSG, IP, VM<br/>genera inventario" --> AZ
    tf -- "crea VM de CI" --> AZCI
    dev -- "desplegar-ansible.ps1" --> ans
    ans -- "SSH: Docker, git clone,<br/>.env, compose up" --> VM
    ans -- "SSH: Jenkins + SonarQube" --> CI
    VM -. "clona y construye imagenes" .-> GH

    usuario -- "HTTP :8082" --> nsg --> front
    k6 -- "carga controlada" --> nsg
    front -- "REST (red interna)" --> api
    api -- "JDBC" --> db

    prom -- "/actuator/prometheus" --> api & front
    prom --> node & cad
    prom -- "alertas" --> am
    alloy -- "logs Docker" --> loki
    graf --> prom & loki & am
    dev -- "observa :3000" --> graf
    graf -. "retroalimentacion:<br/>incidencias y mejoras" .-> dev
```

## 2. Flujo de extremo a extremo

1. **Codigo y versionado.** El desarrollador sube cambios a GitHub (dos repos: API y front). Todo lo de infraestructura, despliegue y monitoreo vive en el repo de la API.
2. **Integracion continua (Jenkins en Azure).** Jenkins y SonarQube corren en su propia VM de Azure (Chile Central), creada con Terraform y configurada con Ansible (`playbook-ci.yml`). Cada ejecucion del `Jenkinsfile` hace checkout, compila con Maven, ejecuta las pruebas (Surefire), mide cobertura (JaCoCo, regla de 100% de metodos), analiza calidad en SonarQube con Quality Gate y ejecuta SAST con Semgrep (OWASP Top 10, `p/java`, `p/secrets` y reglas propias).
3. **Aprovisionamiento (Terraform).** `terraform plan/apply` crea en Azure el grupo de recursos y, para cada servidor (aplicacion y CI), su red, firewall, IP publica con DNS y VM. Al final genera el inventario de Ansible con la IP real.
4. **Configuracion y despliegue (Ansible).** Por SSH instala Docker, crea swap, clona el repo, escribe el `.env` (permisos 600, contrasenas conservadas entre despliegues) y ejecuta `docker compose up --build`. Termina verificando la salud del front, Grafana, Prometheus y todos los objetivos de monitoreo.
5. **Ejecucion.** En la VM de la aplicacion corren 10 contenedores (PostgreSQL, API, front y el monitoreo); en la VM de CI, 4 (Jenkins, SonarQube, su PostgreSQL y el PostgreSQL efimero del pipeline de BD).
6. **Monitoreo.** Prometheus recolecta metricas de la aplicacion (Actuator/Micrometer), de la VM (node-exporter) y de cada contenedor (cAdvisor); evalua 7 reglas de alerta y las envia a Alertmanager. Alloy recolecta los logs de Docker y los guarda en Loki. Grafana muestra todo en un tablero.
7. **Retroalimentacion.** Las alertas, el tablero y las pruebas de carga (k6) generan hallazgos que se registran en la bitacora de incidencias y vuelven al paso 1 como cambios de codigo o de infraestructura.

## 3. Conexiones y puertos

| Origen | Destino | Puerto | Exposicion |
|---|---|---|---|
| Usuario | front | 8082/tcp | Internet |
| Administrador | Grafana | 3000/tcp | Internet (con usuario y contrasena) |
| Administrador | SSH, API, Prometheus | 22 (y 443 alternativo), 8081, 9090/tcp | Solo IP del administrador (NSG) |
| front | api | 8081 | Red interna de Docker |
| api | PostgreSQL | 5432 | Red interna de Docker (sin puerto publicado) |
| Prometheus | api, front (Actuator), node-exporter, cAdvisor | 9081, 9082, 9100, 8080 | Red interna (puertos de administración no publicados) |
| Alloy | Loki | 3100 | Red interna |
| Administrador | Jenkins, SonarQube (VM de CI) | 8080, 9000/tcp | Solo IP del administrador (NSG de CI) |
| Jenkins | GitHub | 443 (salida) | Checkout de los repositorios |
| Jenkins | SonarQube, PostgreSQL CI | 9000, 5432 | Red interna de Docker en la VM de CI |
| SonarQube | Jenkins (webhook del Quality Gate) | 8080 | Red interna de Docker en la VM de CI |
| Ansible | VMs de la aplicacion y de CI | 22 o 443 | Llave SSH ed25519 |

## 4. Justificacion de decisiones

| Decision | Alternativas | Justificacion |
|---|---|---|
| **Azure** (VM IaaS) | AWS, GCP, Oracle, PaaS (App Service) | Suscripcion Azure for Students disponible sin tarjeta. Una VM da control total para instalar el monitoreo y demostrar IaC completo (aprovisionamiento + configuracion), cosa que un PaaS oculta. La politica de la suscripcion solo permite 5 regiones; se eligio North Central US por cercania a Mexico. |
| **Terraform** para aprovisionar | ARM/Bicep, CLI | Declarativo, multi-nube, con `plan` previo y estado: muestra exactamente que se crea/cambia/destruye y es reproducible. |
| **Ansible** para configurar | cloud-init, scripts bash | Idempotente, sin agente (solo SSH) y legible. Terraform y Ansible tienen alcances distintos: uno crea la maquina, el otro define su estado interno. |
| **CI en Azure** en una VM aparte (Chile Central) | Jenkins local, CI en la misma VM de la app, servicio gestionado (GitHub Actions) | El CI queda en la nube como el resto del circuito, sin depender del equipo del desarrollador. VM aparte porque Jenkins + SonarQube necesitan ~8 GB y no deben competir con la aplicacion. Region distinta para no agotar la cuota de vCPU de North Central US, compartida con otro proyecto de la suscripcion. |
| **Docker Compose** en la VM | Kubernetes (AKS) | El mismo stack se prueba en local y en la nube. Para 3 servicios y 1 VM, Kubernetes agregaria complejidad y costo sin beneficio para el objetivo del curso. |
| **B2als_v2** (2 vCPU / 4 GB) | B1s, B2s | La opcion mas economica que soporta 2 JVM + PostgreSQL + monitoreo; se agrego swap de 2 GB como margen. |
| **Prometheus + Grafana + Loki** | Azure Monitor | Herramientas abiertas y portables, configuradas como codigo y versionadas en el repo. |
| **k6** | JMeter | Escenario como codigo (JavaScript), umbrales de aceptacion automaticos y reporte HTML. |

## 5. Seguridad

- Acceso a la VM solo con llave SSH; contrasenas deshabilitadas.
- Firewall con minimo privilegio: solo 8082 y 3000 abiertos al publico.
- Contenedores de la aplicacion con usuario sin privilegios y limites de memoria.
- Sin secretos en el repositorio: `.env`, `terraform.tfvars`, `*.tfstate` e inventario estan en `.gitignore`. Las contrasenas se generan aleatoriamente en la VM (permisos 600).
- Contrasenas de usuarios cifradas con BCrypt; Actuator solo expone `health` y `prometheus`, en un puerto interno (9081/9082) no publicado.
- SAST (Semgrep) y analisis de calidad (SonarQube) en el pipeline.
