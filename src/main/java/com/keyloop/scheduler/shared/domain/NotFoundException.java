package com.keyloop.scheduler.shared.domain;

public final class NotFoundException extends DomainException {

    public NotFoundException(String detail) {
        super(detail);
    }
}
