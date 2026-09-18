package com.quotamanager.repository;

import com.quotamanager.entity.Quota;
import com.quotamanager.entity.QuotaId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuotaRepository extends JpaRepository<Quota, QuotaId> {

    List<Quota> findAllByIdOrgId(UUID orgId);

    // The atomic check-and-reserve: a single conditional UPDATE.
    // 0 rows affected = quota would be exceeded, reject.
    // 1 row affected = reservation granted; Postgres's row lock makes this
    // safe under concurrent calls without any external locking.
    @Modifying
    @Query("UPDATE Quota q SET q.usedAmount = q.usedAmount + :amount, q.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE q.id.orgId = :orgId AND q.id.resourceType = :resourceType " +
           "AND q.usedAmount + :amount <= q.limitAmount")
    int tryReserve(@Param("orgId") UUID orgId, @Param("resourceType") String resourceType, @Param("amount") int amount);

    @Modifying
    @Query("UPDATE Quota q SET q.usedAmount = q.usedAmount - :amount, q.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE q.id.orgId = :orgId AND q.id.resourceType = :resourceType")
    int release(@Param("orgId") UUID orgId, @Param("resourceType") String resourceType, @Param("amount") int amount);
}
