package com.mx.cbtis.gestor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mx.cbtis.gestor.modelo.Asignaciones;

/** Datos que el cliente puede enviar para una asignación (en lugar de la entidad JPA, CWE-915). */
public record AsignacionPeticion(
		@JsonProperty("id_asignacion") Integer idAsignacion,
		@JsonProperty("id_usuario") Integer idUsuario,
		@JsonProperty("id_tarea") Integer idTarea,
		@JsonProperty("fecha_asignacion") String fechaAsignacion) {

	public Asignaciones aEntidad() {
		return new Asignaciones(idAsignacion, idUsuario, idTarea, fechaAsignacion);
	}
}
