package com.keyloop.scheduler.booking.application.port.out;

import java.util.List;

import com.keyloop.scheduler.shared.domain.TimeSlot;

public interface CandidateFinder {

    List<Long> freeBays(long dealershipId, String bayType, TimeSlot slot);

    List<Long> freeTechnicians(long dealershipId, String skill, TimeSlot slot);
}
