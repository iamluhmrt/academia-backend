package com.academia.service;

import com.academia.dto.PagamentoDTO;
import com.academia.entity.Aluno;
import com.academia.entity.Pagamento;
import com.academia.exception.BusinessException;
import com.academia.exception.ResourceNotFoundException;
import com.academia.repository.PagamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PagamentoService {

    private final PagamentoRepository pagamentoRepository;
    private final AlunoService alunoService;

    private static final DateTimeFormatter MES_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Locale PT_BR = new Locale("pt", "BR");

    // ─── HISTÓRICO ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PagamentoDTO.MesResumo> historico(Long alunoId) {
        Aluno aluno = alunoService.buscarPorId(alunoId);

        YearMonth mesInicio = YearMonth.from(aluno.getDataInicioPlano());
        YearMonth mesAtual = YearMonth.now();

        List<Pagamento> pagamentos = pagamentoRepository.findByAlunoIdOrderByMesReferenciaDesc(alunoId);
        Map<String, Pagamento> pagamentosPorMes = pagamentos.stream()
                .collect(Collectors.toMap(Pagamento::getMesReferencia, p -> p));

        List<PagamentoDTO.MesResumo> historico = new ArrayList<>();

        YearMonth mes = mesAtual;
        while (!mes.isBefore(mesInicio)) {
            String mesRef = mes.format(MES_FORMATTER);
            Pagamento pagamento = pagamentosPorMes.get(mesRef);

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