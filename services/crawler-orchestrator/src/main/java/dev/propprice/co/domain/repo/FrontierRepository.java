package dev.propprice.co.domain.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.propprice.co.domain.entity.Frontier;
import dev.propprice.co.domain.entity.FrontierId;
import dev.propprice.co.domain.enums.FrontierStatus;
import dev.propprice.co.domain.enums.TaskType;

public interface FrontierRepository extends JpaRepository<Frontier, FrontierId> {

  @Query("""
      select f
      from Frontier f
      where f.id.portal   = :portal
        and f.id.taskType = :taskType
        and f.status      = :status
      """)
  List<Frontier> findActive(
      @Param("portal") String portal,
      @Param("taskType") TaskType taskType,
      @Param("status") FrontierStatus status);
}
