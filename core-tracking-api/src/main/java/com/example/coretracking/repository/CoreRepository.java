package com.example.coretracking.repository;

import com.example.coretracking.entity.Core;
import com.example.coretracking.entity.CoreStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CoreRepository extends JpaRepository<Core, Long> {

    /** Used by the list endpoint when a ?status= filter is given. */
    Page<Core> findByStatus(CoreStatus status, Pageable pageable);

    /**
     * Loads a core and locks its row (SELECT ... FOR UPDATE) until the surrounding transaction ends.
     * Used by the status update so that two simultaneous requests cannot both validate against the
     * same old status. Must be called inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Core c where c.id = :id")
    Optional<Core> findByIdForUpdate(@Param("id") Long id);
}
