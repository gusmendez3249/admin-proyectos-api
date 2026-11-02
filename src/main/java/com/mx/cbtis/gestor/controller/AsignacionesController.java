package com.mx.cbtis.gestor.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cbtis.gestor.dto.AsignacionPeticion;
import com.mx.cbtis.gestor.modelo.Asignaciones;
import com.mx.cbtis.gestor.repository.AsignacionesRepository;

@RestController
public class AsignacionesController {
	
	private final AsignacionesRepository asignacionestable;

	public AsignacionesController(AsignacionesRepository asignacionestable) {
		this.asignacionestable = asignacionestable;
	}
	
	@GetMapping("/buscarAsignacion")
	public Asignaciones getAsignacion(@RequestParam int id) {
		
		Asignaciones buscar = new Asignaciones();
		Optional<Asignaciones> asignacion = asignacionestable.findById(id);
		if(asignacion.isPresent()) {
			buscar = asignacion.get();
		}
		
		return buscar;
	}
	
	@PostMapping("/addAsignacion")
	public int addAsignacion(@RequestBody AsignacionPeticion asignacion) {
		
		int flag = 0;
		if(asignacion != null) {
			
			asignacionestable.save(asignacion.aEntidad());
			flag = 1;
		}
		
		return flag;
	}
	
	@PostMapping("/updateAsignacion")
	public int updateAsignacion(@RequestBody AsignacionPeticion asignacion) {
		
		int flag = 0;
		if(asignacion != null) {
			asignacionestable.save(asignacion.aEntidad());
			flag = 1;
		}
		
		return flag;
	}
	
	@DeleteMapping("/deleteAsignacion")
	public int deleteAsignacion(@RequestBody AsignacionPeticion asignacion) {
		
		int flag = 0;
		if(asignacion != null) {
			asignacionestable.delete(asignacion.aEntidad());
			flag = 1;
		}
		
		return flag;
	}   
	
	@PostMapping("/buscarAsignaciones")
	public List<Asignaciones> getAsignaciones(){
		
		List<Asignaciones> listaAsignaciones = new ArrayList<>();
		asignacionestable.findAll().forEach(listaAsignaciones :: add);
		return listaAsignaciones;
	}
}
