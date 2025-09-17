package dev.propprice.co.domain.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.propprice.co.domain.entity.PortalPolicy;

public interface PortalPolicyRepository extends JpaRepository<PortalPolicy, String> {
}
