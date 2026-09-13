package com.iris.iris_generation_service.aiclient;

public interface AiGenerationClient {

    GenerationHandle startGeneration(String prompt);

    GenerationResult checkStatus(GenerationHandle handle);
}