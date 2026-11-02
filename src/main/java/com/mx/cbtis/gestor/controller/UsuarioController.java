package com.mx.cbtis.gestor.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cbtis.gestor.dto.LoginPeticion;
import com.mx.cbtis.gestor.dto.UsuarioPeticion;
import com.mx.cbtis.gestor.modelo.Usuario;
import com.mx.cbtis.gestor.repository.UsuarioRepository;

@RestController
public class UsuarioController {

	private final UsuarioRepository usuariotable;

	public UsuarioController(UsuarioRepository usuariotable) {
		this.usuariotable = usuariotable;
	}

	/** Las contraseñas se guardan con BCrypt (nunca en texto plano). */
	private final PasswordEncoder encoder = new BCryptPasswordEncoder();

	@PostMapping("/getUsuarioLogin")
	public Usuario getUsuarioLogin(@RequestBody LoginPeticion userParam) {

		Optional<Usuario> optionaluser = usuariotable.findByCorreo(userParam.correoElectronico());

		Usuario user = new Usuario();

		if(optionaluser.isPresent() && contrasenaValida(userParam.contrasena(), optionaluser.get())) {
			user = optionaluser.get();
		}else {
			user.setNombre("000");
		}

		return user;
	}

	@GetMapping("/buscarUsuario")
	public Usuario getUsuario(@RequestParam int id) {

		Usuario buscar = new Usuario();
		Optional<Usuario> usuario = usuariotable.findById(id);
		if(usuario.isPresent())
		{
			buscar = usuario.get();
		}

		return buscar;
	}

	@PostMapping("/addUsuario")
	public int addUsuario(@RequestBody UsuarioPeticion usuario) {
		int flag = 0;
		if(usuario != null) {
			Usuario entidad = usuario.aEntidad();
			prepararContrasena(entidad);
			usuariotable.save(entidad);
			flag = 1;
		}

		return flag;
	}

	@PostMapping("/updateUsuario")
	public int updateUsuario(@RequestBody UsuarioPeticion usuario) {
		int flag = 0;
		if(usuario != null) {
			Usuario entidad = usuario.aEntidad();
			prepararContrasena(entidad);
			usuariotable.save(entidad);
			flag = 1;
		}

		return flag;
	}

	@DeleteMapping("/deleteUsuario")
	public int deleteUsuario(@RequestBody UsuarioPeticion usuario) {
		int flag = 0;
		if(usuario != null) {
			usuariotable.delete(usuario.aEntidad());
			flag = 1;
		}

		return flag;
	}

	@PostMapping("/buscarUsuarios")
	public List<Usuario> getUsuarios(){
		List<Usuario> listaUsuarios = new ArrayList<>();
		usuariotable.findAll().forEach(listaUsuarios::add);
		return listaUsuarios;
	}

	/**
	 * Cifra la contraseña recibida. Si no se envía (la API nunca la devuelve, así que los clientes
	 * no la conocen al actualizar), se conserva la que ya está guardada.
	 */
	private void prepararContrasena(Usuario usuario) {
		if (usuario.getContrasena() != null) {
			usuario.setContrasena(encoder.encode(usuario.getContrasena()));
		} else if (usuario.getId_usuario() != null) {
			usuariotable.findById(usuario.getId_usuario())
					.ifPresent(actual -> usuario.setContrasena(actual.getContrasena()));
		}
	}

	/**
	 * Valida la contraseña contra el hash BCrypt. Las cuentas creadas antes del cambio tienen la
	 * contraseña en texto plano: se aceptan una vez y se migran a BCrypt en ese mismo login.
	 */
	private boolean contrasenaValida(String recibida, Usuario usuario) {
		String guardada = usuario.getContrasena();
		if (recibida == null || guardada == null) {
			return false;
		}
		if (esHashBCrypt(guardada)) {
			return encoder.matches(recibida, guardada);
		}
		boolean coincide = MessageDigest.isEqual(guardada.getBytes(StandardCharsets.UTF_8),
				recibida.getBytes(StandardCharsets.UTF_8));
		if (coincide) {
			usuario.setContrasena(encoder.encode(recibida));
			usuariotable.save(usuario);
		}
		return coincide;
	}

	private boolean esHashBCrypt(String valor) {
		return valor.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
	}
}
