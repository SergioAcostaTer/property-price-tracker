package dev.propprice.co.domain.entity;

import java.io.Serializable;

import dev.propprice.co.domain.enums.TaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FrontierId implements Serializable {

  @Column(nullable = false)
  private String portal;

  @Enumerated(EnumType.STRING)
  @Column(name = "task_type", nullable = false, columnDefinition = "ing_task_type")
  private TaskType taskType;

  @Column(name = "url_hash", length = 32, nullable = false, columnDefinition = "char(32)")
  private String urlHash;
}
