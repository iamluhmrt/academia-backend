package com.academia.repository;

import com.academia.entity.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    List<Pagamento> findByAlunoIdOrderByMesReferenciaDesc(Long alunoId);

    Optional<Pagamento> findByAlunoIdAndMesReferencia(Long alunoId, String mesReferencia);

    @Query("SELECT p FROM Pagamento p WHERE p.aluno.id IN :alunoIds AND p.mesReferencia = :mesReferencia")
    List<Pagamento> findByAlunoIdsAndMesReferencia(
            @Param("alunoIds") List<Long> alunoIds,
            @Param("mesReferencia") String mesReferencia
    );

    boolean existsByAlunoIdAndMesReferencia(Long alunoId, String mesReferencia);
}