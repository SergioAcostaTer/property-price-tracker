package dev.propprice.co.domain.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.propprice.co.domain.entity.Outbox;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

  @Query(value = """
      select * from ing.outbox
      where sent_at is null
        and (last_error is null or not last_error like 'DEAD:%')
      order by created_at asc
      limit :limit
      """, nativeQuery = true)
  List<Outbox> fetchUnsentOrdered(@Param("limit") int limit);

  @Query(value = """
      select * from ing.outbox
      where sent_at is null
        and (last_error is null or not last_error like 'DEAD:%')
        and (
          attempts = 0
          or (attempts = 1 and created_at <= now() - interval '1 minute')
          or (attempts = 2 and created_at <= now() - interval '5 minutes')
          or (attempts = 3 and created_at <= now() - interval '15 minutes')
          or (attempts = 4 and created_at <= now() - interval '1 hour')
          or (attempts = 5 and created_at <= now() - interval '4 hours')
          or (attempts >= 6 and created_at <= now() - interval '8 hours')
        )
      order by created_at asc
      limit :limit
      """, nativeQuery = true)
  List<Outbox> fetchUnsentOrderedWithRetry(@Param("limit") int limit);

  @Query(value = """
      select count(*) from ing.outbox
      where sent_at is null and last_error like 'DEAD:%'
      """, nativeQuery = true)
  long countDeadMessages();

  @Modifying
  @Query(value = """
      delete from ing.outbox
      where sent_at is not null
        and sent_at < now() - interval '7 days'
      """, nativeQuery = true)
  void cleanupOldMessages();
}
