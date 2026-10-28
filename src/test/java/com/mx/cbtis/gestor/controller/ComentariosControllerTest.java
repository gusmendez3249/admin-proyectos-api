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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mx.cbtis.gestor.modelo.Comentarios;
import com.mx.cbtis.gestor.repository.ComentariosRepository;

@WebMvcTest(ComentariosController.class)
public class ComentariosControllerTest {

    private static final String JSON_COMENTARIO = "{\"id_comentario\":201,\"id_usuario\":\"Perla\",\"id_tarea\":199,\"texto_comentario\":\"Subirlo YA!!!\",\"fecha_hora\":\"2024-06-26 13:48:00\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComentariosRepository comentariosRepository;

    @Autowired
    private ComentariosController controller;

    @Test
    public void testGetComentarioPorTarea() throws Exception {
        Comentarios c1 = new Comentarios(201, "Perla", 199, "Subirlo YA!!!", "2024-06-26 13:48:00");
        Comentarios c2 = new Comentarios(202, "Liliana", 199, "hola", "2024-06-26 13:49:00");
        when(comentariosRepository.findByTarea(199)).thenReturn(Arrays.asList(c1, c2));

        mockMvc.perform(post("/getComentario").contentType(MediaType.APPLICATION_JSON).content("{\"id_tarea\":199}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].texto_comentario").value("Subirlo YA!!!"))
                .andExpect(jsonPath("$[1].id_usuario").value("Liliana"));
    }

    @Test
    public void testBuscarComentarioExistente() throws Exception {
        Comentarios comentario = new Comentarios(201, "Perla", 199, "Subirlo YA!!!", "2024-06-26 13:48:00");
        when(comentariosRepository.findById(201)).thenReturn(Optional.of(comentario));

        mockMvc.perform(get("/buscarComentario").param("id", "201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_tarea").value(199))
                .andExpect(jsonPath("$.fecha_hora").value("2024-06-26 13:48:00"));
    }

    @Test
    public void testBuscarComentarioInexistenteDevuelveComentarioVacio() throws Exception {
        when(comentariosRepository.findById(999)).thenReturn(Optional.empty());

        mockMvc.perform(get("/buscarComentario").param("id", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_comentario").doesNotExist());
    }

    @Test
    public void testBuscarComentarios() throws Exception {
        when(comentariosRepository.findAll()).thenReturn(Arrays.asList(new Comentarios(), new Comentarios(), new Comentarios()));

        mockMvc.perform(post("/buscarComentarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    public void testAddComentario() throws Exception {
        mockMvc.perform(post("/addComentario").contentType(MediaType.APPLICATION_JSON).content(JSON_COMENTARIO))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        ArgumentCaptor<Comentarios> captor = ArgumentCaptor.forClass(Comentarios.class);
        verify(comentariosRepository).save(captor.capture());
        assertEquals("Subirlo YA!!!", captor.getValue().getTexto_comentario());
    }

    @Test
    public void testUpdateComentario() throws Exception {
        mockMvc.perform(post("/updateComentario").contentType(MediaType.APPLICATION_JSON).content(JSON_COMENTARIO))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        verify(comentariosRepository).save(any(Comentarios.class));
    }

    @Test
    public void testDeleteComentario() throws Exception {
        mockMvc.perform(delete("/deleteComentario").contentType(MediaType.APPLICATION_JSON).content(JSON_COMENTARIO))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        verify(comentariosRepository).delete(any(Comentarios.class));
    }

    @Test
    public void testOperacionesConComentarioNuloNoTocanLaBase() {
        assertEquals(0, controller.addComentario(null));
        assertEquals(0, controller.updateComentario(null));
        assertEquals(0, controller.deleteComentario(null));

        verify(comentariosRepository, never()).save(any());
        verify(comentariosRepository, never()).delete(any());
    }
}
