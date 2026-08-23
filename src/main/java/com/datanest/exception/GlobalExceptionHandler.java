package com.datanest.exception;

import com.datanest.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(
            FileNotFoundException.class
    )
    public ResponseEntity<ApiResponse<Object>>
    handleFileNotFound(
            FileNotFoundException ex
    ) {

        return ResponseEntity
                .status(
                        HttpStatus.NOT_FOUND
                )
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .message(
                                        ex.getMessage()
                                )
                                .data(null)
                                .build()
                );
    }

    @ExceptionHandler(
            MethodArgumentNotValidException.class
    )
    public ResponseEntity<ApiResponse<Object>>
    handleValidation(
            MethodArgumentNotValidException ex
    ) {

        String message =
                ex.getBindingResult()
                        .getFieldErrors()
                        .get(0)
                        .getDefaultMessage();

        return ResponseEntity
                .badRequest()
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .message(message)
                                .data(null)
                                .build()
                );
    }

    @ExceptionHandler(
            FileNotTrashedException.class
    )
    public ResponseEntity<ApiResponse<Object>>
    handleFileNotTrashed(
            FileNotTrashedException ex
    ) {

        return ResponseEntity
                .status(
                        HttpStatus.CONFLICT
                )
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .message(
                                        ex.getMessage()
                                )
                                .data(null)
                                .build()
                );
    }

    @ExceptionHandler(
            MissingServletRequestParameterException.class
    )
    public ResponseEntity<ApiResponse<Object>>
    handleMissingParameter(
            MissingServletRequestParameterException ex
    ) {

        return ResponseEntity
                .badRequest()
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .message(
                                        ex.getParameterName() + " is required"
                                )
                                .data(null)
                                .build()
                );
    }

    @ExceptionHandler(
            HttpMessageNotReadableException.class
    )
    public ResponseEntity<ApiResponse<Object>>
    handleUnreadableBody(
            HttpMessageNotReadableException ex
    ) {

        return ResponseEntity
                .badRequest()
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .message("Malformed request body")
                                .data(null)
                                .build()
                );
    }

    @ExceptionHandler(
            HttpRequestMethodNotSupportedException.class
    )
    public ResponseEntity<ApiResponse<Object>>
    handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex
    ) {

        return ResponseEntity
                .status(
                        HttpStatus.METHOD_NOT_ALLOWED
                )
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .message(
                                        ex.getMethod() + " is not supported for this endpoint"
                                )
                                .data(null)
                                .build()
                );
    }

    @ExceptionHandler(
            MethodArgumentTypeMismatchException.class
    )
    public ResponseEntity<ApiResponse<Object>>
    handleTypeMismatch(
            MethodArgumentTypeMismatchException ex
    ) {

        return ResponseEntity
                .badRequest()
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .message(
                                        ex.getName() + " is not valid"
                                )
                                .data(null)
                                .build()
                );
    }

    @ExceptionHandler(
            MaxUploadSizeExceededException.class
    )
    public ResponseEntity<ApiResponse<Object>>
    handleUploadTooLarge(
            MaxUploadSizeExceededException ex
    ) {

        return ResponseEntity
                .status(
                        HttpStatus.PAYLOAD_TOO_LARGE
                )
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .message("File is too large")
                                .data(null)
                                .build()
                );
    }

    @ExceptionHandler(
            InvalidSyncCursorException.class
    )
    public ResponseEntity<ApiResponse<Object>>
    handleInvalidSyncCursor(
            InvalidSyncCursorException ex
    ) {

        return ResponseEntity
                .badRequest()
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .message(
                                        ex.getMessage()
                                )
                                .data(null)
                                .build()
                );
    }

    @ExceptionHandler(
            FileVersionConflictException.class
    )
    public ResponseEntity<ApiResponse<Object>>
    handleVersionConflict(
            FileVersionConflictException ex
    ) {

        return ResponseEntity
                .status(
                        HttpStatus.CONFLICT
                )
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .message(
                                        ex.getMessage()
                                )
                                .data(
                                        ex.getCurrent()
                                )
                                .build()
                );
    }

    @ExceptionHandler(
            ObjectOptimisticLockingFailureException.class
    )
    public ResponseEntity<ApiResponse<Object>>
    handleOptimisticLockFailure(
            ObjectOptimisticLockingFailureException ex
    ) {

        return ResponseEntity
                .status(
                        HttpStatus.CONFLICT
                )
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .message(
                                        "Version conflict"
                                )
                                .data(null)
                                .build()
                );
    }

    @ExceptionHandler(
            AccessDeniedException.class
    )
    public ResponseEntity<ApiResponse<Object>>
    handleAccessDenied(
            AccessDeniedException ex
    ) {

        return ResponseEntity
                .status(
                        HttpStatus.FORBIDDEN
                )
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .message("Access denied")
                                .data(null)
                                .build()
                );
    }

    @ExceptionHandler(
            NoResourceFoundException.class
    )
    public ResponseEntity<ApiResponse<Object>>
    handleNoResourceFound(
            NoResourceFoundException ex
    ) {

        return ResponseEntity
                .status(
                        HttpStatus.NOT_FOUND
                )
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .message("No endpoint for this path")
                                .data(null)
                                .build()
                );
    }

    @ExceptionHandler(
            Exception.class
    )
    public ResponseEntity<ApiResponse<Object>>
    handleGenericException(
            Exception ex
    ) {

        ex.printStackTrace();

        return ResponseEntity
                .status(
                        HttpStatus.INTERNAL_SERVER_ERROR
                )
                .body(
                        ApiResponse.builder()
                                .success(false)
                                .message(
                                        "Internal server error"
                                )
                                .data(null)
                                .build()
                );
    }
}