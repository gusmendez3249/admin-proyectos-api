package com.mx.cbtis.gestor.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.mx.cbtis.gestor.modelo.Usuario;
import com.mx.cbtis.gestor.repository.UsuarioRepository;

@WebMvcTest(UsuarioController.class)
public class UsuarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @Test
    public void testBuscarUsuario() throws Exception {
        Usuario mockUser = new Usuario(1, "Ana Maria", "ana@cbtis.edu.mx", "1234", "Admin", "foto.jpg");
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(mockUser));

        mockMvc.perform(get("/buscarUsuario?id=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana Maria"));
    }

    @Test
    public void testBuscarUsuarios() throws Exception {
        Usuario u1 = new Usuario(1, "Ana", "ana@mail.com", "123", "Admin", "a.jpg");
        Usuario u2 = new Usuario(2, "Carlos", "carlos@mail.com", "456", "User", "c.jpg");
        when(usuarioRepository.findAll()).thenReturn(Arrays.asList(u1, u2));

        mockMvc.perform(post("/buscarUsuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    public void testAddUsuario() throws Exception {
        String jsonBody = "{\"nombre\":\"Nuevo Usuario\",\"correo_electronico\":\"nuevo@mail.com\",\"contrasena\":\"pass\",\"rol\":\"Dev\",\"imagen_usuario\":\"img.jpg\"}";

        mockMvc.perform(post("/addUsuario")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isOk());
    }
}
