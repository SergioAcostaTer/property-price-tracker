package dev.propprice.co.domain.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import dev.propprice.co.domain.entity.Outbox;

@Repository
public interface OutboxRepository extends JpaRepository<Outbox, Long> {

  @Query(value = """
      select o.*
        from ing.outbox o
       where o.sent_at is null
       order by o.created_at asc
       limit :batch
      """, nativeQuery = true)
  List<Outbox> findPending(@Param("batch") int batch);
}