package com.academia.service;

import com.academia.entity.Plano;
import com.academia.exception.ResourceNotFoundException;
import com.academia.repository.PlanoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanoService {

    private final PlanoRepository planoRepository;

    @Transactional(readOnly = true)
    public List<Plano> listar() {
        return planoRepository.findByAtivoTrueOrderByValorMensalAsc();
    }

    @Transactional(readOnly = true)
    public Plano buscarPorId(Long id) {
        return planoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plano não encontrado com id: " + id));
    }
}