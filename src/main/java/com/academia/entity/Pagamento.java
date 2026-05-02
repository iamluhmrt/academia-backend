package com.academia.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "pagamentos",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"aluno_id", "mes_referencia"},
                name = "uk_pagamento_aluno_mes"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Aluno é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aluno_id", nullable = false)
    private Aluno aluno;

    @NotBlank(message = "Mês de referência é obrigatório")
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "Mês de referência deve estar no formato YYYY-MM")
    @Column(name = "mes_referencia", nullable = false, length = 7)
    private String mesReferencia;

    // Valor total do plano no momento do primeiro pagamento
    @Column(name = "valor_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    // Soma acumulada de todos os pagamentos parciais
    @Builder.Default
    @Column(name = "valor_pago", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorPago = BigDecimal.ZERO;

    // Data do último pagamento registrado
    @Column(name = "data_ultimo_pagamento")
    private LocalDate dataUltimoPagamento;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Métodos utilitários ──────────────────────────────────────────────────

    public BigDecimal getValorRestante() {
        return valorTotal.subtract(valorPago).max(BigDecimal.ZERO);
    }

    public StatusPagamento getStatus() {
        int cmp = valorPago.compareTo(valorTotal);
        if (cmp >= 0) return StatusPagamento.PAGO;
        if (valorPago.compareTo(BigDecimal.ZERO) > 0) return StatusPagamento.PARCIAL;
        return StatusPagamento.PENDENTE; // ATRASADO é calculado no service (depende da data)
    }

    public enum StatusPagamento {
        PAGO, PARCIAL, PENDENTE, ATRASADO
    }
}