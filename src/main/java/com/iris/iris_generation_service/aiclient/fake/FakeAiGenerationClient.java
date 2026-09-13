package com.iris.iris_generation_service.aiclient.fake;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.iris.iris_generation_service.aiclient.AiGenerationClient;
import com.iris.iris_generation_service.aiclient.GenerationHandle;
import com.iris.iris_generation_service.aiclient.GenerationResult;
import com.iris.iris_generation_service.aiclient.GenerationStatus;

@Component
public class FakeAiGenerationClient implements AiGenerationClient {

    private final Map<String, Integer> callCounts = new ConcurrentHashMap<>();

    @Override
    public GenerationHandle startGeneration(String prompt) {
        String fakeProviderJobId = "fake-" + System.currentTimeMillis();
        return new GenerationHandle(fakeProviderJobId);

    }

    @Override
    public GenerationResult checkStatus(GenerationHandle handle) {
        int count = callCounts.merge(handle.providerJobId(), 1, Integer::sum);

        if (count == 1) {
            return new GenerationResult(GenerationStatus.STILL_PROCESSING, null, null);
        }
        return new GenerationResult(GenerationStatus.SUCCEEDED, "https://fake.dev/model.glb", null);
    }

}