package com.iris.iris_generation_service.aiclient;

public record GenerationResult(GenerationStatus status, String modelUrl, String errorMessage) {
}