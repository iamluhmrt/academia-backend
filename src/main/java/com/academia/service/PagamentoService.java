package com.academia.service;

import com.academia.dto.PagamentoDTO;
import com.academia.entity.Aluno;
import com.academia.entity.Pagamento;
import com.academia.exception.BusinessException;
import com.academia.exception.ResourceNotFoundException;
import com.academia.repository.AlunoRepository;
import com.academia.repository.PagamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PagamentoService {

    private final PagamentoRepository pagamentoRepository;
    private final AlunoRepository alunoRepository;
    private final AlunoService alunoService;

    private static final DateTimeFormatter MES_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Locale PT_BR = new Locale("pt", "BR");

    // ─── HISTÓRICO ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PagamentoDTO.MesResumo> historico(Long alunoId) {
        Aluno aluno = alunoService.buscarPorId(alunoId);

        YearMonth mesInicio = YearMonth.from(aluno.getDataInicioPlano());
        YearMonth mesAtual  = YearMonth.now();

        // Busca todos os pagamentos registrados
        List<Pagamento> pagamentos = pagamentoRepository.findByAlunoIdOrderByMesReferenciaDesc(alunoId);
        Map<String, Pagamento> pagsPorMes = pagamentos.stream()
                .collect(Collectors.toMap(Pagamento::getMesReferencia, p -> p));

        // Determina até onde mostrar:
        // - Se tem dataFimPlano: mostra até o maior entre dataFimPlano e o mês mais recente com pagamento
        // - Senão: mostra até o mês atual
        YearMonth mesFinal = mesAtual;
        if (aluno.getDataFimPlano() != null) {
            YearMonth fimPlano = YearMonth.from(aluno.getDataFimPlano());
            // Se o plano foi pago integralmente no futuro, mostra até o fim do plano
            if (fimPlano.isAfter(mesAtual)) {
                mesFinal = fimPlano;
            }
        }
        // Também verifica se há pagamentos futuros registrados (ex: integral)
        for (String mesRef : pagsPorMes.keySet()) {
            YearMonth mesPag = YearMonth.parse(mesRef, MES_FORMATTER);
            if (mesPag.isAfter(mesFinal)) {
                mesFinal = mesPag;
            }
        }

        List<PagamentoDTO.MesResumo> historico = new ArrayList<>();

        YearMonth mes = mesFinal;
        while (!mes.isBefore(mesInicio)) {
            String mesRef = mes.format(MES_FORMATTER);
            Pagamento pagamento = pagsPorMes.get(mesRef);
            historico.add(buildMesResumo(mes, mesRef, pagamento, aluno.getValorMensalEfetivo()));
            mes = mes.minusMonths(1);
        }

        return historico;
    }

    // ─── REGISTRAR PAGAMENTO (PARCIAL OU TOTAL) ───────────────────────────────

    @Transactional
    public PagamentoDTO.PagamentoResponse registrarPagamento(
            Long alunoId,
            String mesReferencia,
            PagamentoDTO.PagarRequest request
    ) {
        Aluno aluno = alunoService.buscarPorId(alunoId);
        validarMesReferencia(mesReferencia);

        Optional<Pagamento> existente = pagamentoRepository.findByAlunoIdAndMesReferencia(alunoId, mesReferencia);

        // Se já existe, acumula
        if (existente.isPresent()) {
            Pagamento pagamento = existente.get();

            // Verifica se já está totalmente pago
            if (pagamento.getStatus() == Pagamento.StatusPagamento.PAGO) {
                throw new BusinessException("Este mês já está totalmente pago.");
            }

            // Verifica se o valor excede o restante
            BigDecimal novoTotal = pagamento.getValorPago().add(request.valor());
            if (novoTotal.compareTo(pagamento.getValorTotal()) > 0) {
                throw new BusinessException(
                        String.format("Valor informado (R$ %.2f) excede o restante (R$ %.2f).",
                                request.valor(), pagamento.getValorRestante())
                );
            }

            pagamento.setValorPago(novoTotal);
            pagamento.setDataUltimoPagamento(request.dataPagamento());

            return toResponse(pagamentoRepository.save(pagamento));
        }

        // Primeiro pagamento — cria o registro
        if (request.valor().compareTo(aluno.getValorMensalEfetivo()) > 0) {
            throw new BusinessException(
                    String.format("Valor informado (R$ %.2f) excede o valor do plano (R$ %.2f).",
                            request.valor(), aluno.getValorMensalEfetivo())
            );
        }

        Pagamento novo = Pagamento.builder()
                .aluno(aluno)
                .mesReferencia(mesReferencia)
                .valorTotal(aluno.getValorMensalEfetivo())
                .valorPago(request.valor())
                .dataUltimoPagamento(request.dataPagamento())
                .build();

        return toResponse(pagamentoRepository.save(novo));
    }

    // ─── ESTORNAR (ZERA TUDO) ────────────────────────────────────────────────

    @Transactional
    public PagamentoDTO.PagamentoResponse estornar(Long alunoId, String mesReferencia) {
        validarMesReferencia(mesReferencia);

        Pagamento pagamento = pagamentoRepository
                .findByAlunoIdAndMesReferencia(alunoId, mesReferencia)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pagamento não encontrado para o mês: " + mesReferencia));

        if (pagamento.getValorPago().compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("Este mês não possui pagamento para estornar.");
        }

        pagamento.setValorPago(BigDecimal.ZERO);
        pagamento.setDataUltimoPagamento(null);

        return toResponse(pagamentoRepository.save(pagamento));
    }

    // ─── PAGAMENTO INTEGRAL (plano completo pago de uma vez) ────────────────────

    @Transactional
    public List<PagamentoDTO.PagamentoResponse> registrarPagamentoIntegral(
            Long alunoId,
            PagamentoDTO.PagamentoIntegralRequest request
    ) {
        Aluno aluno = alunoService.buscarPorId(alunoId);

        if (aluno.getPlano() == null) {
            throw new BusinessException("O aluno precisa ter um plano selecionado para usar pagamento integral.");
        }

        int duracao = aluno.getPlano().getDuracaoMeses();
        if (duracao <= 1) {
            throw new BusinessException("Pagamento integral só está disponível para planos com duração maior que 1 mês.");
        }

        YearMonth mesInicio = YearMonth.from(aluno.getDataInicioPlano());

        // Calcula dataFimPlano automaticamente pelo plano se não definido
        YearMonth mesFim;
        if (aluno.getDataFimPlano() != null) {
            mesFim = YearMonth.from(aluno.getDataFimPlano());
        } else {
            mesFim = mesInicio.plusMonths(duracao - 1);
            // Persiste o dataFimPlano no aluno
            aluno.setDataFimPlano(mesFim.atEndOfMonth());
        }

        // Distribui o valor total entre os meses
        long totalMeses = mesInicio.until(mesFim, java.time.temporal.ChronoUnit.MONTHS) + 1;
        java.math.BigDecimal valorPorMes = request.valorTotal()
                .divide(java.math.BigDecimal.valueOf(totalMeses), 2, java.math.RoundingMode.HALF_UP);

        // Atualiza valorPlano do aluno com o valor negociado por mês
        // Isso garante que a listagem mostre o valor correto
        aluno.setValorPlano(valorPorMes);
        alunoRepository.save(aluno);

        // Ajuste do último mês para não perder centavos
        java.math.BigDecimal somaAntesDoUltimo = valorPorMes.multiply(java.math.BigDecimal.valueOf(totalMeses - 1));
        java.math.BigDecimal valorUltimoMes = request.valorTotal().subtract(somaAntesDoUltimo);

        List<PagamentoDTO.PagamentoResponse> resultado = new ArrayList<>();
        YearMonth mes = mesInicio;
        long idx = 0;

        while (!mes.isAfter(mesFim)) {
            String mesRef = mes.format(MES_FORMATTER);
            java.math.BigDecimal valorEsseMes = (idx == totalMeses - 1) ? valorUltimoMes : valorPorMes;

            Optional<Pagamento> existente = pagamentoRepository
                    .findByAlunoIdAndMesReferencia(alunoId, mesRef);

            Pagamento pagamento;
            if (existente.isPresent()) {
                pagamento = existente.get();
                if (pagamento.getStatus() == Pagamento.StatusPagamento.PAGO) {
                    mes = mes.plusMonths(1);
                    idx++;
                    continue;
                }
            } else {
                pagamento = Pagamento.builder()
                        .aluno(aluno)
                        .mesReferencia(mesRef)
                        .valorTotal(java.math.BigDecimal.ZERO) // será sobrescrito abaixo
                        .valorPago(java.math.BigDecimal.ZERO)
                        .build();
            }

            // valorTotal do mês = valor negociado dividido pelos meses
            // valorPago = mesmo valor → status PAGO
            pagamento.setValorTotal(valorEsseMes);
            pagamento.setValorPago(valorEsseMes);
            pagamento.setDataUltimoPagamento(request.dataPagamento());
            resultado.add(toResponse(pagamentoRepository.save(pagamento)));
            mes = mes.plusMonths(1);
            idx++;
        }

        // Atualiza valorPlano do aluno para refletir o valor mensal negociado
        aluno.setValorPlano(valorPorMes);
        alunoRepository.save(aluno);

        return resultado;
    }

    // ─── HELPERS ────────────────────────────────────────────────────────────────

    private PagamentoDTO.MesResumo buildMesResumo(
            YearMonth mes, String mesRef, Pagamento pagamento, BigDecimal valorPlano
    ) {
        if (pagamento == null) {
            // Sem registro: PENDENTE ou ATRASADO
            boolean atrasado = !mes.isAfter(YearMonth.now().minusMonths(1));
            Pagamento.StatusPagamento status = atrasado
                    ? Pagamento.StatusPagamento.ATRASADO
                    : Pagamento.StatusPagamento.PENDENTE;

            return new PagamentoDTO.MesResumo(
                    mesRef, labelMes(mes),
                    valorPlano, BigDecimal.ZERO, valorPlano,
                    status, null, null
            );
        }

        // Com registro: calcula status final
        // PARCIAL mantém PARCIAL mesmo atrasado (tem valor pago)
        // PENDENTE sem nenhum pagamento e mês passado → ATRASADO
        Pagamento.StatusPagamento status = pagamento.getStatus();
        if (status == Pagamento.StatusPagamento.PENDENTE && mes.isBefore(YearMonth.now())) {
            status = Pagamento.StatusPagamento.ATRASADO;
        }

        return new PagamentoDTO.MesResumo(
                mesRef, labelMes(mes),
                pagamento.getValorTotal(),
                pagamento.getValorPago(),
                pagamento.getValorRestante(),
                status,
                pagamento.getDataUltimoPagamento(),
                pagamento.getId()
        );
    }

    private void validarMesReferencia(String mesReferencia) {
        if (mesReferencia == null || !mesReferencia.matches("\\d{4}-\\d{2}")) {
            throw new BusinessException("Mês de referência inválido. Use o formato YYYY-MM.");
        }
        try {
            YearMonth.parse(mesReferencia, MES_FORMATTER);
        } catch (Exception e) {
            throw new BusinessException("Mês de referência inválido: " + mesReferencia);
        }
    }

    private String labelMes(YearMonth mes) {
        String nomeMes = mes.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
        nomeMes = Character.toUpperCase(nomeMes.charAt(0)) + nomeMes.substring(1);
        return nomeMes + " " + mes.getYear();
    }

    private PagamentoDTO.PagamentoResponse toResponse(Pagamento p) {
        Pagamento.StatusPagamento status = p.getStatus();
        // PENDENTE sem pagamento e mês passado → ATRASADO
        // PARCIAL mantém PARCIAL mesmo que o mês já tenha vencido
        YearMonth mesPag = YearMonth.parse(p.getMesReferencia(), MES_FORMATTER);
        if (status == Pagamento.StatusPagamento.PENDENTE && mesPag.isBefore(YearMonth.now())) {
            status = Pagamento.StatusPagamento.ATRASADO;
        }

        return new PagamentoDTO.PagamentoResponse(
                p.getId(),
                p.getAluno().getId(),
                p.getAluno().getNome(),
                p.getMesReferencia(),
                p.getValorTotal(),
                p.getValorPago(),
                p.getValorRestante(),
                status,
                p.getDataUltimoPagamento(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}