package com.keyloop.scheduler.shared.adapter.in.web;

import com.keyloop.scheduler.shared.domain.BusinessRuleViolationException;
import com.keyloop.scheduler.shared.domain.ConflictException;
import com.keyloop.scheduler.shared.domain.DomainException;
import com.keyloop.scheduler.shared.domain.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

final class ApiProblems {

    private ApiProblems() {
    }

    static ErrorResponseException from(DomainException ex) {
        return switch (ex) {
            case NotFoundException e -> problem(HttpStatus.NOT_FOUND, "Not found", e);
            case BusinessRuleViolationException e -> problem(HttpStatus.UNPROCESSABLE_CONTENT, "Business rule violated", e);
            case ConflictException e -> problem(HttpStatus.CONFLICT, e.title(), e);
        };
    }

    private static ErrorResponseException problem(HttpStatus status, String title, DomainException cause) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, cause.getMessage());
        body.setTitle(title);
        return new ErrorResponseException(status, body, cause);
    }
}
