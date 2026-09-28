package com.keyloop.scheduler.shared.adapter.in.web;

import com.keyloop.scheduler.shared.domain.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String RETRY_AFTER_SECONDS = "1";

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Object> handleDomain(DomainException ex, WebRequest request) {
        ErrorResponseException problem = ApiProblems.from(ex);
        return handleErrorResponseException(problem, problem.getHeaders(), problem.getStatusCode(), request);
    }

    @ExceptionHandler({
            DataAccessResourceFailureException.class,
            QueryTimeoutException.class,
            TransientDataAccessResourceException.class,
            PessimisticLockingFailureException.class,
            CannotCreateTransactionException.class
    })
    ResponseEntity<ProblemDetail> handleOverload(RuntimeException ex) {
        log.warn("Database unavailable or saturated: {}", ex.getMessage());
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The service is temporarily overloaded, retry shortly.");
        body.setTitle("Service unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(body);
    }
}
