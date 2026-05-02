package com.academia.controller;

import com.academia.entity.Plano;
import com.academia.service.PlanoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/planos")
@RequiredArgsConstructor
public class PlanoController {

    private final PlanoService planoService;

    @GetMapping
    public ResponseEntity<List<Plano>> listar() {
        return ResponseEntity.ok(planoService.listar());
    }
}