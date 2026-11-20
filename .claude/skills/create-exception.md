# Skill : Create Exception

## Declencheur

User asks to create custom exceptions and exception handlers (e.g., "create LeadNotFoundException", "add custom exceptions for Sequence", "handle exceptions globally").

## Instructions

1. **Review CONVENTIONS.md** section 10 (Exceptions) and section 15 (general rules)
2. **Check ARCHITECTURE.md** for package placement:
   - Exception classes: `fr.leadrevive.[domain].exception`
   - GlobalExceptionHandler: `fr.leadrevive.common.exception`

3. **Create Custom Exception Classes**:
   - Extend `RuntimeException` (never checked exceptions)
   - Add `@Getter` annotation to expose context fields
   - Include relevant context fields (e.g., `id`, `email`, `status`)
   - Constructor with meaningful message
   - Custom message pattern: "Entity ... - context: value"

4. **Exception Types**:
   - **NotFoundException**: resource not found (404)
   - **AlreadyExistsException**: duplicate/conflict (409)
   - **InvalidDataException**: validation/business rule failure (400)
   - **UnauthorizedException**: access denied (403)
   - **InternalServerException**: unexpected technical error (500)

5. **GlobalExceptionHandler**:
   - `@RestControllerAdvice` on class
   - `@Slf4j` for logging
   - One `@ExceptionHandler` method per custom exception type
   - Log with appropriate level:
     - `warn` for NotFoundException (not an error, expected scenario)
     - `error` for validation/business rule violations (includes stack trace)
     - `error` for technical errors (includes cause)
   - Return `ResponseEntity<ErrorResponse>` with appropriate status code

6. **Error Response Format**:
   - Simple format: `ResponseEntity.status(HttpStatus.X).body(ex.getMessage())`
   - Or create `ErrorResponse` DTO with timestamp, message, details

7. **Verify**:
   - All exceptions extend `RuntimeException`
   - All custom exceptions have `@Getter`
   - GlobalExceptionHandler catches all custom exceptions
   - Logging levels correct (warn for not found, error for others)
   - Exception compiles without errors

## Templates

### Custom Exception Template

```java
package fr.leadrevive.[domain].exception;

import lombok.Getter;

@Getter
public class [EntityName]NotFoundException extends RuntimeException {

    private final Long [entity]Id;

    public [EntityName]NotFoundException(final Long [entity]Id) {
        super("[EntityName] non trouve - id: " + [entity]Id);
        this.[entity]Id = [entity]Id;
    }
}
```

### Multiple Custom Exceptions

```java
package fr.leadrevive.[domain].exception;

import lombok.Getter;

@Getter
public class [EntityName]NotFoundException extends RuntimeException {

    private final Long [entity]Id;

    public [EntityName]NotFoundException(final Long [entity]Id) {
        super("[EntityName] non trouve - id: " + [entity]Id);
        this.[entity]Id = [entity]Id;
    }
}

@Getter
public class [EntityName]AlreadyExistsException extends RuntimeException {

    private final String [field]Value;

    public [EntityName]AlreadyExistsException(final String [field]Value) {
        super("[EntityName] deja existe - [field]: " + [field]Value);
        this.[field]Value = [field]Value;
    }
}

@Getter
public class Invalid[EntityName]DataException extends RuntimeException {

    private final String field;

    public Invalid[EntityName]DataException(final String message, final String field) {
        super("Donnees invalides - " + message);
        this.field = field;
    }
}
```

### GlobalExceptionHandler Template (Minimal)

```java
package fr.leadrevive.common.exception;

import fr.leadrevive.[domain].exception.[EntityName]NotFoundException;
import fr.leadrevive.[domain].exception.[EntityName]AlreadyExistsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler([EntityName]NotFoundException.class)
    public ResponseEntity<String> handle[EntityName]NotFoundException(final [EntityName]NotFoundException ex) {
        log.warn("[EntityName] non trouve - id: {}", ex.get[EntityName]Id());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler([EntityName]AlreadyExistsException.class)
    public ResponseEntity<String> handle[EntityName]AlreadyExistsException(final [EntityName]AlreadyExistsException ex) {
        log.warn("[EntityName] deja existe - value: {}", ex.get[Field]Value());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(Invalid[EntityName]DataException.class)
    public ResponseEntity<String> handleInvalid[EntityName]DataException(final Invalid[EntityName]DataException ex) {
        log.error("Donnees invalides - field: {}, message: {}", ex.getField(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
```

### GlobalExceptionHandler Template (Comprehensive with ErrorResponse)

```java
package fr.leadrevive.common.exception;

import tools.jackson.annotation.JsonInclude;
import fr.leadrevive.[domain].exception.*;
import jakarta.validation.ConstraintViolationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler([EntityName]NotFoundException.class)
    public ResponseEntity<ErrorResponse> handle[EntityName]NotFoundException(final [EntityName]NotFoundException ex) {
        log.warn("[EntityName] non trouve - id: {}", ex.get[EntityName]Id());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler([EntityName]AlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handle[EntityName]AlreadyExistsException(final [EntityName]AlreadyExistsException ex) {
        log.warn("[EntityName] deja existe - value: {}", ex.get[Field]Value());
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(Invalid[EntityName]DataException.class)
    public ResponseEntity<ErrorResponse> handleInvalid[EntityName]DataException(final Invalid[EntityName]DataException ex) {
        log.error("Donnees invalides - field: {}, message: {}", ex.getField(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(final MethodArgumentNotValidException ex) {
        final String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation error - {}", message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Validation error: " + message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(final Exception ex) {
        log.error("Erreur interne serveur", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne serveur");
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(final HttpStatus status, final String message) {
        final ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .message(message)
                .build();
        return ResponseEntity.status(status).body(response);
    }

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class ErrorResponse {
        private LocalDateTime timestamp;
        private int status;
        private String message;
        private String details;
    }
}
```

## Example: Lead Exceptions

### LeadNotFoundException.java

```java
package fr.leadrevive.lead.exception;

import lombok.Getter;

@Getter
public class LeadNotFoundException extends RuntimeException {

    private final Long leadId;

    public LeadNotFoundException(final Long leadId) {
        super("Lead non trouve - id: " + leadId);
        this.leadId = leadId;
    }
}
```

### LeadAlreadyExistsException.java

```java
package fr.leadrevive.lead.exception;

import lombok.Getter;

@Getter
public class LeadAlreadyExistsException extends RuntimeException {

    private final String email;

    public LeadAlreadyExistsException(final String email) {
        super("Lead deja existe - email: " + email);
        this.email = email;
    }
}
```

### InvalidLeadDataException.java

```java
package fr.leadrevive.lead.exception;

import lombok.Getter;

@Getter
public class InvalidLeadDataException extends RuntimeException {

    private final String field;

    public InvalidLeadDataException(final String message, final String field) {
        super("Donnees invalides - " + message);
        this.field = field;
    }

    public InvalidLeadDataException(final String message) {
        super("Donnees invalides - " + message);
        this.field = null;
    }
}
```

## Example: GlobalExceptionHandler for Leads

```java
package fr.leadrevive.common.exception;

import fr.leadrevive.lead.exception.LeadNotFoundException;
import fr.leadrevive.lead.exception.LeadAlreadyExistsException;
import fr.leadrevive.lead.exception.InvalidLeadDataException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(LeadNotFoundException.class)
    public ResponseEntity<String> handleLeadNotFoundException(final LeadNotFoundException ex) {
        log.warn("Lead non trouve - id: {}", ex.getLeadId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(LeadAlreadyExistsException.class)
    public ResponseEntity<String> handleLeadAlreadyExistsException(final LeadAlreadyExistsException ex) {
        log.warn("Lead deja existe - email: {}", ex.getEmail());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(InvalidLeadDataException.class)
    public ResponseEntity<String> handleInvalidLeadDataException(final InvalidLeadDataException ex) {
        log.error("Donnees lead invalides - field: {}, message: {}", ex.getField(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleMethodArgumentNotValidException(final MethodArgumentNotValidException ex) {
        final String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation error - {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Validation error: " + message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneralException(final Exception ex) {
        log.error("Erreur interne serveur", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur interne serveur");
    }
}
```

## Key Notes

- **All exceptions extend `RuntimeException`** (no checked exceptions)
- **Include `@Getter`** to expose context fields
- **Log levels**:
  - `warn` for NotFoundException (expected, not an error)
  - `error` for InvalidData / AlreadyExists (business rule violations)
  - `error` for technical errors (with stack trace)
- **One GlobalExceptionHandler** for all custom exceptions
- **HTTP status codes**:
  - 404 NOT_FOUND for NotFoundException
  - 409 CONFLICT for AlreadyExistsException
  - 400 BAD_REQUEST for InvalidDataException
  - 403 FORBIDDEN for UnauthorizedException
  - 500 INTERNAL_SERVER_ERROR for unexpected errors
- **Message format**: "Entity context - detail: value"
- Never expose stack traces to clients (log only)

## Checklist

- [ ] Custom exception classes created in correct package (`fr.leadrevive.[domain].exception`)
- [ ] All exceptions extend `RuntimeException`
- [ ] All exceptions have `@Getter` annotation
- [ ] Exceptions have meaningful context fields
- [ ] Exception messages follow format: "Entity context - detail: value"
- [ ] GlobalExceptionHandler created in `fr.leadrevive.common.exception`
- [ ] GlobalExceptionHandler annotated with `@RestControllerAdvice` and `@Slf4j`
- [ ] One `@ExceptionHandler` method per custom exception type
- [ ] Logging levels correct (warn for not found, error for others)
- [ ] HTTP status codes correct (404, 409, 400, 500)
- [ ] MethodArgumentNotValidException handled (Bean Validation failures)
- [ ] Catch-all Exception handler for unexpected errors
- [ ] Exceptions compile without errors
- [ ] Reviewed CONVENTIONS.md section 10
