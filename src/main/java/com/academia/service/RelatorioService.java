package com.academia.service;

import com.academia.dto.RelatorioDTO;
import com.academia.entity.Aluno;
import com.academia.entity.Pagamento;
import com.academia.repository.AlunoRepository;
import com.academia.repository.PagamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RelatorioService {

    private final AlunoRepository alunoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final AlunoService alunoService;

    private static final DateTimeFormatter MES_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Locale PT_BR = new Locale("pt", "BR");

    @Transactional(readOnly = true)
    public RelatorioDTO.RelatorioMensalResponse gerarMensal(String periodo) {
        YearMonth ym = YearMonth.parse(periodo, MES_FORMATTER);
        LocalDate inicio = ym.atDay(1);
        LocalDate fim    = ym.atEndOfMonth();

        List<Aluno> ativos = alunoRepository.findAllAtivos();
        List<Long> idsAtivos = ativos.stream().map(Aluno::getId).toList();

        // ── 1 query por tipo de consulta necessária ──────────────────────────────

        // Todos os pagamentos dos ativos (para calcular inadimplência em memória)
        List<Pagamento> todosAtivos = idsAtivos.isEmpty()
                ? Collections.emptyList()
                : pagamentoRepository.findAllByAlunoIds(idsAtivos);

        // Receita por caixa: pagamentos cujo dataUltimoPagamento está no período
        List<Pagamento> pagsCaixa = pagamentoRepository
                .findByDataUltimoPagamentoBetween(inicio, fim);

        // Receita por competência: pagamentos cujo mesReferencia = período e valorPago > 0
        List<Pagamento> pagsCompetencia = pagamentoRepository
                .findByAlunoIdsAndMesReferencia(idsAtivos, periodo);

        // ── Cálculos ─────────────────────────────────────────────────────────────

        BigDecimal receitaCaixa = pagsCaixa.stream()
                .map(Pagamento::getValorPago)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal receitaCompetencia = pagsCompetencia.stream()
                .map(Pagamento::getValorPago)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEsperado = ativos.stream()
                .map(Aluno::getValorMensalEfetivo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Inadimplência em memória
        Map<Long, List<Pagamento>> pagsPorAluno = todosAtivos.stream()
                .collect(Collectors.groupingBy(p -> p.getAluno().getId()));

        List<RelatorioDTO.AlunoInadimplenteResumo> inadimplentes = new ArrayList<>();
        BigDecimal totalEmAberto = BigDecimal.ZERO;

        for (Aluno aluno : ativos) {
            List<Pagamento> pags = pagsPorAluno.getOrDefault(aluno.getId(), Collections.emptyList());
            AlunoService.InadimplenciaInfo info = alunoService.calcularInadimplenciaEmMemoria(aluno, pags);
            if (info.inadimplente()) {
                totalEmAberto = totalEmAberto.add(info.totalDevido());
                inadimplentes.add(new RelatorioDTO.AlunoInadimplenteResumo(
                        aluno.getId(),
                        aluno.getNome(),
                        aluno.getTelefone(),
                        info.totalMesesEmAtraso(),
                        info.totalDevido()
                ));
            }
        }

        // Ordena inadimplentes por maior dívida
        inadimplentes.sort(Comparator.comparing(RelatorioDTO.AlunoInadimplenteResumo::totalDevido).reversed());

        // Receita por plano (baseado em caixa do período)
        Map<String, List<Pagamento>> pagsPorPlano = pagsCaixa.stream()
                .collect(Collectors.groupingBy(p -> {
                    Aluno aluno = ativos.stream()
                            .filter(a -> a.getId().equals(p.getAluno().getId()))
                            .findFirst().orElse(null);
                    if (aluno == null) return "Sem plano";
                    return aluno.getPlano() != null ? aluno.getPlano().getNome() : "Sem plano";
                }));

        List<RelatorioDTO.ReceitaPorPlano> receitaPorPlano = pagsPorPlano.entrySet().stream()
                .map(e -> new RelatorioDTO.ReceitaPorPlano(
                        e.getKey(),
                        e.getValue().size(),
                        e.getValue().stream().map(Pagamento::getValorPago).reduce(BigDecimal.ZERO, BigDecimal::add)
                ))
                .sorted(Comparator.comparing(RelatorioDTO.ReceitaPorPlano::receitaTotal).reversed())
                .collect(Collectors.toList());

        // Ticket médio
        BigDecimal ticketMedio = ativos.isEmpty() ? BigDecimal.ZERO
                : receitaCaixa.divide(BigDecimal.valueOf(ativos.size()), 2, RoundingMode.HALF_UP);

        // Taxa de inadimplência
        double taxaInadimplencia = ativos.isEmpty() ? 0.0
                : (inadimplentes.size() * 100.0) / ativos.size();

        // Label do período
        String nomeMes = ym.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
        nomeMes = Character.toUpperCase(nomeMes.charAt(0)) + nomeMes.substring(1);
        String periodoLabel = nomeMes + " " + ym.getYear();

        return new RelatorioDTO.RelatorioMensalResponse(
                periodo, periodoLabel,
                receitaCaixa, receitaCompetencia,
                totalEsperado, totalEmAberto, ticketMedio,
                ativos.size(), inadimplentes.size(), taxaInadimplencia,
                receitaPorPlano, inadimplentes
        );
    }

    public Map<String, String> getPeriodosDisponiveis() {
        YearMonth mesAtual = YearMonth.now();
        String periodoMaximo = mesAtual.format(MES_FORMATTER);

        // Menor mesReferencia de pagamentos existentes
        Optional<String> menorPagamento = pagamentoRepository.findMenorMesReferencia();

        // Menor dataInicioPlano de alunos (pode não ter pagamentos ainda)
        Optional<String> menorAluno = alunoRepository.findMenorDataInicio()
                .map(d -> YearMonth.from(d).format(MES_FORMATTER));

        // Usa o menor entre os dois
        String periodoMinimo = Stream.of(menorPagamento, menorAluno)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .min(String::compareTo)
                .orElse(periodoMaximo); // fallback: só o mês atual

        return Map.of(
                "periodoMinimo", periodoMinimo,
                "periodoMaximo", periodoMaximo
        );
    }
}