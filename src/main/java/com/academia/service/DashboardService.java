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
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AlunoRepository alunoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final AlunoService alunoService;

    private static final DateTimeFormatter MES_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Transactional(readOnly = true)
    public DashboardDTO calcular() {
        List<Aluno> todos   = alunoRepository.findAllByOrderByNomeAsc();
        List<Aluno> ativos  = todos.stream().filter(a -> a.getStatus() == Aluno.StatusAluno.ATIVO).toList();
        long totalInativos  = todos.size() - ativos.size();

        if (ativos.isEmpty()) {
            return new DashboardDTO(0, totalInativos, 0, 0, BigDecimal.ZERO);
        }

        // ✅ 1 query para todos os pagamentos de todos os ativos
        List<Long> ids = ativos.stream().map(Aluno::getId).toList();
        List<Pagamento> todosPagamentos = pagamentoRepository.findAllByAlunoIds(ids);

        Map<Long, List<Pagamento>> pagsPorAluno = todosPagamentos.stream()
                .collect(Collectors.groupingBy(p -> p.getAluno().getId()));

        String mesAtual = YearMonth.now().format(MES_FORMATTER);
        int diaHoje     = LocalDate.now().getDayOfMonth();

        long inadimplentes    = 0;
        long venceHojeNaoPago = 0;
        BigDecimal emAberto   = BigDecimal.ZERO;

        for (Aluno aluno : ativos) {
            List<Pagamento> pags = pagsPorAluno.getOrDefault(aluno.getId(), Collections.emptyList());

            AlunoService.InadimplenciaInfo info = alunoService.calcularInadimplenciaEmMemoria(aluno, pags);
            if (info.inadimplente()) {
                inadimplentes++;
                emAberto = emAberto.add(info.totalDevido());
            }

            // Vence hoje e não pagou o mês atual
            if (aluno.getDiaVencimento() == diaHoje) {
                boolean pagoMesAtual = pags.stream()
                        .filter(p -> p.getMesReferencia().equals(mesAtual))
                        .findFirst()
                        .map(p -> p.getStatus() == Pagamento.StatusPagamento.PAGO)
                        .orElse(false);
                if (!pagoMesAtual) venceHojeNaoPago++;
            }
        }

        return new DashboardDTO(
                ativos.size(),
                totalInativos,
                inadimplentes,
                venceHojeNaoPago,
                emAberto
        );
    }
}