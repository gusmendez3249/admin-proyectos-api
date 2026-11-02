package com.mx.cbtis.gestor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mx.cbtis.gestor.modelo.Usuario;

/**
 * Datos que el cliente puede enviar para dar de alta, actualizar o borrar un usuario.
 * Se usa en lugar de la entidad JPA para que el JSON solo pueda asignar estos campos (CWE-915).
 */
public record UsuarioPeticion(
		@JsonProperty("id_usuario") Integer idUsuario,
		@JsonProperty("nombre") String nombre,
		@JsonProperty("correo_electronico") String correoElectronico,
		@JsonProperty("contrasena") String contrasena,
		@JsonProperty("rol") String rol,
		@JsonProperty("imagen_usuario") String imagenUsuario) {

	public Usuario aEntidad() {
		return new Usuario(idUsuario, nombre, correoElectronico, contrasena, rol, imagenUsuario);
	}
}
