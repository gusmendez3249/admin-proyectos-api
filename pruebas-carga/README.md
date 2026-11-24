# Evaluacion del rendimiento (k6)

Prueba de carga **controlada** contra el entorno propio en Azure (`rg-gusmendez-devops`), con limite de usuarios y duracion, sin afectar servicios de terceros. Solo se ejecutan operaciones de lectura.

## 1. Escenario

| Elemento | Valor |
|---|---|
| Herramienta | k6 0.52.0 (en contenedor Docker), escenario como codigo: [`escenario-carga.js`](escenario-carga.js) |
| Objetivo | `http://gusmendez-devops-fa6883.northcentralus.cloudapp.azure.com:8082` (front → API → PostgreSQL) |
| Origen de la carga | Equipo del desarrollador (Mexico) → Azure North Central US (incluye latencia de red real) |
| Flujo por usuario virtual | `GET /login` → `POST /verificarLogin` (BCrypt) → `POST /consultarProyectos` → `POST /obtenerTareas`, con 1 s entre pasos y 1–3 s de "lectura" |
| Perfil de carga (`ramping-vus`) | 0→10 VU (1 min) · 10→25 (2 min) · 25 sostenido (3 min) · 25→50 pico (1 min) · 50 sostenido (1 min) · 50→0 (1 min) = **9 min, maximo 50 usuarios concurrentes** |
| Umbrales de aceptacion | errores `< 1%` · p95 global `< 1 s` · p95 login `< 1.5 s` · p95 consultarProyectos `< 800 ms` · checks `> 99%` |

```powershell
# Ejecucion (desde la raiz del repo de la API)
docker run --rm -v "${PWD}/pruebas-carga:/scripts" `
  -e BASE_URL=http://<dns-de-la-vm>:8082 `
  -e K6_WEB_DASHBOARD=true -e K6_WEB_DASHBOARD_EXPORT=/scripts/resultados/reporte-k6.html `
  grafana/k6:0.52.0 run --summary-export=/scripts/resultados/resumen-k6.json /scripts/escenario-carga.js
```

## 2. Resultados

Reportes originales: `resultados/<ejecucion>/` → `salida-k6.txt` (consola), `resumen-k6.json` (resumen), `reporte-k6.html` (tablero web de k6 exportado) y `metricas-servidor-prometheus.json` (lado servidor).

### 2.1 Comparacion antes / despues de la correccion

| Metrica (cliente k6) | Ejecucion 1 (antes) | Ejecucion 2 (despues) | Umbral |
|---|---|---|---|
| Peticiones totales | 10 008 | 10 128 | — |
| **Throughput** | 18.41 req/s | **18.74 req/s** | — |
| Iteraciones (flujos completos) | 2 502 (4.60/s) | 2 532 (4.69/s) | — |
| **Tasa de error** `http_req_failed` | **25.00 %** ✗ | **0.00 %** ✓ | < 1 % |
| Checks | 60.00 % ✗ | 100.00 % (12 660/12 660) ✓ | > 99 % |
| Latencia promedio | 99.25 ms | 96.58 ms | — |
| Latencia mediana | 71.14 ms | 69.42 ms | — |
| **Latencia p95** | 195.81 ms | **189.98 ms** ✓ | < 1 000 ms |
| Latencia p99 | 320.22 ms | 301.71 ms | — |
| Latencia maxima | 653.58 ms | 580.50 ms | — |
| p95 `verificarLogin` | 267.60 ms | 260.73 ms ✓ | < 1 500 ms |
| p95 `consultarProyectos` | 85.91 ms | 81.97 ms ✓ | < 800 ms |
| Duracion del flujo completo (p95) | 3.59 s | 3.49 s | — |
| Codigo de salida de k6 | 99 (umbrales rotos) | **0 (todos los umbrales OK)** | — |

La ejecucion 1 revelo un defecto real: el 100% de los `POST /verificarLogin` fallaron con `too many transfer encodings: ["chunked" "chunked"]` (ver [bitacora, incidencia 11](../docs/bitacora-incidencias.md)). Tras corregir el front (commit `71c9f73`) y redesplegar, la ejecucion 2 cumplio todos los umbrales.

### 2.2 Lado servidor durante la ejecucion 2 (Prometheus, 03:37–03:46 UTC)

| Metrica | Valor |
|---|---|
| Peticiones atendidas por el front / la API | 10 338 / 7 808 |
| Respuestas 5xx | **0** |
| p95 del front (tiempo dentro del servidor) | 122 ms |
| p95 de la API | 128 ms |
| p95 de la API en `/getUsuarioLogin` (BCrypt) | 188 ms |
| CPU de la VM: promedio / maximo | 29.4 % / **53.1 %** |
| RAM de la VM: maximo | 34.0 % (de 3.8 GB) |
| Swap usado: maximo | 81 MB (de 2 GB) |
| Heap JVM maximo: API / front | 50 MB / 33 MB |

## 3. Analisis

1. **La latencia esta dominada por la red, no por la aplicacion.** La peticion mas rapida tardo 27 ms y la mediana 69 ms medidas desde Mexico; dentro del servidor el p95 del front es 122 ms y el del cliente 190 ms. Aproximadamente 50–70 ms de cada peticion son ida y vuelta por Internet hasta Illinois.
2. **El inicio de sesion es la operacion mas costosa**, como se esperaba: BCrypt es lento a proposito para resistir ataques de fuerza bruta (p95 de 188 ms en la API frente a ~70 ms de las consultas). Es un costo de seguridad aceptado.
3. **Hay holgura de capacidad.** Con 50 usuarios concurrentes la CPU llego a 53% como maximo, la RAM a 34% y el heap de las JVM se mantuvo bajo (50 MB). No hubo errores ni degradacion de latencia en el pico: el p99 se mantuvo cerca de 300 ms.
4. **El throughput (≈18.7 req/s) esta limitado por el escenario, no por el servidor.** Cada usuario hace 4 peticiones y espera ~5.4 s por ciclo (tiempos de "lectura" realistas). El servidor no se satura, asi que mas usuarios aumentarian el throughput de forma aproximadamente lineal hasta acercarse al limite de CPU (2 vCPU).
5. **Las pruebas de carga encuentran defectos que las pruebas funcionales no detectan.** El defecto de cabeceras duplicadas paso desapercibido en el navegador y en 38 pruebas automatizadas; k6 lo detecto porque su cliente HTTP es estricto.

## 4. Conclusiones

- Con la correccion aplicada, el sistema **cumple todos los criterios de aceptacion** con 50 usuarios concurrentes: 0% de errores, p95 de 190 ms (5 veces por debajo del umbral de 1 s) y uso maximo de CPU de 53%.
- La VM `B2als_v2` (2 vCPU / 4 GB) es suficiente para la carga del proyecto con margen; el primer recurso en agotarse con mas carga seria la CPU (BCrypt + 2 JVM).
- El monitoreo permitio correlacionar la vista del cliente (k6) con la del servidor (Prometheus/Grafana) en la misma ventana de tiempo.

## 5. Limitaciones

- Una sola ubicacion de origen (red domestica); la latencia incluye Internet. No se midio desde la misma region de Azure.
- No se busco el punto de quiebre (prueba de estres hasta fallar) para no consumir de mas el credito de una suscripcion compartida; el maximo fue 50 usuarios.
- Base de datos con pocos registros de demostracion; con mas datos las consultas sin paginacion podrian ser mas lentas.
- No se midio la operacion de escritura (alta de proyectos/tareas) para no ensuciar la base de datos.
