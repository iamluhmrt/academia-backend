package com.academia.dto;

import com.academia.entity.Aluno;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class AlunoDTO {

    public record AlunoRequest(
            @NotBlank(message = "Nome é obrigatório")
            @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
            String nome,

            @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
            String telefone,

            @NotNull(message = "Status é obrigatório")
            Aluno.StatusAluno status,

            @NotNull(message = "Dia de vencimento é obrigatório")
            @Min(value = 1, message = "Dia de vencimento deve ser entre 1 e 28")
            @Max(value = 28, message = "Dia de vencimento deve ser entre 1 e 28")
            Integer diaVencimento,

            @NotNull(message = "Valor do plano é obrigatório")
            @DecimalMin(value = "0.01", message = "Valor do plano deve ser maior que zero")
            BigDecimal valorPlano,

            @NotNull(message = "Data de início do plano é obrigatória")
            LocalDate dataInicioPlano,

            String observacoes,

            // Opcional — se informado, sobrescreve valorPlano com valorMensal do plano
            Long planoId
    ) {}

    public record AlunoResponse(
            Long id,
            String nome,
            String telefone,
            Aluno.StatusAluno status,
            Integer diaVencimento,
            BigDecimal valorPlano,
            LocalDate dataInicioPlano,
            String observacoes,
            PlanoResumo plano,          // null se não tiver plano
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record AlunoResumoResponse(
            Long id,
            String nome,
            String telefone,
            Aluno.StatusAluno status,
            Integer diaVencimento,
            BigDecimal valorPlano,
            LocalDate dataInicioPlano,
            String observacoes,
            PlanoResumo plano,
            Boolean inadimplente,
            String mesInadimplente,
            Integer totalMesesEmAtraso,
            BigDecimal totalDevido       // soma de valorRestante de todos os meses pendentes/parciais
    ) {}

    public record PlanoResumo(
            Long id,
            String nome,
            java.math.BigDecimal valorMensal,
            Integer duracaoMeses
    ) {}
}