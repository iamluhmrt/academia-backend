package com.academia.controller;

import com.academia.dto.AlunoDTO;
import com.academia.service.AlunoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alunos")
@RequiredArgsConstructor
public class AlunoController {

    private final AlunoService alunoService;

    /**
     * GET /api/alunos
     * Parâmetros opcionais:
     *   - filtro: TODOS | ATIVO | INATIVO | INADIMPLENTES | VENCE_HOJE | VENCE_MES
     *   - nome: busca parcial por nome
     */
    @GetMapping
    public ResponseEntity<?> listar(
            @RequestParam(required = false) String filtro,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        // Se page > 0 ou size != 20 (padrão), retorna paginado
        // Mantém compatibilidade: sem parâmetros retorna lista simples
        if (page > 0 || size != 20) {
            return ResponseEntity.ok(alunoService.listarPaginado(filtro, nome, page, size));
        }
        return ResponseEntity.ok(alunoService.listar(filtro, nome));
    }

    /**
     * GET /api/alunos/{id}
     * Retorna detalhes completos de um aluno
     */
    @GetMapping("/{id}")
    public ResponseEntity<AlunoDTO.AlunoResponse> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(alunoService.buscarDetalhes(id));
    }

    /**
     * POST /api/alunos
     * Cria um novo aluno
     */
    @PostMapping
    public ResponseEntity<AlunoDTO.AlunoResponse> criar(@Valid @RequestBody AlunoDTO.AlunoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alunoService.criar(request));
    }

    /**
     * PUT /api/alunos/{id}
     * Atualiza todos os dados do aluno
     */
    @PutMapping("/{id}")
    public ResponseEntity<AlunoDTO.AlunoResponse> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody AlunoDTO.AlunoRequest request
    ) {
        return ResponseEntity.ok(alunoService.atualizar(id, request));
    }

    /**
     * DELETE /api/alunos/{id}
     * Remove um aluno
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        alunoService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}