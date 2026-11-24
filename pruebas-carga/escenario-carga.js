// Prueba de carga controlada con k6 contra el entorno propio en Azure.
// Flujo de un usuario: abrir login -> iniciar sesion -> listar proyectos -> ver tareas de un proyecto.
// Solo operaciones de lectura (no modifica la base de datos).
//
// Ejecucion (desde la raiz del repo, ver README.md de esta carpeta):
//   docker run --rm -v "${PWD}/pruebas-carga:/scripts" -e BASE_URL=http://<host>:8082 \
//     grafana/k6:0.52.0 run /scripts/escenario-carga.js
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';
const USUARIO = { correo_electronico: 'demo1@gestor.test', contrasena: 'Demo1234' }; // usuario demo documentado

const loginFallido = new Rate('login_fallido');
const duracionFlujo = new Trend('duracion_flujo_completo', true);

export const options = {
  scenarios: {
    usuarios_concurrentes: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 10 },  // calentamiento
        { duration: '2m', target: 25 },  // subida
        { duration: '3m', target: 25 },  // carga sostenida
        { duration: '1m', target: 50 },  // pico (estres moderado)
        { duration: '1m', target: 50 },
        { duration: '1m', target: 0 },   // bajada
      ],
      gracefulRampDown: '30s',
    },
  },
  // Criterios de aceptacion: si no se cumplen, k6 termina con codigo de error
  thresholds: {
    http_req_failed: ['rate<0.01'],                       // menos de 1% de errores
    http_req_duration: ['p(95)<1000'],                    // p95 global < 1 s
    'http_req_duration{endpoint:verificarLogin}': ['p(95)<1500'], // BCrypt es costoso a proposito
    'http_req_duration{endpoint:consultarProyectos}': ['p(95)<800'],
    login_fallido: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const JSON_HDR = { headers: { 'Content-Type': 'application/json' } };

export default function () {
  const inicio = Date.now();

  group('01 abrir login', () => {
    const r = http.get(`${BASE_URL}/login`, { tags: { endpoint: 'login' } });
    check(r, { 'login 200': (x) => x.status === 200 });
  });
  sleep(1);

  group('02 iniciar sesion', () => {
    const r = http.post(`${BASE_URL}/verificarLogin`, JSON.stringify(USUARIO),
      Object.assign({}, JSON_HDR, { tags: { endpoint: 'verificarLogin' } }));
    const ok = check(r, {
      'verificarLogin 200': (x) => x.status === 200,
      'credenciales aceptadas': (x) => x.status === 200 && x.json('nombre') !== '000',
    });
    loginFallido.add(!ok);
  });
  sleep(1);

  let idProyecto = null;
  group('03 listar proyectos', () => {
    const r = http.post(`${BASE_URL}/consultarProyectos`, null, { tags: { endpoint: 'consultarProyectos' } });
    check(r, { 'consultarProyectos 200': (x) => x.status === 200 });
    if (r.status === 200) {
      const proyectos = r.json();
      if (Array.isArray(proyectos) && proyectos.length > 0) {
        idProyecto = proyectos[Math.floor(Math.random() * proyectos.length)].id_proyecto;
      }
    }
  });
  sleep(1);

  if (idProyecto !== null) {
    group('04 ver tareas del proyecto', () => {
      const r = http.post(`${BASE_URL}/obtenerTareas`, JSON.stringify({ id_proyecto: idProyecto }),
        Object.assign({}, JSON_HDR, { tags: { endpoint: 'obtenerTareas' } }));
      check(r, { 'obtenerTareas 200': (x) => x.status === 200 });
    });
  }

  duracionFlujo.add(Date.now() - inicio);
  sleep(Math.random() * 2 + 1); // tiempo de lectura del usuario: 1-3 s
}
