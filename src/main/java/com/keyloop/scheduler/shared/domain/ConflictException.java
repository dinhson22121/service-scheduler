package com.keyloop.scheduler.shared.domain;

public final class ConflictException extends DomainException {

    private final String title;

    public ConflictException(String title, String detail) {
        super(detail);
        this.title = title;
    }

    public String title() {
        return title;
    }
}
