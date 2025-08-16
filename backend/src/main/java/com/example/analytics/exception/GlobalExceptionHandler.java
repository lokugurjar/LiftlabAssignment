package com.example.analytics.exception;

import com.example.analytics.controller.ApiController;
import com.example.analytics.error.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.ConstraintViolationException;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
    List<String> details = ex.getBindingResult().getAllErrors().stream().map(err -> err.getDefaultMessage()).toList();
    return new ErrorResponse("invalid_event", "Validation failed", details);
  }

  @ExceptionHandler(BindException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleBind(BindException ex) {
    List<String> details = ex.getAllErrors().stream().map(err -> err.getDefaultMessage()).toList();
    return new ErrorResponse("invalid_request", "Binding failed", details);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleConstraint(ConstraintViolationException ex) {
    List<String> details = ex.getConstraintViolations().stream().map(v -> v.getPropertyPath() + ": " + v.getMessage()).toList();
    return new ErrorResponse("invalid_request", "Constraint violation", details);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleUnreadable(HttpMessageNotReadableException ex) {
    return new ErrorResponse("invalid_json", "Malformed JSON payload");
  }

  @ExceptionHandler(ApiController.TooManyRequestsException.class)
  @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
  public ErrorResponse handleRate(ApiController.TooManyRequestsException ex) {
    return new ErrorResponse("rate_limit_exceeded", "Too many events");
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ErrorResponse handleOther(Exception ex) {
    return new ErrorResponse("internal_error", "Something went wrong");
  }
}
