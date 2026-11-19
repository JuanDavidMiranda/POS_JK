package com.possaas.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Activa el filtro Hibernate @tenantFilter dentro de la transacción de cada caso de uso. */
@Component
public class TenantFilterSupport {

    @PersistenceContext
    private EntityManager entityManager;

    public void enable(UUID tenantId) {
        Session session = entityManager.unwrap(Session.class);
        Filter filter = session.enableFilter("tenantFilter");
        filter.setParameter("tenantId", tenantId);
        filter.validate();
    }
}
