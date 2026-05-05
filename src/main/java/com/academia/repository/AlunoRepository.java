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

    @Query("SELECT a FROM Aluno a WHERE LOWER(a.nome) LIKE LOWER(CONCAT('%', :nome, '%')) ORDER BY a.nome")
    List<Aluno> findByNomeContainingIgnoreCaseOrderByNomeAsc(@Param("nome") String nome);

    @Query("SELECT a FROM Aluno a WHERE a.status = :status AND LOWER(a.nome) LIKE LOWER(CONCAT('%', :nome, '%')) ORDER BY a.nome")
    List<Aluno> findByStatusAndNomeContainingIgnoreCaseOrderByNomeAsc(@Param("status") Aluno.StatusAluno status, @Param("nome") String nome);

    @Query("SELECT a FROM Aluno a WHERE a.status = 'ATIVO' AND a.diaVencimento = :dia ORDER BY a.nome")
    List<Aluno> findAtivosComVencimentoNoDia(@Param("dia") Integer dia);

    @Query("SELECT a FROM Aluno a WHERE a.status = 'ATIVO' ORDER BY a.nome")
    List<Aluno> findAllAtivos();

    List<Aluno> findAllByOrderByNomeAsc();

    @Query("SELECT MIN(a.dataInicioPlano) FROM Aluno a")
    java.util.Optional<java.time.LocalDate> findMenorDataInicio();
}