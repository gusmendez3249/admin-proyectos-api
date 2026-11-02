package com.mx.cbtis.gestor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Credenciales del login: solo correo y contraseña. */
public record LoginPeticion(
		@JsonProperty("correo_electronico") String correoElectronico,
		@JsonProperty("contrasena") String contrasena) {
}
