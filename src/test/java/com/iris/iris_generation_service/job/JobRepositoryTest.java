package com.iris.iris_generation_service.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class JobRepositoryTest {

    @Autowired
    private JobRepository jobRepository;

    @Test
    void savesAndFindsAGenerationJob() {
        GenerationJob job = GenerationJob.builder()
                .prompt("a red vintage car")
                .status(JobStatus.PENDING)
                .build();

        GenerationJob saved = jobRepository.save(job);

        Optional<GenerationJob> found = jobRepository.findById(saved.getId());

        assertTrue(found.isPresent());
        assertEquals("a red vintage car", found.get().getPrompt());
        assertEquals(JobStatus.PENDING, found.get().getStatus());
        assertNotNull(found.get().getCreatedAt());

    }
}