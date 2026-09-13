package com.iris.iris_generation_service.job;

import org.springframework.stereotype.Service;

import com.iris.iris_generation_service.aiclient.AiGenerationClient;
import com.iris.iris_generation_service.aiclient.GenerationHandle;
import com.iris.iris_generation_service.aiclient.GenerationResult;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final AiGenerationClient aiClient;

    public JobService(JobRepository jobRepository, AiGenerationClient aiClient) {

        this.jobRepository = jobRepository;
        this.aiClient = aiClient;

    }

    public GenerationJob createJob(String prompt) {
        GenerationJob job = GenerationJob.builder()
                .prompt(prompt)
                .status(JobStatus.PENDING)
                .build();

        jobRepository.save(job);

        try {
            GenerationHandle handle = aiClient.startGeneration(prompt);
            job.setProviderJobId(handle.providerJobId());
            job.setStatus(JobStatus.PROCESSING);
        } catch (Exception e) {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
        }

        jobRepository.save(job);

        return job;
    }

    public GenerationJob pollJob(GenerationJob job) {
        GenerationHandle handle = new GenerationHandle(job.getProviderJobId());

        GenerationResult result = aiClient.checkStatus(handle);

        switch (result.status()) {

            case STILL_PROCESSING -> {

            }
            case SUCCEEDED -> {
                job.setStatus(JobStatus.DONE);
                job.setModelUrl(result.modelUrl());

            }
            case FAILED -> {
                job.setStatus(JobStatus.FAILED);
                job.setErrorMessage(result.errorMessage());

            }

        }

        jobRepository.save(job);
        return job;
    }

}