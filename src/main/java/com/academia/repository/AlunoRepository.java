package com.academia.repository;

import com.academia.entity.Aluno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlunoRepository extends JpaRepository<Aluno, Long> {

    List<Aluno> findByStatusOrderByNomeAsc(Aluno.StatusAluno status);

    List<Aluno> findByNomeContainingIgnoreCaseOrderByNomeAsc(String nome);

    List<Aluno> findByStatusAndNomeContainingIgnoreCaseOrderByNomeAsc(
            Aluno.StatusAluno status, String nome
    );

    @Query("SELECT a FROM Aluno a WHERE a.status = 'ATIVO' AND a.diaVencimento = :dia ORDER BY a.nome")
    List<Aluno> findAtivosComVencimentoNoDia(@Param("dia") Integer dia);

    @Query("SELECT a FROM Aluno a WHERE a.status = 'ATIVO' ORDER BY a.nome")
    List<Aluno> findAllAtivos();

    List<Aluno> findAllByOrderByNomeAsc();
}
