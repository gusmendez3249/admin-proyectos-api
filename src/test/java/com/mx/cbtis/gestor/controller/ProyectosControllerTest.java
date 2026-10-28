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
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mx.cbtis.gestor.modelo.Proyectos;
import com.mx.cbtis.gestor.repository.ProtectosRepository;

@WebMvcTest(ProyectosController.class)
public class ProyectosControllerTest {

    private static final String JSON_PROYECTO = "{\"id_proyecto\":197,\"nombre\":\"My API Rest\",\"descripcion\":\"API\",\"fecha_inicio\":\"2024-05-31\",\"fecha_finalizacion\":\"2024-06-29\",\"estado_proyecto\":\"En proceso\",\"fondo\":\"/Images/animacion1.gif\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProtectosRepository proyectosRepository;

    @Autowired
    private ProyectosController controller;

    @Test
    public void testBuscarProyectoExistente() throws Exception {
        Proyectos proyecto = new Proyectos(197, "My API Rest", "API", "2024-05-31", "2024-06-29", "En proceso", "/Images/animacion1.gif");
        when(proyectosRepository.findById(197)).thenReturn(Optional.of(proyecto));

        mockMvc.perform(get("/buscarProyecto").param("id_proyecto", "197"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_proyecto").value(197))
                .andExpect(jsonPath("$.nombre").value("My API Rest"))
                .andExpect(jsonPath("$.estado_proyecto").value("En proceso"));
    }

    @Test
    public void testBuscarProyectoInexistenteDevuelveProyectoVacio() throws Exception {
        when(proyectosRepository.findById(999)).thenReturn(Optional.empty());

        mockMvc.perform(get("/buscarProyecto").param("id_proyecto", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_proyecto").doesNotExist());
    }

    @Test
    public void testBuscarProyectoSinParametroEsPeticionInvalida() throws Exception {
        mockMvc.perform(get("/buscarProyecto"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testBuscarProyectos() throws Exception {
        Proyectos p1 = new Proyectos(1, "A", "d", "2024-01-01", "2024-02-01", "En proceso", "f");
        Proyectos p2 = new Proyectos(2, "B", "d", "2024-01-01", "2024-02-01", "Terminado", "f");
        when(proyectosRepository.findAll()).thenReturn(Arrays.asList(p1, p2));

        mockMvc.perform(post("/buscarProyectos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].estado_proyecto").value("Terminado"));
    }

    @Test
    public void testBuscarProyectosSinRegistrosDevuelveListaVacia() throws Exception {
        when(proyectosRepository.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/buscarProyectos"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    public void testAddProyecto() throws Exception {
        mockMvc.perform(post("/addProyecto").contentType(MediaType.APPLICATION_JSON).content(JSON_PROYECTO))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        ArgumentCaptor<Proyectos> captor = ArgumentCaptor.forClass(Proyectos.class);
        verify(proyectosRepository).save(captor.capture());
        assertEquals("My API Rest", captor.getValue().getNombre());
        assertEquals("2024-06-29", captor.getValue().getFecha_finalizacion());
    }

    @Test
    public void testUpdateProyecto() throws Exception {
        mockMvc.perform(post("/updateProyecto").contentType(MediaType.APPLICATION_JSON).content(JSON_PROYECTO))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        verify(proyectosRepository).save(any(Proyectos.class));
    }

    @Test
    public void testDeleteProyecto() throws Exception {
        mockMvc.perform(delete("/deleteProyecto").contentType(MediaType.APPLICATION_JSON).content(JSON_PROYECTO))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        ArgumentCaptor<Proyectos> captor = ArgumentCaptor.forClass(Proyectos.class);
        verify(proyectosRepository).delete(captor.capture());
        assertEquals(197, captor.getValue().getId_proyecto());
    }

    @Test
    public void testOperacionesConProyectoNuloNoTocanLaBase() {
        assertEquals(0, controller.addProyecto(null));
        assertEquals(0, controller.updateProyecto(null));
        assertEquals(0, controller.deleteProyecto(null));

        verify(proyectosRepository, never()).save(any());
        verify(proyectosRepository, never()).delete(any());
    }
}
