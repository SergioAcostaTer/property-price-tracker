package dev.propprice.co.app;

import org.springframework.stereotype.Service;

import dev.propprice.co.domain.entity.PortalPolicy;
import dev.propprice.co.domain.repo.PortalPolicyRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PolicyService {
  private final PortalPolicyRepository repo;

  public PortalPolicy getOrDefault(String portal) {
    return repo.findById(portal).orElseGet(() -> {
      PortalPolicy p = new PortalPolicy();
      p.setPortal(portal);
      return p; // defaults from entity
    });
  }
}
