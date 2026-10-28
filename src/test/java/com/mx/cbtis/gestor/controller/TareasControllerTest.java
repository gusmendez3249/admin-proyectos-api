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

import com.mx.cbtis.gestor.modelo.Tareas;
import com.mx.cbtis.gestor.repository.TareasRepository;

@WebMvcTest(TareasController.class)
public class TareasControllerTest {

    private static final String JSON_TAREA = "{\"id_tarea\":199,\"nombre\":\"Base de datos\",\"descripcion\":\"Tablas\",\"fecha_vencimiento\":\"2024-06-13\",\"lista\":\"terminado\",\"estado\":\"danger\",\"id_proyecto\":197}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TareasRepository tareasRepository;

    @Autowired
    private TareasController controller;

    @Test
    public void testGetTareaPorProyecto() throws Exception {
        Tareas t1 = new Tareas(1, "T1", "d", "2024-06-01", "pendiente", "success", 197);
        Tareas t2 = new Tareas(2, "T2", "d", "2024-06-02", "terminado", "danger", 197);
        when(tareasRepository.findByIdProyecto(197)).thenReturn(Arrays.asList(t1, t2));

        mockMvc.perform(post("/getTarea").contentType(MediaType.APPLICATION_JSON).content("{\"id_proyecto\":197}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id_proyecto").value(197))
                .andExpect(jsonPath("$[1].lista").value("terminado"));
    }

    @Test
    public void testGetTareaDeProyectoSinTareas() throws Exception {
        when(tareasRepository.findByIdProyecto(5)).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/getTarea").contentType(MediaType.APPLICATION_JSON).content("{\"id_proyecto\":5}"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    public void testBuscarTareaExistente() throws Exception {
        Tareas tarea = new Tareas(199, "Base de datos", "Tablas", "2024-06-13", "terminado", "danger", 197);
        when(tareasRepository.findById(199)).thenReturn(Optional.of(tarea));

        mockMvc.perform(get("/buscarTarea").param("id_tarea", "199"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Base de datos"))
                .andExpect(jsonPath("$.estado").value("danger"));
    }

    @Test
    public void testBuscarTareaInexistenteDevuelveTareaVacia() throws Exception {
        when(tareasRepository.findById(999)).thenReturn(Optional.empty());

        mockMvc.perform(get("/buscarTarea").param("id_tarea", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_tarea").doesNotExist());
    }

    @Test
    public void testBuscarTareas() throws Exception {
        when(tareasRepository.findAll()).thenReturn(Arrays.asList(new Tareas(), new Tareas()));

        mockMvc.perform(post("/buscarTareas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    public void testAddTareas() throws Exception {
        mockMvc.perform(post("/addTareas").contentType(MediaType.APPLICATION_JSON).content(JSON_TAREA))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        ArgumentCaptor<Tareas> captor = ArgumentCaptor.forClass(Tareas.class);
        verify(tareasRepository).save(captor.capture());
        assertEquals(197, captor.getValue().getId_proyecto());
        assertEquals("2024-06-13", captor.getValue().getFecha_vencimiento());
    }

    @Test
    public void testUpdateTareas() throws Exception {
        mockMvc.perform(post("/updateTareas").contentType(MediaType.APPLICATION_JSON).content(JSON_TAREA))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        verify(tareasRepository).save(any(Tareas.class));
    }

    @Test
    public void testDeleteTareas() throws Exception {
        mockMvc.perform(delete("/deleteTareas").contentType(MediaType.APPLICATION_JSON).content(JSON_TAREA))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        verify(tareasRepository).delete(any(Tareas.class));
    }

    @Test
    public void testOperacionesConTareaNulaNoTocanLaBase() {
        assertEquals(0, controller.addTareas(null));
        assertEquals(0, controller.updateTareas(null));
        assertEquals(0, controller.deleteTareas(null));

        verify(tareasRepository, never()).save(any());
        verify(tareasRepository, never()).delete(any());
    }
}
