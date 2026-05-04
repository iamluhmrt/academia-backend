package com.academia.repository;

import com.academia.entity.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    List<Pagamento> findByAlunoIdOrderByMesReferenciaDesc(Long alunoId);

    Optional<Pagamento> findByAlunoIdAndMesReferencia(Long alunoId, String mesReferencia);

    // Busca todos os pagamentos de uma lista de alunos — 1 query em vez de N
    @Query("SELECT p FROM Pagamento p WHERE p.aluno.id IN :alunoIds")
    List<Pagamento> findAllByAlunoIds(@Param("alunoIds") List<Long> alunoIds);

    @Query("SELECT p FROM Pagamento p WHERE p.aluno.id IN :alunoIds AND p.mesReferencia = :mesReferencia")
    List<Pagamento> findByAlunoIdsAndMesReferencia(
            @Param("alunoIds") List<Long> alunoIds,
            @Param("mesReferencia") String mesReferencia
    );

    // Para relatório — receita por caixa (data real do pagamento)
    @Query("SELECT p FROM Pagamento p WHERE p.dataUltimoPagamento >= :inicio AND p.dataUltimoPagamento <= :fim")
    List<Pagamento> findByDataUltimoPagamentoBetween(
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim
    );

    boolean existsByAlunoIdAndMesReferencia(Long alunoId, String mesReferencia);

    @Query("SELECT MIN(p.mesReferencia) FROM Pagamento p")
    Optional<String> findMenorMesReferencia();
}