package com.example.coretracking.service;

import com.example.coretracking.dto.CoreResponse;
import com.example.coretracking.dto.CreateCoreRequest;
import com.example.coretracking.dto.PageResponse;
import com.example.coretracking.entity.Core;
import com.example.coretracking.entity.CoreStatus;
import com.example.coretracking.exception.CoreNotFoundException;
import com.example.coretracking.exception.InvalidStatusTransitionException;
import com.example.coretracking.repository.CoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for cores. Works with entities internally and returns DTOs, so entities never leave this layer.
 */
@Service
@Transactional(readOnly = true) // default for every method; the write methods below override it
public class CoreService {

    private static final Logger log = LoggerFactory.getLogger(CoreService.class);

    private final CoreRepository coreRepository;

    public CoreService(CoreRepository coreRepository) {
        this.coreRepository = coreRepository;
    }

    @Transactional
    public CoreResponse create(CreateCoreRequest request) {
        Core saved = coreRepository.save(new Core(request.partNumber(), request.dealer(), request.receivedDate()));
        log.info("Created core id={} partNumber={} dealer={} status={}",
                saved.getId(), saved.getPartNumber(), saved.getDealer(), saved.getStatus());
        return CoreResponse.from(saved);
    }

    public CoreResponse getById(Long id) {
        return coreRepository.findById(id)
                .map(CoreResponse::from)
                .orElseThrow(() -> new CoreNotFoundException(id));
    }

    /** @param status optional filter; null means "all statuses" */
    public PageResponse<CoreResponse> list(CoreStatus status, Pageable pageable) {
        Page<Core> page = (status == null)
                ? coreRepository.findAll(pageable)
                : coreRepository.findByStatus(status, pageable);
        return PageResponse.from(page.map(CoreResponse::from));
    }

    @Transactional
    public CoreResponse updateStatus(Long id, CoreStatus newStatus) {
        // The row is locked until this transaction ends, so a concurrent update waits and then
        // validates against the latest status instead of a stale one.
        Core core = coreRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CoreNotFoundException(id));

        CoreStatus oldStatus = core.getStatus();
        if (!oldStatus.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(oldStatus, newStatus);
        }

        core.setStatus(newStatus);
        coreRepository.save(core);
        log.info("Core {} status changed {} -> {}", id, oldStatus, newStatus);
        return CoreResponse.from(core);
    }
}
