package com.academia.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "planos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plano {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Nome é obrigatório")
    @Column(nullable = false, unique = true)
    private String nome;

    @NotNull(message = "Valor mensal é obrigatório")
    @DecimalMin(value = "0.01", message = "Valor mensal deve ser maior que zero")
    @Column(name = "valor_mensal", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorMensal;

    @NotNull(message = "Duração em meses é obrigatória")
    @Min(value = 1, message = "Duração mínima é 1 mês")
    @Column(name = "duracao_meses", nullable = false)
    private Integer duracaoMeses;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;
}