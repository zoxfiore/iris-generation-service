package com.iris.iris_generation_service.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.iris.iris_generation_service.aiclient.fake.FakeAiGenerationClient;

@ExtendWith(MockitoExtension.class)
public class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobService(jobRepository, new FakeAiGenerationClient());

    }

    @Test
    void createJob_startsGenerationAndMarksProcessing() {
        GenerationJob job = jobService.createJob("a red vintage car");

        assertEquals(JobStatus.PROCESSING, job.getStatus());
        assertNotNull(job.getProviderJobId());

    }

    @Test
    void pollJob_walksFromProcessingToDone() {

        GenerationJob job = jobService.createJob("a red vintage car");

        jobService.pollJob(job);
        assertEquals(JobStatus.PROCESSING, job.getStatus());

        jobService.pollJob(job);
        assertEquals(JobStatus.DONE, job.getStatus());
        assertEquals("https://fake.dev/model.glb", job.getModelUrl());
    }

}