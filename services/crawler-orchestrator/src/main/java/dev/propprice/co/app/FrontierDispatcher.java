package dev.propprice.co.app;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.propprice.co.domain.entity.Job;
import dev.propprice.co.domain.enums.JobStatus;
import dev.propprice.co.domain.repo.FrontierRepository;
import dev.propprice.co.domain.repo.JobRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FrontierDispatcher {

  private final FrontierRepository frontierRepo;
  private final JobRepository jobRepo;

  @Transactional
  public int dispatchBatch(int batchSize, Duration lease) {
    var due = frontierRepo.lockDueBatch(batchSize);
    var leaseUntil = OffsetDateTime.now(ZoneOffset.UTC).plus(lease);

    int dispatched = 0;
    for (var f : due) {
      // try to lease (best-effort safety if someone else raced)
      var id = f.getId();
      var updated = frontierRepo.leaseOne(
          id.getPortal(), id.getTaskType().name(), id.getUrlHash(), leaseUntil);
      if (updated == 0)
        continue;

      // create a Job row (dispatch log)
      var job = Job.builder()
          .jobId(UUID.randomUUID())
          .portal(id.getPortal())
          .taskType(id.getTaskType())
          .segment(f.getSegment())
          .urlHash(id.getUrlHash())
          .url(f.getUrl())
          .attempt(1)
          .status(JobStatus.dispatched)
          .build();
      jobRepo.save(job);

      dispatched++;
    }
    return dispatched;
  }
}
