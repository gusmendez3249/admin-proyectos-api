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

import com.mx.cbtis.gestor.modelo.Asignaciones;
import com.mx.cbtis.gestor.repository.AsignacionesRepository;

@WebMvcTest(AsignacionesController.class)
public class AsignacionesControllerTest {

    private static final String JSON_ASIGNACION = "{\"id_asignacion\":20,\"id_usuario\":131,\"id_tarea\":199,\"fecha_asignacion\":\"2024-06-01\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AsignacionesRepository asignacionesRepository;

    @Autowired
    private AsignacionesController controller;

    @Test
    public void testBuscarAsignacionExistente() throws Exception {
        when(asignacionesRepository.findById(20)).thenReturn(Optional.of(new Asignaciones(20, 131, 199, "2024-06-01")));

        mockMvc.perform(get("/buscarAsignacion").param("id", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_usuario").value(131))
                .andExpect(jsonPath("$.id_tarea").value(199))
                .andExpect(jsonPath("$.fecha_asignacion").value("2024-06-01"));
    }

    @Test
    public void testBuscarAsignacionInexistenteDevuelveAsignacionVacia() throws Exception {
        when(asignacionesRepository.findById(999)).thenReturn(Optional.empty());

        mockMvc.perform(get("/buscarAsignacion").param("id", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_asignacion").doesNotExist());
    }

    @Test
    public void testBuscarAsignacionConIdNoNumericoEsPeticionInvalida() throws Exception {
        mockMvc.perform(get("/buscarAsignacion").param("id", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testBuscarAsignaciones() throws Exception {
        when(asignacionesRepository.findAll()).thenReturn(Arrays.asList(
                new Asignaciones(1, 131, 199, "2024-06-01"), new Asignaciones(2, 172, 199, "2024-06-02")));

        mockMvc.perform(post("/buscarAsignaciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].id_usuario").value(172));
    }

    @Test
    public void testAddAsignacion() throws Exception {
        mockMvc.perform(post("/addAsignacion").contentType(MediaType.APPLICATION_JSON).content(JSON_ASIGNACION))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        ArgumentCaptor<Asignaciones> captor = ArgumentCaptor.forClass(Asignaciones.class);
        verify(asignacionesRepository).save(captor.capture());
        assertEquals(131, captor.getValue().getId_usuario());
    }

    @Test
    public void testUpdateAsignacion() throws Exception {
        mockMvc.perform(post("/updateAsignacion").contentType(MediaType.APPLICATION_JSON).content(JSON_ASIGNACION))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        verify(asignacionesRepository).save(any(Asignaciones.class));
    }

    @Test
    public void testDeleteAsignacion() throws Exception {
        mockMvc.perform(delete("/deleteAsignacion").contentType(MediaType.APPLICATION_JSON).content(JSON_ASIGNACION))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        verify(asignacionesRepository).delete(any(Asignaciones.class));
    }

    @Test
    public void testOperacionesConAsignacionNulaNoTocanLaBase() {
        assertEquals(0, controller.addAsignacion(null));
        assertEquals(0, controller.updateAsignacion(null));
        assertEquals(0, controller.deleteAsignacion(null));

        verify(asignacionesRepository, never()).save(any());
        verify(asignacionesRepository, never()).delete(any());
    }
}
