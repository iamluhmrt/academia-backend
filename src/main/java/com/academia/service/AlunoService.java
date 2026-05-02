package com.academia.service;

import com.academia.dto.AlunoDTO;
import com.academia.entity.Aluno;
import com.academia.entity.Pagamento;
import com.academia.entity.Plano;
import com.academia.exception.ResourceNotFoundException;
import com.academia.repository.AlunoRepository;
import com.academia.repository.PagamentoRepository;
import com.academia.repository.PlanoRepository;
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
public class AlunoService {

    private final AlunoRepository alunoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final PlanoRepository planoRepository;

    private static final DateTimeFormatter MES_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    // ─── CRUD ───────────────────────────────────────────────────────────────────

    @Transactional
    public AlunoDTO.AlunoResponse criar(AlunoDTO.AlunoRequest request) {
        Plano plano = resolverPlano(request.planoId());

        Aluno aluno = Aluno.builder()
                .nome(request.nome())
                .telefone(request.telefone())
                .status(request.status())
                .diaVencimento(request.diaVencimento())
                .valorPlano(plano != null ? plano.getValorMensal() : request.valorPlano())
                .dataInicioPlano(request.dataInicioPlano())
                .observacoes(request.observacoes())
                .plano(plano)
                .build();

        return toResponse(alunoRepository.save(aluno));
    }

    @Transactional
    public AlunoDTO.AlunoResponse atualizar(Long id, AlunoDTO.AlunoRequest request) {
        Aluno aluno = buscarPorId(id);
        Plano plano = resolverPlano(request.planoId());

        aluno.setNome(request.nome());
        aluno.setTelefone(request.telefone());
        aluno.setStatus(request.status());
        aluno.setDiaVencimento(request.diaVencimento());
        aluno.setValorPlano(plano != null ? plano.getValorMensal() : request.valorPlano());
        aluno.setDataInicioPlano(request.dataInicioPlano());
        aluno.setObservacoes(request.observacoes());
        aluno.setPlano(plano);

        return toResponse(alunoRepository.save(aluno));
    }

    @Transactional
    public void deletar(Long id) {
        Aluno aluno = buscarPorId(id);
        alunoRepository.delete(aluno);
    }

    @Transactional(readOnly = true)
    public AlunoDTO.AlunoResponse buscarDetalhes(Long id) {
        return toResponse(buscarPorId(id));
    }

    // ─── LISTAGEM COM FILTROS ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AlunoDTO.AlunoResumoResponse> listar(String filtro, String nome) {
        List<Aluno> alunos = buscarAlunosPorFiltroBase(filtro, nome);

        // Aplica filtros que precisam de lógica de pagamento
        if ("INADIMPLENTES".equals(filtro)) {
            return filtrarInadimplentes(alunos);
        }

        if ("VENCE_HOJE".equals(filtro)) {
            return filtrarVenceHoje(alunos);
        }

        if ("VENCE_MES".equals(filtro)) {
            return filtrarVenceMes(alunos);
        }

        // Filtros simples (ATIVO, INATIVO, TODOS)
        return alunos.stream()
                .map(a -> toResumoResponse(a, calcularInadimplencia(a)))
                .collect(Collectors.toList());
    }

    // ─── FILTROS INTERNOS ────────────────────────────────────────────────────────

    private List<Aluno> buscarAlunosPorFiltroBase(String filtro, String nome) {
        boolean temNome = nome != null && !nome.isBlank();

        return switch (filtro == null ? "TODOS" : filtro.toUpperCase()) {
            case "ATIVO"         -> temNome
                    ? alunoRepository.findByStatusAndNomeContainingIgnoreCaseOrderByNomeAsc(Aluno.StatusAluno.ATIVO, nome)
                    : alunoRepository.findByStatusOrderByNomeAsc(Aluno.StatusAluno.ATIVO);
            case "INATIVO"       -> temNome
                    ? alunoRepository.findByStatusAndNomeContainingIgnoreCaseOrderByNomeAsc(Aluno.StatusAluno.INATIVO, nome)
                    : alunoRepository.findByStatusOrderByNomeAsc(Aluno.StatusAluno.INATIVO);
            case "INADIMPLENTES" -> alunoRepository.findAllAtivos(); // filtra depois
            case "VENCE_HOJE"    -> alunoRepository.findAllAtivos();
            case "VENCE_MES"     -> alunoRepository.findAllAtivos();
            default              -> temNome
                    ? alunoRepository.findByNomeContainingIgnoreCaseOrderByNomeAsc(nome)
                    : alunoRepository.findAllByOrderByNomeAsc();
        };
    }

    private List<AlunoDTO.AlunoResumoResponse> filtrarInadimplentes(List<Aluno> ativos) {
        return ativos.stream()
                .map(a -> {
                    InadimplenciaInfo info = calcularInadimplencia(a);
                    return info.inadimplente() ? toResumoResponse(a, info) : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<AlunoDTO.AlunoResumoResponse> filtrarVenceHoje(List<Aluno> ativos) {
        int diaHoje = LocalDate.now().getDayOfMonth();
        String mesAtual = YearMonth.now().format(MES_FORMATTER);

        return ativos.stream()
                .filter(a -> a.getDiaVencimento().equals(diaHoje))
                .filter(a -> {
                    // Só aparece se ainda não pagou o mês atual
                    return pagamentoRepository
                            .findByAlunoIdAndMesReferencia(a.getId(), mesAtual)
                            .map(p -> p.getStatus() != Pagamento.StatusPagamento.PAGO)
                            .orElse(true); // sem registro = não pagou
                })
                .map(a -> toResumoResponse(a, calcularInadimplencia(a)))
                .collect(Collectors.toList());
    }

    private List<AlunoDTO.AlunoResumoResponse> filtrarVenceMes(List<Aluno> ativos) {
        return ativos.stream()
                .map(a -> toResumoResponse(a, calcularInadimplencia(a)))
                .collect(Collectors.toList());
    }

    // ─── CÁLCULO DE INADIMPLÊNCIA ─────────────────────────────────────────────

    public InadimplenciaInfo calcularInadimplencia(Aluno aluno) {
        if (aluno.getStatus() != Aluno.StatusAluno.ATIVO) {
            return new InadimplenciaInfo(false, null, 0, BigDecimal.ZERO);
        }

        YearMonth mesInicio = YearMonth.from(aluno.getDataInicioPlano());
        YearMonth mesAtual = YearMonth.now();

        List<String> mesesEmAtraso = new ArrayList<>();
        BigDecimal totalDevido = BigDecimal.ZERO;

        YearMonth mes = mesInicio;
        while (!mes.isAfter(mesAtual)) {
            String mesRef = mes.format(MES_FORMATTER);
            Optional<Pagamento> pagamento = pagamentoRepository
                    .findByAlunoIdAndMesReferencia(aluno.getId(), mesRef);

            boolean pago = pagamento.map(p -> p.getStatus() == Pagamento.StatusPagamento.PAGO).orElse(false);
            if (!pago) {
                mesesEmAtraso.add(mesRef);
                // Acumula o valorRestante: se tem registro parcial usa valorRestante, senão usa valorMensal do aluno
                BigDecimal restante = pagamento
                        .map(Pagamento::getValorRestante)
                        .orElse(aluno.getValorMensalEfetivo());
                totalDevido = totalDevido.add(restante);
            }
            mes = mes.plusMonths(1);
        }

        if (mesesEmAtraso.isEmpty()) {
            return new InadimplenciaInfo(false, null, 0, BigDecimal.ZERO);
        }

        String maisAntigo = mesesEmAtraso.get(0);
        return new InadimplenciaInfo(true, maisAntigo, mesesEmAtraso.size(), totalDevido);
    }

    // ─── HELPERS ────────────────────────────────────────────────────────────────

    public Aluno buscarPorId(Long id) {
        return alunoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aluno não encontrado com id: " + id));
    }

    private AlunoDTO.AlunoResponse toResponse(Aluno aluno) {
        return new AlunoDTO.AlunoResponse(
                aluno.getId(),
                aluno.getNome(),
                aluno.getTelefone(),
                aluno.getStatus(),
                aluno.getDiaVencimento(),
                aluno.getValorMensalEfetivo(),
                aluno.getDataInicioPlano(),
                aluno.getObservacoes(),
                toPlanoResumo(aluno.getPlano()),
                aluno.getCreatedAt(),
                aluno.getUpdatedAt()
        );
    }

    private AlunoDTO.AlunoResumoResponse toResumoResponse(Aluno aluno, InadimplenciaInfo info) {
        return new AlunoDTO.AlunoResumoResponse(
                aluno.getId(),
                aluno.getNome(),
                aluno.getTelefone(),
                aluno.getStatus(),
                aluno.getDiaVencimento(),
                aluno.getValorMensalEfetivo(),
                aluno.getDataInicioPlano(),
                aluno.getObservacoes(),
                toPlanoResumo(aluno.getPlano()),
                info.inadimplente(),
                info.mesInadimplente(),
                info.totalMesesEmAtraso(),
                info.totalDevido()
        );
    }

    private AlunoDTO.PlanoResumo toPlanoResumo(Plano plano) {
        if (plano == null) return null;
        return new AlunoDTO.PlanoResumo(
                plano.getId(),
                plano.getNome(),
                plano.getValorMensal(),
                plano.getDuracaoMeses()
        );
    }

    private Plano resolverPlano(Long planoId) {
        if (planoId == null) return null;
        return planoRepository.findById(planoId)
                .orElseThrow(() -> new ResourceNotFoundException("Plano não encontrado com id: " + planoId));
    }

    // Record auxiliar interno
    public record InadimplenciaInfo(
            boolean inadimplente,
            String mesInadimplente,
            int totalMesesEmAtraso,
            BigDecimal totalDevido
    ) {}
}