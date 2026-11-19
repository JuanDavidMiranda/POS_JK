package com.possaas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "document_type", nullable = false, length = 20)
    private String documentType;

    @Column(name = "document_number", nullable = false, length = 30)
    private String documentNumber;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
