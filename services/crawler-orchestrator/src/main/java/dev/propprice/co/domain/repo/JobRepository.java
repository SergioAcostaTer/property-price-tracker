package dev.propprice.co.domain.repo;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.propprice.co.domain.entity.Job;
import dev.propprice.co.domain.enums.JobStatus;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {
  long countByPortalAndStatus(String portal, JobStatus status);
}
