package com.academia.controller;

import com.academia.dto.PagamentoDTO;
import com.academia.service.PagamentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alunos/{alunoId}/pagamentos")
@RequiredArgsConstructor
public class PagamentoController {

    private final PagamentoService pagamentoService;

    /**
     * GET /api/alunos/{alunoId}/pagamentos
     * Histórico completo do dataInicioPlano até o mês atual (mais recente primeiro).
     */
    @GetMapping
    public ResponseEntity<List<PagamentoDTO.MesResumo>> historico(@PathVariable Long alunoId) {
        return ResponseEntity.ok(pagamentoService.historico(alunoId));
    }

    /**
     * POST /api/alunos/{alunoId}/pagamentos/{mesReferencia}/pagar
     * Registra pagamento total ou parcial. Acumula se já existir registro.
     * Body: { "valor": 50.00, "dataPagamento": "2025-01-10" }
     */
    @PostMapping("/{mesReferencia}/pagar")
    public ResponseEntity<PagamentoDTO.PagamentoResponse> pagar(
            @PathVariable Long alunoId,
            @PathVariable String mesReferencia,
            @Valid @RequestBody PagamentoDTO.PagarRequest request
    ) {
        return ResponseEntity.ok(pagamentoService.registrarPagamento(alunoId, mesReferencia, request));
    }

    /**
     * POST /api/alunos/{alunoId}/pagamentos/{mesReferencia}/estornar
     * Zera o valorPago e volta para PENDENTE/ATRASADO.
     */
    @PostMapping("/{mesReferencia}/estornar")
    public ResponseEntity<PagamentoDTO.PagamentoResponse> estornar(
            @PathVariable Long alunoId,
            @PathVariable String mesReferencia
    ) {
        return ResponseEntity.ok(pagamentoService.estornar(alunoId, mesReferencia));
    }

    /**
     * POST /api/alunos/{alunoId}/pagamentos/integral
     * Registra pagamento de todos os meses do plano de uma vez.
     * O aluno deve ter dataFimPlano configurado.
     * Body: { "valorTotal": 960.00, "dataPagamento": "2026-05-06" }
     */
    @PostMapping("/integral")
    public ResponseEntity<List<PagamentoDTO.PagamentoResponse>> integral(
            @PathVariable Long alunoId,
            @Valid @RequestBody PagamentoDTO.PagamentoIntegralRequest request
    ) {
        return ResponseEntity.ok(pagamentoService.registrarPagamentoIntegral(alunoId, request));
    }
}