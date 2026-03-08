package com.financial.wallet.infra.tenant;

import com.financial.wallet.domain.Account;
import com.financial.wallet.domain.Transaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Activates the Hibernate {@code tenantFilter} on the current session before
 * any
 * service-layer method executes. This ensures that all Hibernate queries for
 * {@link Account} and {@link Transaction} automatically include
 * {@code WHERE tenant_id = :tenantId}.
 */
@Aspect
@Component
public class TenantFilterActivationAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* com.financial.wallet.service..*(..))")
    public void enableTenantFilter() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return;
        }
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("tenantFilter").setParameter("tenantId", tenantId.toString());
    }
}
