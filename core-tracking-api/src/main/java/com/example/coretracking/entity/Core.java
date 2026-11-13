package com.example.coretracking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * A returned engine core. This is the database entity; it is never returned from the API
 * (see CoreResponse).
 */
@Entity
@Table(name = "cores")
public class Core {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "part_number", nullable = false, length = 50)
    private String partNumber;

    @Column(name = "dealer", nullable = false, length = 100)
    private String dealer;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    /** Stored as text (RECEIVED, INSPECTED, ...) so the database stays readable and reordering the enum is safe. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CoreStatus status;

    /** Required by JPA. */
    protected Core() {
    }

    /** Every new core starts as RECEIVED. */
    public Core(String partNumber, String dealer, LocalDate receivedDate) {
        this.partNumber = partNumber;
        this.dealer = dealer;
        this.receivedDate = receivedDate;
        this.status = CoreStatus.RECEIVED;
    }

    public Long getId() {
        return id;
    }

    public String getPartNumber() {
        return partNumber;
    }

    public String getDealer() {
        return dealer;
    }

    public LocalDate getReceivedDate() {
        return receivedDate;
    }

    public CoreStatus getStatus() {
        return status;
    }

    /** Only CoreService calls this, after checking CoreStatus.canTransitionTo(...). */
    public void setStatus(CoreStatus status) {
        this.status = status;
    }
}
