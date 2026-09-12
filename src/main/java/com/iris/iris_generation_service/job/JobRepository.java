package com.iris.iris_generation_service.job;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<GenerationJob, Long> {

}