-- Pruebas automatizadas de la base de datos dbgestor (pipeline de base de datos).
-- Se ejecutan con:  psql -v ON_ERROR_STOP=1 -f database/pruebas/validar_esquema.sql
-- Cada bloque lanza una excepcion (y detiene el pipeline) si la verificacion falla.

-- BD-01 Existen las 5 tablas que mapean las entidades JPA
DO $$
DECLARE
  faltantes text;
BEGIN
  SELECT string_agg(t.nombre, ', ') INTO faltantes
  FROM (VALUES ('usuario'), ('proyectos'), ('tareas'), ('comentarios'), ('asignaciones')) AS t (nombre)
  WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.tables AS it
    WHERE it.table_schema = 'public' AND it.table_name = t.nombre
  );
  IF faltantes IS NOT NULL THEN
    RAISE EXCEPTION 'BD-01 FALLA: faltan tablas: %', faltantes;
  END IF;
  RAISE NOTICE 'BD-01 OK: existen las 5 tablas';
END $$;

-- BD-02 Las columnas coinciden con los atributos de las entidades (nombre y tipo)
DO $$
DECLARE
  diferencias text;
BEGIN
  WITH esperado (tabla, columna, tipo) AS (
    VALUES
    ('usuario', 'id_usuario', 'integer'),
    ('usuario', 'nombre', 'character varying'),
    ('usuario', 'correo_electronico', 'character varying'),
    ('usuario', 'contrasena', 'character varying'),
    ('usuario', 'rol', 'character varying'),
    ('usuario', 'imagen_usuario', 'character varying'),
    ('proyectos', 'id_proyecto', 'integer'),
    ('proyectos', 'nombre', 'character varying'),
    ('proyectos', 'descripcion', 'character varying'),
    ('proyectos', 'fecha_inicio', 'character varying'),
    ('proyectos', 'fecha_finalizacion', 'character varying'),
    ('proyectos', 'estado_proyecto', 'character varying'),
    ('proyectos', 'fondo', 'character varying'),
    ('tareas', 'id_tarea', 'integer'),
    ('tareas', 'nombre', 'character varying'),
    ('tareas', 'descripcion', 'character varying'),
    ('tareas', 'fecha_vencimiento', 'character varying'),
    ('tareas', 'estado', 'character varying'),
    ('tareas', 'lista', 'character varying'),
    ('tareas', 'id_proyecto', 'integer'),
    ('comentarios', 'id_comentario', 'integer'),
    ('comentarios', 'texto_comentario', 'character varying'),
    ('comentarios', 'fecha_hora', 'character varying'),
    ('comentarios', 'id_usuario', 'character varying'),
    ('comentarios', 'id_tarea', 'integer'),
    ('asignaciones', 'id_asignacion', 'integer'),
    ('asignaciones', 'id_usuario', 'integer'),
    ('asignaciones', 'id_tarea', 'integer'),
    ('asignaciones', 'fecha_asignacion', 'character varying')
  )

  SELECT string_agg(e.tabla || '.' || e.columna, ', ') INTO diferencias
  FROM esperado AS e
  LEFT JOIN information_schema.columns AS c
    ON c.table_schema = 'public' AND c.table_name = e.tabla AND c.column_name = e.columna
  WHERE c.data_type IS DISTINCT FROM e.tipo;
  IF diferencias IS NOT NULL THEN
    RAISE EXCEPTION 'BD-02 FALLA: columnas ausentes o con otro tipo: %', diferencias;
  END IF;
  RAISE NOTICE 'BD-02 OK: 29 columnas con el tipo esperado';
END $$;

-- BD-03 Cada tabla tiene llave primaria autogenerada (IDENTITY), como espera GenerationType.IDENTITY
DO $$
DECLARE
  sin_identity text;
BEGIN
  SELECT string_agg(t.tabla || '.' || t.columna, ', ') INTO sin_identity
  FROM (VALUES
    ('usuario', 'id_usuario'), ('proyectos', 'id_proyecto'), ('tareas', 'id_tarea'),
    ('comentarios', 'id_comentario'), ('asignaciones', 'id_asignacion')
  ) AS t (tabla, columna)
  WHERE NOT EXISTS (
    SELECT 1
    FROM information_schema.columns AS c
    INNER JOIN information_schema.key_column_usage AS k
      ON c.table_name = k.table_name AND c.column_name = k.column_name
    INNER JOIN information_schema.table_constraints AS tc
      ON k.constraint_name = tc.constraint_name AND tc.constraint_type = 'PRIMARY KEY'
    WHERE
      c.table_schema = 'public' AND c.table_name = t.tabla
      AND c.column_name = t.columna AND c.is_identity = 'YES'
  );
  IF sin_identity IS NOT NULL THEN
    RAISE EXCEPTION 'BD-03 FALLA: sin PK IDENTITY: %', sin_identity;
  END IF;
  RAISE NOTICE 'BD-03 OK: las 5 llaves primarias son IDENTITY';
END $$;

-- BD-04 Datos iniciales cargados
DO $$
BEGIN
  IF (SELECT count(*) FROM usuario) <> 3 THEN
    RAISE EXCEPTION 'BD-04 FALLA: se esperaban 3 usuarios';
  END IF;
  IF (SELECT count(*) FROM proyectos) <> 1 THEN
    RAISE EXCEPTION 'BD-04 FALLA: se esperaba 1 proyecto';
  END IF;
  IF (SELECT count(*) FROM tareas) <> 1 THEN
    RAISE EXCEPTION 'BD-04 FALLA: se esperaba 1 tarea';
  END IF;
  IF (SELECT count(*) FROM comentarios) <> 2 THEN
    RAISE EXCEPTION 'BD-04 FALLA: se esperaban 2 comentarios';
  END IF;
  IF (SELECT count(*) FROM asignaciones) <> 0 THEN
    RAISE EXCEPTION 'BD-04 FALLA: no se esperaban asignaciones';
  END IF;
  RAISE NOTICE 'BD-04 OK: datos iniciales completos';
END $$;

-- BD-05 Integridad referencial de los datos (no hay tareas ni comentarios huerfanos)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM tareas AS t
    WHERE NOT EXISTS (SELECT 1 FROM proyectos AS p WHERE p.id_proyecto = t.id_proyecto)
  ) THEN
    RAISE EXCEPTION 'BD-05 FALLA: hay tareas sin proyecto';
  END IF;
  IF EXISTS (
    SELECT 1 FROM comentarios AS c
    WHERE NOT EXISTS (SELECT 1 FROM tareas AS t WHERE t.id_tarea = c.id_tarea)
  ) THEN
    RAISE EXCEPTION 'BD-05 FALLA: hay comentarios sin tarea';
  END IF;
  RAISE NOTICE 'BD-05 OK: sin registros huerfanos';
END $$;

-- BD-06 Las consultas nativas de los repositorios de la API devuelven lo esperado
DO $$
BEGIN
  -- UsuarioRepository.findByCorreoPass
  IF (
    SELECT count(*) FROM usuario
    WHERE correo_electronico = 'ana22533@cbtis75.edu.mx' AND contrasena = '0722'
  ) <> 1 THEN
    RAISE EXCEPTION 'BD-06 FALLA: login valido no encontrado';
  END IF;
  IF (
    SELECT count(*) FROM usuario
    WHERE correo_electronico = 'ana22533@cbtis75.edu.mx' AND contrasena = 'incorrecta'
  ) <> 0 THEN
    RAISE EXCEPTION 'BD-06 FALLA: login con contrasena incorrecta devolvio filas';
  END IF;
  -- TareasRepository.findByIdProyecto
  IF (SELECT count(*) FROM tareas WHERE id_proyecto = 197) <> 1 THEN
    RAISE EXCEPTION 'BD-06 FALLA: tareas del proyecto 197';
  END IF;
  -- ComentariosRepository.findByTarea
  IF (SELECT count(*) FROM comentarios WHERE id_tarea = 199) <> 2 THEN
    RAISE EXCEPTION 'BD-06 FALLA: comentarios de la tarea 199';
  END IF;
  RAISE NOTICE 'BD-06 OK: consultas nativas de la API';
END $$;

-- BD-07 Las secuencias continuan despues de los datos iniciales (no hay choque de llaves)
DO $$
DECLARE
  nuevo_usuario integer;
  nuevo_proyecto integer;
  nueva_tarea integer;
  nuevo_comentario integer;
  nueva_asignacion integer;
BEGIN
  INSERT INTO usuario (nombre) VALUES ('prueba-ci') RETURNING id_usuario INTO nuevo_usuario;
  INSERT INTO proyectos (nombre) VALUES ('prueba-ci') RETURNING id_proyecto INTO nuevo_proyecto;
  INSERT INTO tareas (nombre, id_proyecto) VALUES ('prueba-ci', nuevo_proyecto) RETURNING id_tarea INTO nueva_tarea;
  INSERT INTO comentarios (texto_comentario, id_tarea) VALUES ('prueba-ci', nueva_tarea)
  RETURNING id_comentario INTO nuevo_comentario;
  INSERT INTO asignaciones (id_usuario, id_tarea) VALUES (nuevo_usuario, nueva_tarea)
  RETURNING id_asignacion INTO nueva_asignacion;

  IF nuevo_usuario <> 197 OR nuevo_proyecto <> 199 OR nueva_tarea <> 201
     OR nuevo_comentario <> 203 OR nueva_asignacion <> 21 THEN
    RAISE EXCEPTION 'BD-07 FALLA: ids generados % % % % %',
      nuevo_usuario, nuevo_proyecto, nueva_tarea, nuevo_comentario, nueva_asignacion;
  END IF;

  -- Limpieza de los registros de prueba
  DELETE FROM asignaciones WHERE id_asignacion = nueva_asignacion;
  DELETE FROM comentarios WHERE id_comentario = nuevo_comentario;
  DELETE FROM tareas WHERE id_tarea = nueva_tarea;
  DELETE FROM proyectos WHERE id_proyecto = nuevo_proyecto;
  DELETE FROM usuario WHERE id_usuario = nuevo_usuario;
  RAISE NOTICE 'BD-07 OK: las secuencias generan ids nuevos sin colisiones';
END $$;
