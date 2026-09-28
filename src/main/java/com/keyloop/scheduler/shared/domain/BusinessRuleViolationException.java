package com.keyloop.scheduler.shared.domain;

public final class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(String detail) {
        super(detail);
    }
}
