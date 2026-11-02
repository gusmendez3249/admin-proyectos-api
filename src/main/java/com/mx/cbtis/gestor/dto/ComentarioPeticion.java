package com.mx.cbtis.gestor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mx.cbtis.gestor.modelo.Comentarios;

/** Datos que el cliente puede enviar para un comentario (en lugar de la entidad JPA, CWE-915). */
public record ComentarioPeticion(
		@JsonProperty("id_comentario") Integer idComentario,
		@JsonProperty("id_usuario") String idUsuario,
		@JsonProperty("id_tarea") Integer idTarea,
		@JsonProperty("texto_comentario") String textoComentario,
		@JsonProperty("fecha_hora") String fechaHora) {

	public Comentarios aEntidad() {
		return new Comentarios(idComentario, idUsuario, idTarea, textoComentario, fechaHora);
	}
}
