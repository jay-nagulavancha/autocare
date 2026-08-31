package com.autocare.maintenance.payload.response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ErrorResponseTest {

    private ErrorResponse errorResponse;

    @BeforeEach
    void setUp() {
        errorResponse = new ErrorResponse(400, "Bad Request", "Validation failed");
    }

    @Test
    void testConstructorSetsFieldsCorrectly() {
        assertEquals(400, errorResponse.getStatus());
        assertEquals("Bad Request", errorResponse.getError());
        assertEquals("Validation failed", errorResponse.getMessage());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    void testTimestampIsSetOnConstruction() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        ErrorResponse response = new ErrorResponse(500, "Internal Server Error", "Something went wrong");
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        assertNotNull(response.getTimestamp());
        assertTrue(response.getTimestamp().isAfter(before));
        assertTrue(response.getTimestamp().isBefore(after));
    }

    @Test
    void testGetErrorsReturnsNullWhenNotSet() {
        assertNull(errorResponse.getErrors());
    }

    @Test
    void testSetErrorsAndGetErrors() {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        errors.add(new ErrorResponse.FieldError("field1", "must not be null"));
        errors.add(new ErrorResponse.FieldError("field2", "must be positive"));

        errorResponse.setErrors(errors);

        List<ErrorResponse.FieldError> retrieved = errorResponse.getErrors();
        assertNotNull(retrieved);
        assertEquals(2, retrieved.size());
        assertEquals("field1", retrieved.get(0).getField());
        assertEquals("must not be null", retrieved.get(0).getMessage());
        assertEquals("field2", retrieved.get(1).getField());
        assertEquals("must be positive", retrieved.get(1).getMessage());
    }

    @Test
    void testGetErrorsReturnsCopy_EI_EXPOSE_REP() {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        errors.add(new ErrorResponse.FieldError("field1", "error1"));
        errorResponse.setErrors(errors);

        List<ErrorResponse.FieldError> retrieved = errorResponse.getErrors();
        assertNotNull(retrieved);

        // Modify the retrieved list
        retrieved.add(new ErrorResponse.FieldError("field2", "error2"));

        // Internal state should not be affected
        List<ErrorResponse.FieldError> retrievedAgain = errorResponse.getErrors();
        assertEquals(1, retrievedAgain.size());
    }

    @Test
    void testSetErrorsStoresCopy_EI_EXPOSE_REP2() {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        errors.add(new ErrorResponse.FieldError("field1", "error1"));
        errorResponse.setErrors(errors);

        // Modify the original list after setting
        errors.add(new ErrorResponse.FieldError("field2", "error2"));

        // Internal state should not be affected by external modification
        List<ErrorResponse.FieldError> retrieved = errorResponse.getErrors();
        assertEquals(1, retrieved.size());
        assertEquals("field1", retrieved.get(0).getField());
    }

    @Test
    void testSetErrorsWithNull() {
        errorResponse.setErrors(null);
        assertNull(errorResponse.getErrors());
    }

    @Test
    void testSetErrorsWithNullAfterSettingErrors() {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        errors.add(new ErrorResponse.FieldError("field1", "error1"));
        errorResponse.setErrors(errors);
        assertNotNull(errorResponse.getErrors());

        errorResponse.setErrors(null);
        assertNull(errorResponse.getErrors());
    }

    @Test
    void testSetErrorsWithEmptyList() {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        errorResponse.setErrors(errors);

        List<ErrorResponse.FieldError> retrieved = errorResponse.getErrors();
        assertNotNull(retrieved);
        assertTrue(retrieved.isEmpty());
    }

    @Test
    void testGetErrorsReturnsNewInstanceEachTime() {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        errors.add(new ErrorResponse.FieldError("field1", "error1"));
        errorResponse.setErrors(errors);

        List<ErrorResponse.FieldError> first = errorResponse.getErrors();
        List<ErrorResponse.FieldError> second = errorResponse.getErrors();

        assertNotSame(first, second);
        assertEquals(first.size(), second.size());
    }

    @Test
    void testFieldErrorConstructorAndGetters() {
        ErrorResponse.FieldError fieldError = new ErrorResponse.FieldError("username", "must not be empty");
        assertEquals("username", fieldError.getField());
        assertEquals("must not be empty", fieldError.getMessage());
    }

    @Test
    void testFieldErrorWithNullValues() {
        ErrorResponse.FieldError fieldError = new ErrorResponse.FieldError(null, null);
        assertNull(fieldError.getField());
        assertNull(fieldError.getMessage());
    }

    @Test
    void testConstructorWithDifferentStatusCodes() {
        ErrorResponse notFound = new ErrorResponse(404, "Not Found", "Resource not found");
        assertEquals(404, notFound.getStatus());
        assertEquals("Not Found", notFound.getError());
        assertEquals("Resource not found", notFound.getMessage());

        ErrorResponse serverError = new ErrorResponse(500, "Internal Server Error", "Unexpected error");
        assertEquals(500, serverError.getStatus());
        assertEquals("Internal Server Error", serverError.getError());
        assertEquals("Unexpected error", serverError.getMessage());
    }

    @Test
    void testMultipleFieldErrors() {
        List<ErrorResponse.FieldError> errors = Arrays.asList(
                new ErrorResponse.FieldError("name", "must not be blank"),
                new ErrorResponse.FieldError("age", "must be greater than 0"),
                new ErrorResponse.FieldError("email", "must be a valid email")
        );

        errorResponse.setErrors(errors);

        List<ErrorResponse.FieldError> retrieved = errorResponse.getErrors();
        assertEquals(3, retrieved.size());
        assertEquals("name", retrieved.get(0).getField());
        assertEquals("age", retrieved.get(1).getField());
        assertEquals("email", retrieved.get(2).getField());
    }

    @Test
    void testGetErrorsDoesNotReturnSameReference() {
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        errors.add(new ErrorResponse.FieldError("field1", "error1"));
        errorResponse.setErrors(errors);

        List<ErrorResponse.FieldError> retrieved1 = errorResponse.getErrors();
        List<ErrorResponse.FieldError> retrieved2 = errorResponse.getErrors();

        // Should be different list instances (defensive copy)
        assertNotSame(retrieved1, retrieved2);
    }

    @Test
    void testSetErrorsDoesNotKeepReferenceToOriginal() {
        List<ErrorResponse.FieldError> originalErrors = new ArrayList<>();
        ErrorResponse.FieldError fieldError = new ErrorResponse.FieldError("field1", "error1");
        originalErrors.add(fieldError);

        errorResponse.setErrors(originalErrors);

        // Clear the original list
        originalErrors.clear();

        // The stored errors should still have the original element
        List<ErrorResponse.FieldError> retrieved = errorResponse.getErrors();
        assertNotNull(retrieved);
        assertEquals(1, retrieved.size());
    }
}