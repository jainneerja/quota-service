package com.quotamanager.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "quotas")
@Data
@NoArgsConstructor
public class Quota {

    @EmbeddedId
    private QuotaId id;

    private int limitAmount;

    private int usedAmount;

    private Instant updatedAt;
}
