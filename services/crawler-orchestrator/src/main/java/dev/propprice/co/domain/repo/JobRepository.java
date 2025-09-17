package dev.propprice.co.domain.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import dev.propprice.co.domain.entity.Job;

@Repository
public interface JobRepository extends JpaRepository<Job, java.util.UUID> {
  long countByPortalAndStatus(String portal, dev.propprice.co.domain.enums.JobStatus status);
}
