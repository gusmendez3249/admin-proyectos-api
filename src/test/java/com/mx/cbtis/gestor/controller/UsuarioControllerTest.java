package com.mx.cbtis.gestor.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.mx.cbtis.gestor.modelo.Usuario;
import com.mx.cbtis.gestor.repository.UsuarioRepository;

@WebMvcTest(UsuarioController.class)
public class UsuarioControllerTest {

    private static final String JSON_USUARIO = "{\"id_usuario\":1,\"nombre\":\"Ana\",\"correo_electronico\":\"ana@mail.com\",\"contrasena\":\"123\",\"rol\":\"Admin\",\"imagen_usuario\":\"a.jpg\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UsuarioController controller;

    @Test
    public void testBuscarUsuario() throws Exception {
        Usuario mockUser = new Usuario(1, "Ana Maria", "ana@cbtis.edu.mx", "1234", "Admin", "foto.jpg");
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(mockUser));

        mockMvc.perform(get("/buscarUsuario?id=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana Maria"))
                // la contraseña nunca debe viajar en la respuesta
                .andExpect(jsonPath("$.contrasena").doesNotExist());
    }

    @Test
    public void testBuscarUsuarioInexistenteDevuelveUsuarioVacio() throws Exception {
        when(usuarioRepository.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(get("/buscarUsuario?id=99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_usuario").doesNotExist())
                .andExpect(jsonPath("$.nombre").doesNotExist());
    }

    @Test
    public void testBuscarUsuarios() throws Exception {
        Usuario u1 = new Usuario(1, "Ana", "ana@mail.com", "123", "Admin", "a.jpg");
        Usuario u2 = new Usuario(2, "Carlos", "carlos@mail.com", "456", "User", "c.jpg");
        when(usuarioRepository.findAll()).thenReturn(Arrays.asList(u1, u2));

        mockMvc.perform(post("/buscarUsuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].nombre").value("Carlos"))
                .andExpect(jsonPath("$[0].contrasena").doesNotExist());
    }

    @Test
    public void testLoginValidoDevuelveUsuarioSinContrasena() throws Exception {
        Usuario usuario = new Usuario(7, "Ana QA", "ana.qa@mail.com", "1234", "Estudiante", "#112233");
        when(usuarioRepository.findByCorreoPass("ana.qa@mail.com", "1234")).thenReturn(Optional.of(usuario));

        mockMvc.perform(post("/getUsuarioLogin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo_electronico\":\"ana.qa@mail.com\",\"contrasena\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_usuario").value(7))
                .andExpect(jsonPath("$.nombre").value("Ana QA"))
                .andExpect(jsonPath("$.contrasena").doesNotExist());
    }

    @Test
    public void testLoginInvalidoDevuelveCodigo000() throws Exception {
        when(usuarioRepository.findByCorreoPass("x@mail.com", "mala")).thenReturn(Optional.empty());

        mockMvc.perform(post("/getUsuarioLogin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo_electronico\":\"x@mail.com\",\"contrasena\":\"mala\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("000"))
                .andExpect(jsonPath("$.id_usuario").doesNotExist());
    }

    @Test
    public void testAddUsuario() throws Exception {
        String jsonBody = "{\"nombre\":\"Nuevo Usuario\",\"correo_electronico\":\"nuevo@mail.com\",\"contrasena\":\"pass\",\"rol\":\"Dev\",\"imagen_usuario\":\"img.jpg\"}";

        mockMvc.perform(post("/addUsuario")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        // la contraseña sí se recibe (WRITE_ONLY) y se guarda
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertEquals("nuevo@mail.com", captor.getValue().getCorreo_electronico());
        assertEquals("pass", captor.getValue().getContrasena());
    }

    @Test
    public void testUpdateUsuario() throws Exception {
        mockMvc.perform(post("/updateUsuario")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_USUARIO))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    public void testDeleteUsuario() throws Exception {
        mockMvc.perform(delete("/deleteUsuario")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_USUARIO))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        verify(usuarioRepository).delete(any(Usuario.class));
    }

    @Test
    public void testOperacionesConUsuarioNuloNoTocanLaBase() {
        assertEquals(0, controller.addUsuario(null));
        assertEquals(0, controller.updateUsuario(null));
        assertEquals(0, controller.deleteUsuario(null));

        verify(usuarioRepository, never()).save(any());
        verify(usuarioRepository, never()).delete(any());
    }
}
