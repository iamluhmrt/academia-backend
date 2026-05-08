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
import java.text.Normalizer;
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
                .dataFimPlano(request.dataFimPlano())
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
        aluno.setDataFimPlano(request.dataFimPlano());
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
        if (alunos.isEmpty()) return Collections.emptyList();

        // ✅ 1 única query para buscar todos os pagamentos dos alunos retornados
        List<Long> ids = alunos.stream().map(Aluno::getId).toList();
        List<Pagamento> todosPagamentos = pagamentoRepository.findAllByAlunoIds(ids);

        // Agrupa por aluno_id para acesso O(1)
        Map<Long, List<Pagamento>> pagamentosPorAluno = todosPagamentos.stream()
                .collect(Collectors.groupingBy(p -> p.getAluno().getId()));

        // Calcula inadimplência em memória para todos os alunos
        List<AlunoDTO.AlunoResumoResponse> resultado = alunos.stream()
                .map(a -> {
                    List<Pagamento> pags = pagamentosPorAluno.getOrDefault(a.getId(), Collections.emptyList());
                    InadimplenciaInfo info = calcularInadimplenciaEmMemoria(a, pags);
                    return toResumoResponse(a, info);
                })
                .collect(Collectors.toList());

        // Filtros pós-processamento
        return switch (filtro == null ? "TODOS" : filtro.toUpperCase()) {
            case "INADIMPLENTES" -> resultado.stream()
                    .filter(AlunoDTO.AlunoResumoResponse::inadimplente)
                    .collect(Collectors.toList());
            case "VENCE_HOJE" -> {
                int diaHoje = LocalDate.now().getDayOfMonth();
                String mesAtual = YearMonth.now().format(MES_FORMATTER);
                yield resultado.stream()
                        .filter(r -> {
                            Aluno a = alunos.stream().filter(al -> al.getId().equals(r.id())).findFirst().orElseThrow();
                            if (a.getDiaVencimento() != diaHoje) return false;
                            List<Pagamento> pags = pagamentosPorAluno.getOrDefault(a.getId(), Collections.emptyList());
                            return pags.stream()
                                    .filter(p -> p.getMesReferencia().equals(mesAtual))
                                    .findFirst()
                                    .map(p -> p.getStatus() != Pagamento.StatusPagamento.PAGO)
                                    .orElse(true);
                        })
                        .collect(Collectors.toList());
            }
            default -> resultado;
        };
    }

    private List<Aluno> buscarAlunosPorFiltroBase(String filtro, String nome) {
        boolean temNome = nome != null && !nome.isBlank();
        String filtroUpper = filtro == null ? "TODOS" : filtro.toUpperCase();

        // Busca base sem filtro de nome (fazemos em memória para suportar acentos)
        List<Aluno> base = switch (filtroUpper) {
            case "ATIVO"                          -> alunoRepository.findByStatusOrderByNomeAsc(Aluno.StatusAluno.ATIVO);
            case "INATIVO"                        -> alunoRepository.findByStatusOrderByNomeAsc(Aluno.StatusAluno.INATIVO);
            case "INADIMPLENTES"  -> alunoRepository.findAllByOrderByNomeAsc();
            case "RENOVAR_PLANO"  -> {
                java.time.LocalDate hoje   = java.time.LocalDate.now();
                java.time.LocalDate limite = hoje.plusDays(30);
                List<Aluno> vencendo = alunoRepository.findComPlanoVencendo(hoje, limite);
                List<Aluno> vencidos = alunoRepository.findComPlanoVencido(hoje);
                List<Aluno> combined = new ArrayList<>(vencidos);
                vencendo.stream().filter(a -> !combined.contains(a)).forEach(combined::add);
                yield combined;
            }
            case "VENCE_HOJE", "VENCE_MES" -> alunoRepository.findAllAtivos();
            default                               -> alunoRepository.findAllByOrderByNomeAsc();
        };

        // Filtro de nome em memória — normaliza acentos de ambos os lados
        if (temNome) {
            String nomeBusca = normalizarParaBusca(nome);
            base = base.stream()
                    .filter(a -> normalizarParaBusca(a.getNome()).contains(nomeBusca))
                    .collect(Collectors.toList());
        }

        return base;
    }

    // ─── CÁLCULO EM MEMÓRIA (sem queries adicionais) ─────────────────────────────

    /**
     * Calcula inadimplência usando mapa de pagamentos já carregado.
     * Substitui o loop com N queries por processamento em memória.
     */
    public InadimplenciaInfo calcularInadimplenciaEmMemoria(Aluno aluno, List<Pagamento> pagamentos) {
        // Indexa por mesReferencia para O(1)
        Map<String, Pagamento> pagsPorMes = pagamentos.stream()
                .collect(Collectors.toMap(Pagamento::getMesReferencia, p -> p));

        YearMonth mesInicio = YearMonth.from(aluno.getDataInicioPlano());
        YearMonth mesAtual  = YearMonth.now();

        // Aluno inativo sem dataFimPlano: congela cobranças no mês atual
        // mas mantém todas as dívidas passadas — não zera nada
        // Aluno inativo COM dataFimPlano: respeita o dataFimPlano normalmente

        List<String> mesesEmAtraso = new ArrayList<>();
        BigDecimal totalDevido = BigDecimal.ZERO;

        YearMonth mes = mesInicio;
        while (!mes.isAfter(mesAtual)) {
            // Só cobra meses dentro do período do plano
            if (!aluno.deveCobrarMes(mes)) {
                mes = mes.plusMonths(1);
                continue;
            }

            String mesRef = mes.format(MES_FORMATTER);
            Pagamento pag = pagsPorMes.get(mesRef);

            boolean pago = pag != null && pag.getStatus() == Pagamento.StatusPagamento.PAGO;
            if (!pago) {
                mesesEmAtraso.add(mesRef);
                BigDecimal restante = pag != null
                        ? pag.getValorRestante()
                        : aluno.getValorMensalEfetivo();
                totalDevido = totalDevido.add(restante);
            }
            mes = mes.plusMonths(1);
        }

        if (mesesEmAtraso.isEmpty()) {
            return new InadimplenciaInfo(false, null, 0, BigDecimal.ZERO);
        }
        return new InadimplenciaInfo(true, mesesEmAtraso.get(0), mesesEmAtraso.size(), totalDevido);
    }

    // Mantido para uso pontual (ex: DashboardService, chamadas individuais)
    public InadimplenciaInfo calcularInadimplencia(Aluno aluno) {
        List<Pagamento> pags = pagamentoRepository.findByAlunoIdOrderByMesReferenciaDesc(aluno.getId());
        return calcularInadimplenciaEmMemoria(aluno, pags);
    }

    // ─── HELPERS ────────────────────────────────────────────────────────────────

    public Aluno buscarPorId(Long id) {
        return alunoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aluno não encontrado com id: " + id));
    }

    private AlunoDTO.AlunoResponse toResponse(Aluno aluno) {
        return new AlunoDTO.AlunoResponse(
                aluno.getId(), aluno.getNome(), aluno.getTelefone(),
                aluno.getStatus(), aluno.getDiaVencimento(),
                aluno.getValorMensalEfetivo(), aluno.getDataInicioPlano(),
                aluno.getObservacoes(), toPlanoResumo(aluno.getPlano()),
                aluno.getDataFimPlano(),
                aluno.getCreatedAt(), aluno.getUpdatedAt()
        );
    }

    private AlunoDTO.AlunoResumoResponse toResumoResponse(Aluno aluno, InadimplenciaInfo info) {
        return new AlunoDTO.AlunoResumoResponse(
                aluno.getId(), aluno.getNome(), aluno.getTelefone(),
                aluno.getStatus(), aluno.getDiaVencimento(),
                aluno.getValorMensalEfetivo(), aluno.getDataInicioPlano(),
                aluno.getObservacoes(), toPlanoResumo(aluno.getPlano()),
                aluno.getDataFimPlano(),
                info.inadimplente(), info.mesInadimplente(),
                info.totalMesesEmAtraso(), info.totalDevido()
        );
    }

    private AlunoDTO.PlanoResumo toPlanoResumo(Plano plano) {
        if (plano == null) return null;
        return new AlunoDTO.PlanoResumo(
                plano.getId(), plano.getNome(),
                plano.getValorMensal(), plano.getDuracaoMeses()
        );
    }

    private Plano resolverPlano(Long planoId) {
        if (planoId == null) return null;
        return planoRepository.findById(planoId)
                .orElseThrow(() -> new ResourceNotFoundException("Plano não encontrado com id: " + planoId));
    }

    @Transactional(readOnly = true)
    public AlunoDTO.PaginatedResponse listarPaginado(String filtro, String nome, int page, int size) {
        // Limita page size máximo a 50
        int pageSize = Math.min(size, 50);

        List<AlunoDTO.AlunoResumoResponse> todos = listar(filtro, nome);

        int totalElements = todos.size();
        int totalPages = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / pageSize);

        int fromIndex = page * pageSize;
        // Página além do limite — retorna vazio
        if (fromIndex >= totalElements && totalElements > 0) {
            return new AlunoDTO.PaginatedResponse(
                    Collections.emptyList(), page, pageSize,
                    totalElements, totalPages, true
            );
        }

        int toIndex = Math.min(fromIndex + pageSize, totalElements);
        List<AlunoDTO.AlunoResumoResponse> pageContent = fromIndex >= totalElements
                ? Collections.emptyList()
                : todos.subList(fromIndex, toIndex);

        return new AlunoDTO.PaginatedResponse(
                pageContent, page, pageSize,
                totalElements, totalPages,
                page >= totalPages - 1
        );
    }

    /**
     * Normaliza string para busca: remove acentos e converte para minúsculas.
     * "João" -> "joao", "Ação" -> "acao"
     */
    private String normalizarParaBusca(String texto) {
        if (texto == null) return "";
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return normalizado.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "").toLowerCase();
    }

    public record InadimplenciaInfo(
            boolean inadimplente,
            String mesInadimplente,
            int totalMesesEmAtraso,
            BigDecimal totalDevido
    ) {}
}