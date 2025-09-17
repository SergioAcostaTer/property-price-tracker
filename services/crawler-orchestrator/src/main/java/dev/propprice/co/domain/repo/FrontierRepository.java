package dev.propprice.co.domain.repo;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import dev.propprice.co.domain.entity.Frontier;
import dev.propprice.co.domain.entity.FrontierId;

@Repository
public interface FrontierRepository extends JpaRepository<Frontier, FrontierId> {

  // Step 1: lock due rows (no contention)
  @Query(value = """
      select f.*
      from ing.frontier f
      where f.status = 'active'
        and f.next_run_at <= now()
        and (f.lease_until is null or f.lease_until < now())
      order by f.priority desc, f.next_run_at asc
      for update skip locked
      limit :batch
      """, nativeQuery = true)
  List<Frontier> lockDueBatch(@Param("batch") int batch);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(value = """
      update ing.frontier f
         set lease_until = :leaseUntil,
             last_dispatched_at = now()
       where (f.portal, f.task_type, f.url_hash) in (
             select :portal, :taskType, :urlHash
       )
      """, nativeQuery = true)
  int leaseOne(@Param("portal") String portal,
      @Param("taskType") String taskType,
      @Param("urlHash") String urlHash,
      @Param("leaseUntil") OffsetDateTime leaseUntil);

  @Modifying
  @Query(value = """
      update ing.frontier
         set status = :status
       where portal = :portal and task_type = :taskType and url_hash = :urlHash
      """, nativeQuery = true)
  int updateStatus(@Param("portal") String portal,
      @Param("taskType") String taskType,
      @Param("urlHash") String urlHash,
      @Param("status") String status);
}