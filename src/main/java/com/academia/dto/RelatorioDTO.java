package com.academia.dto;

import java.math.BigDecimal;
import java.util.List;

public class RelatorioDTO {

    public record RelatorioMensalResponse(
            String periodo,                    // "2026-05"
            String periodoLabel,               // "Maio 2026"

            // Receita por CAIXA — baseado em dataUltimoPagamento
            BigDecimal receitaCaixa,           // quanto entrou no mês (independente do mês de referência)

            // Receita por COMPETÊNCIA — baseado em mesReferencia
            BigDecimal receitaCompetencia,     // quanto foi pago de mensalidades deste mês

            BigDecimal totalEsperado,          // soma de valorMensal de todos os ativos
            BigDecimal totalEmAberto,          // total ainda não pago
            BigDecimal ticketMedio,            // receitaCaixa / totalAtivos

            long totalAtivos,
            long totalInadimplentes,
            double taxaInadimplencia,          // percentual

            List<ReceitaPorPlano> receitaPorPlano,
            List<AlunoInadimplenteResumo> inadimplentes
    ) {}

    public record ReceitaPorPlano(
            String nomePlano,
            long quantidadeAlunos,
            BigDecimal receitaTotal
    ) {}

    public record AlunoInadimplenteResumo(
            Long id,
            String nome,
            String telefone,
            int mesesEmAtraso,
            BigDecimal totalDevido
    ) {}
}