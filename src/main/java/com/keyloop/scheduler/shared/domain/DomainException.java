package com.keyloop.scheduler.shared.domain;

public abstract sealed class DomainException extends RuntimeException
        permits NotFoundException, BusinessRuleViolationException, ConflictException {

    protected DomainException(String detail) {
        super(detail);
    }
}
