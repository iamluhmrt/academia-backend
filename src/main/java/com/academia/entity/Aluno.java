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
@Table(name = "alunos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Aluno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 2, max = 100, message = "Nome deve ter entre 2 e 100 caracteres")
    @Column(nullable = false)
    private String nome;

    @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
    private String telefone;

    @NotNull(message = "Status é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusAluno status;

    @NotNull(message = "Dia de vencimento é obrigatório")
    @Min(value = 1, message = "Dia de vencimento deve ser entre 1 e 28")
    @Max(value = 28, message = "Dia de vencimento deve ser entre 1 e 28")
    @Column(name = "dia_vencimento", nullable = false)
    private Integer diaVencimento;

    // Mantido para compatibilidade e override manual de valor
    @NotNull(message = "Valor do plano é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor do plano deve ser maior que zero")
    @Column(name = "valor_plano", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorPlano;

    @NotNull(message = "Data de início do plano é obrigatória")
    @Column(name = "data_inicio_plano", nullable = false)
    private LocalDate dataInicioPlano;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    /**
     * Data de encerramento do plano (inclusive).
     * null = plano ativo indefinidamente.
     * Meses APÓS este mês não geram cobrança.
     * Dívidas de meses ANTERIORES continuam existindo.
     */
    @Column(name = "data_fim_plano")
    private LocalDate dataFimPlano;

    // Plano associado — opcional (null = sem plano cadastrado, usa valorPlano direto)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plano_id")
    private Plano plano;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Retorna o valor mensal efetivo:
     * - Se tem plano associado, usa o valorMensal do plano
     * - Caso contrário, usa o valorPlano manual
     */
    /**
     * Retorna true se o mês dado deve ser cobrado para este aluno.
     * Considera dataInicioPlano e dataFimPlano.
     */
    public boolean deveCobrarMes(java.time.YearMonth mes) {
        java.time.YearMonth inicio = java.time.YearMonth.from(dataInicioPlano);
        if (mes.isBefore(inicio)) return false;
        if (dataFimPlano != null) {
            java.time.YearMonth fim = java.time.YearMonth.from(dataFimPlano);
            if (mes.isAfter(fim)) return false;
        }
        return true;
    }

    public BigDecimal getValorMensalEfetivo() {
        if (plano != null) {
            return plano.getValorMensal();
        }
        return valorPlano;
    }

    public enum StatusAluno {
        ATIVO, INATIVO
    }
}