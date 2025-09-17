package dev.propprice.co.domain.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import dev.propprice.co.domain.entity.Outbox;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {
  @Query(value = """
      select * from ing.outbox
      where sent_at is null
      order by created_at asc
      limit :limit
      """, nativeQuery = true)
  List<Outbox> fetchUnsentOrdered(int limit);
}
