package com.academia.dto;

import java.math.BigDecimal;

public record DashboardDTO(
        long totalAtivos,
        long totalInativos,
        long inadimplentes,
        long venceHojeNaoPago,
        BigDecimal emAbertoTotal
) {}