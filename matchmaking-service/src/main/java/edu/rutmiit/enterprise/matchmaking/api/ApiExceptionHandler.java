package edu.rutmiit.enterprise.matchmaking.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail handleMissingHeader(MissingRequestHeaderException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Отсутствует обязательный заголовок " + exception.getHeaderName());
    }

    @ExceptionHandler(CannotAcquireLockException.class)
    ProblemDetail handleConcurrentCommand() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Конкурирующая команда ещё выполняется; согласуйте результат или повторите с тем же ключом позже");
    }

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApiException(ApiException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(exception.getStatus(), exception.getMessage());
        detail.setType(URI.create("https://course.local/problems/domain-rule"));
        detail.setTitle("Операция не выполнена");
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail detail = ProblemDetail.forStatus(400);
        detail.setType(URI.create("https://course.local/problems/validation"));
        detail.setTitle("Ошибка валидации");
        detail.setDetail(exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Проверьте тело запроса"));
        return detail;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleConstraintConflict() {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Изменение нарушает ограничение целостности данных"
        );
        detail.setType(URI.create("https://course.local/problems/data-conflict"));
        detail.setTitle("Конфликт данных");
        return detail;
    }
}
