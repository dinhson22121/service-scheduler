package com.keyloop.scheduler.catalog.adapter.out.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface ServiceBayJpaRepository extends Repository<ServiceBayJpaEntity, Long> {

    @Query("select b.id from ServiceBayJpaEntity b where b.dealershipId = :dealershipId and b.bayType = :bayType order by b.id")
    List<Long> findIds(long dealershipId, String bayType);
}
