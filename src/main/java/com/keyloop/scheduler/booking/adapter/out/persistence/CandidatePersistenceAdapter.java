package com.keyloop.scheduler.booking.adapter.out.persistence;

import java.util.List;

import com.keyloop.scheduler.booking.application.port.out.CandidateFinder;
import com.keyloop.scheduler.shared.domain.TimeSlot;
import org.springframework.stereotype.Component;

@Component
class CandidatePersistenceAdapter implements CandidateFinder {

    private final CandidateJpaRepository candidates;

    CandidatePersistenceAdapter(CandidateJpaRepository candidates) {
        this.candidates = candidates;
    }

    @Override
    public List<Long> freeBays(long dealershipId, String bayType, TimeSlot slot) {
        return candidates.freeBays(dealershipId, bayType, slot);
    }

    @Override
    public List<Long> freeTechnicians(long dealershipId, String skill, TimeSlot slot) {
        return candidates.freeTechnicians(dealershipId, skill, slot);
    }
}
