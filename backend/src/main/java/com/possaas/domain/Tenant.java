package com.possaas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

    @Id
    private UUID id;

    @Column(name = "nit", nullable = false, unique = true, length = 20)
    private String nit;

    @Column(name = "business_name", nullable = false, length = 255)
    private String businessName;

    @Column(name = "subscription_status", nullable = false, length = 30)
    private String subscriptionStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
