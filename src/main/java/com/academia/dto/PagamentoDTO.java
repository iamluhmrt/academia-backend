package com.academia.dto;

import com.academia.entity.Pagamento;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class PagamentoDTO {

    public record PagarRequest(
            @NotNull(message = "Valor é obrigatório")
            @DecimalMin(value = "0.01", message = "Valor deve ser maior que zero")
            BigDecimal valor,

            @NotNull(message = "Data de pagamento é obrigatória")
            LocalDate dataPagamento
    ) {}

    public record PagamentoResponse(
            Long id,
            Long alunoId,
            String nomeAluno,
            String mesReferencia,
            BigDecimal valorTotal,
            BigDecimal valorPago,
            BigDecimal valorRestante,
            Pagamento.StatusPagamento status,
            LocalDate dataUltimoPagamento,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record MesResumo(
            String mesReferencia,
            String mesLabel,
            BigDecimal valorTotal,
            BigDecimal valorPago,
            BigDecimal valorRestante,
            Pagamento.StatusPagamento status,
            LocalDate dataUltimoPagamento,
            Long pagamentoId
    ) {}
}