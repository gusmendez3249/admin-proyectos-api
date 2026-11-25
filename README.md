# admin-proyectos-api

API REST del sistema **admin-proyectos** (Gestor de Proyectos) en Java 21 / Spring Boot 3.5 con PostgreSQL, y repositorio central del circuito DevOps del proyecto: infraestructura como código, despliegue, monitoreo y pruebas de carga. El front está en [admin-proyectos-front](https://github.com/gusmendez3249/admin-proyectos-front).

| Documento | Contenido |
|---|---|
| [docs/informe-tecnico.md](docs/informe-tecnico.md) | Informe técnico de la Unidad III |
| [docs/arquitectura.md](docs/arquitectura.md) | Diagrama de arquitectura DevOps, flujo y justificación |
| [docs/bitacora-incidencias.md](docs/bitacora-incidencias.md) | Bitácora de incidencias |
| [infra/README.md](infra/README.md) | Terraform (Azure) + Ansible: aprovisionamiento y despliegue |
| [pruebas-carga/README.md](pruebas-carga/README.md) | Prueba de carga con k6: resultados y análisis |

| Carpeta | Qué contiene |
|---|---|
| `src/` | Código y pruebas de la API (JUnit, JaCoCo 100 %) |
| `Jenkinsfile`, `.semgrep/`, `database/` | Pipeline CI: build, pruebas, SonarQube, Semgrep; pipeline de BD |
| `ci/` | Servidor de CI en Azure: Jenkins + SonarQube (Docker Compose, versiones fijas) |
| `Dockerfile`, `deploy/` | Imagen de la API y stack Docker Compose (app + monitoreo) |
| `infra/` | Terraform y Ansible (VM de la aplicación y VM de CI) |
| `pruebas-carga/` | Escenario k6 y resultados |

## Ejecución local rápida

```powershell
cd deploy
copy .env.example .env        # cambiar las contraseñas
docker compose up -d --build  # front: http://localhost:8082 · Grafana: http://localhost:3000
```
