package com.academia.config;

import com.academia.entity.Plano;
import com.academia.repository.PlanoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final PlanoRepository planoRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (planoRepository.count() == 0) {
            log.info("Inserindo planos padrão...");
            planoRepository.saveAll(List.of(
                    Plano.builder()
                            .nome("Mensal")
                            .valorMensal(new BigDecimal("100.00"))
                            .duracaoMeses(1)
                            .ativo(true)
                            .build(),
                    Plano.builder()
                            .nome("Trimestral")
                            .valorMensal(new BigDecimal("90.00"))
                            .duracaoMeses(3)
                            .ativo(true)
                            .build(),
                    Plano.builder()
                            .nome("Anual")
                            .valorMensal(new BigDecimal("80.00"))
                            .duracaoMeses(12)
                            .ativo(true)
                            .build()
            ));
            log.info("Planos inseridos com sucesso.");
        }
    }
}