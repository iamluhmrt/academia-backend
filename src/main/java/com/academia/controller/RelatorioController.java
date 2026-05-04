package com.academia.controller;

import com.academia.dto.RelatorioDTO;
import com.academia.service.RelatorioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
public class RelatorioController {

    private final RelatorioService relatorioService;

    /**
     * GET /api/relatorios/mensal?periodo=2026-05
     * Se período não informado, usa o mês atual.
     */
    @GetMapping("/mensal")
    public ResponseEntity<RelatorioDTO.RelatorioMensalResponse> mensal(
            @RequestParam(required = false) String periodo
    ) {
        if (periodo == null) {
            periodo = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        return ResponseEntity.ok(relatorioService.gerarMensal(periodo));
    }

    /**
     * GET /api/relatorios/periodos
     * Retorna o menor período disponível (para montar o dropdown no frontend).
     * { "periodoMinimo": "2023-01", "periodoMaximo": "2026-05" }
     */
    @GetMapping("/periodos")
    public ResponseEntity<Map<String, String>> periodos() {
        return ResponseEntity.ok(relatorioService.getPeriodosDisponiveis());
    }
}