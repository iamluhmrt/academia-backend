package com.academia.dto;

import java.math.BigDecimal;

public record DashboardDTO(
        long totalAtivos,
        long totalInativos,
        long inadimplentes,       // ativos com qualquer valor em aberto
        long venceHojeNaoPago,    // ativos com vencimento hoje e mês atual não pago
        BigDecimal receitaMesAtual,   // soma do que JÁ foi pago no mês atual
        BigDecimal emAbertoTotal      // soma de todo valorRestante de ativos
) {}