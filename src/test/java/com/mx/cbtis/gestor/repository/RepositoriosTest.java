package com.mx.cbtis.gestor.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.mx.cbtis.gestor.modelo.Comentarios;
import com.mx.cbtis.gestor.modelo.Tareas;
import com.mx.cbtis.gestor.modelo.Usuario;

/** Consultas nativas de los repositorios ejecutadas contra H2 (modo PostgreSQL). */
@DataJpaTest
class RepositoriosTest {

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private TareasRepository tareas;

	@Autowired
	private ComentariosRepository comentarios;

	@Test
	void findByCorreoSoloCoincideConElCorreoExacto() {
		usuarios.save(new Usuario(null, "Ana", "ana@mail.com", "$2a$10$hash", "Estudiante", "#112233"));

		Optional<Usuario> encontrado = usuarios.findByCorreo("ana@mail.com");
		assertTrue(encontrado.isPresent());
		assertEquals("Ana", encontrado.get().getNombre());

		assertFalse(usuarios.findByCorreo("otro@mail.com").isPresent());
		assertFalse(usuarios.findByCorreo(null).isPresent());
	}

	@Test
	void findByCorreoConCorreoRepetidoDevuelveUnSoloUsuario() {
		usuarios.save(new Usuario(null, "Ana", "ana@mail.com", "x", "Estudiante", "#112233"));
		usuarios.save(new Usuario(null, "Ana 2", "ana@mail.com", "y", "Estudiante", "#112233"));

		assertTrue(usuarios.findByCorreo("ana@mail.com").isPresent());
	}

	@Test
	void findByCorreoNoEsVulnerableAInyeccionSql() {
		usuarios.save(new Usuario(null, "Ana", "ana@mail.com", "x", "Estudiante", "#112233"));

		assertFalse(usuarios.findByCorreo("ana@mail.com' --").isPresent());
		assertFalse(usuarios.findByCorreo("' OR '1'='1").isPresent());
	}

	@Test
	void findByIdProyectoFiltraPorProyecto() {
		tareas.save(new Tareas(null, "T1", "d", "2024-06-01", "pendiente", "success", 197));
		tareas.save(new Tareas(null, "T2", "d", "2024-06-02", "terminado", "danger", 197));
		tareas.save(new Tareas(null, "T3", "d", "2024-06-03", "pendiente", "success", 5));

		List<Tareas> delProyecto = tareas.findByIdProyecto(197);
		assertEquals(2, delProyecto.size());
		assertTrue(delProyecto.stream().allMatch(t -> t.getId_proyecto() == 197));
		assertTrue(tareas.findByIdProyecto(1000).isEmpty());
	}

	@Test
	void findByTareaFiltraPorTarea() {
		comentarios.save(new Comentarios(null, "Perla", 199, "Subirlo", "2024-06-26 13:48:00"));
		comentarios.save(new Comentarios(null, "Liliana", 199, "hola", "2024-06-26 13:49:00"));
		comentarios.save(new Comentarios(null, "Ana", 7, "otra", "2024-06-26 13:50:00"));

		List<Comentarios> deLaTarea = comentarios.findByTarea(199);
		assertEquals(2, deLaTarea.size());
		assertTrue(deLaTarea.stream().allMatch(c -> c.getId_tarea() == 199));
	}
}
