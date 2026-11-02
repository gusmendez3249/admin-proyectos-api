package com.mx.cbtis.gestor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mx.cbtis.gestor.modelo.Tareas;

/** Datos que el cliente puede enviar para una tarea (en lugar de la entidad JPA, CWE-915). */
public record TareaPeticion(
		@JsonProperty("id_tarea") Integer idTarea,
		@JsonProperty("nombre") String nombre,
		@JsonProperty("descripcion") String descripcion,
		@JsonProperty("fecha_vencimiento") String fechaVencimiento,
		@JsonProperty("lista") String lista,
		@JsonProperty("estado") String estado,
		@JsonProperty("id_proyecto") Integer idProyecto) {

	public Tareas aEntidad() {
		return new Tareas(idTarea, nombre, descripcion, fechaVencimiento, lista, estado, idProyecto);
	}
}
