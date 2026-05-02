package com.academia.service;

import com.academia.dto.DashboardDTO;
import com.academia.entity.Aluno;
import com.academia.entity.Pagamento;
import com.academia.repository.AlunoRepository;
import com.academia.repository.PagamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AlunoRepository alunoRepository;
    private final PagamentoRepository pagamentoRepository;

    private static final DateTimeFormatter MES_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Transactional(readOnly = true)
    public DashboardDTO calcular() {
        List<Aluno> todos    = alunoRepository.findAllByOrderByNomeAsc();
        List<Aluno> ativos   = todos.stream()
                .filter(a -> a.getStatus() == Aluno.StatusAluno.ATIVO).toList();
        List<Aluno> inativos = todos.stream()
                .filter(a -> a.getStatus() == Aluno.StatusAluno.INATIVO).toList();

        String mesAtual = YearMonth.now().format(MES_FORMATTER);
        int diaHoje     = LocalDate.now().getDayOfMonth();

        long inadimplentes    = 0;
        long venceHojeNaoPago = 0;
        BigDecimal receitaMes = BigDecimal.ZERO;
        BigDecimal emAberto   = BigDecimal.ZERO;

        for (Aluno aluno : ativos) {
            Optional<Pagamento> pagMes = pagamentoRepository
                    .findByAlunoIdAndMesReferencia(aluno.getId(), mesAtual);

            boolean pagoMesAtual = pagMes
                    .map(p -> p.getStatus() == Pagamento.StatusPagamento.PAGO)
                    .orElse(false);

            // Receita do mês: soma o valorPago registrado no mês atual
            if (pagMes.isPresent()) {
                receitaMes = receitaMes.add(pagMes.get().getValorPago());
            }

            // Vence hoje e não pagou o mês atual
            if (aluno.getDiaVencimento() == diaHoje && !pagoMesAtual) {
                venceHojeNaoPago++;
            }

            // Total em aberto: percorre todo o histórico do aluno
            BigDecimal devidoAluno = calcularDevidoAluno(aluno);
            if (devidoAluno.compareTo(BigDecimal.ZERO) > 0) {
                inadimplentes++;
                emAberto = emAberto.add(devidoAluno);
            }
        }

        return new DashboardDTO(
                ativos.size(),
                inativos.size(),
                inadimplentes,
                venceHojeNaoPago,
                receitaMes,
                emAberto
        );
    }

    private BigDecimal calcularDevidoAluno(Aluno aluno) {
        YearMonth mesInicio = YearMonth.from(aluno.getDataInicioPlano());
        YearMonth mesAtual  = YearMonth.now();
        BigDecimal total    = BigDecimal.ZERO;

        YearMonth mes = mesInicio;
        while (!mes.isAfter(mesAtual)) {
            String mesRef = mes.format(MES_FORMATTER);
            Optional<Pagamento> pagamento = pagamentoRepository
                    .findByAlunoIdAndMesReferencia(aluno.getId(), mesRef);

            boolean pago = pagamento
                    .map(p -> p.getStatus() == Pagamento.StatusPagamento.PAGO)
                    .orElse(false);

            if (!pago) {
                BigDecimal restante = pagamento
                        .map(Pagamento::getValorRestante)
                        .orElse(aluno.getValorMensalEfetivo());
                total = total.add(restante);
            }
            mes = mes.plusMonths(1);
        }
        return total;
    }
}