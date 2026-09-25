package com.dcdev.pt.unit.dto;

import com.dcdev.pt.item.Category;
import com.dcdev.pt.item.dto.CreateItemRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Input normalisation lives in the request DTO's compact constructor, so it runs
 * before Bean Validation. These tests pin both halves: what the constructor does
 * to the raw values, and that validation then judges the normalised value.
 */
class CreateItemRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("strips surrounding whitespace from name and description, keeping inner spacing")
    void stripsSurroundingWhitespace() {
        CreateItemRequest request = request("  Vintage  Wedding Card \n", "\t A  note  ");

        assertThat(request.name()).isEqualTo("Vintage  Wedding Card");
        assertThat(request.description()).isEqualTo("A  note");
    }

    @Test
    @DisplayName("strips Unicode whitespace that trim() would leave behind")
    void stripsUnicodeWhitespace() {
        // U+2003 EM SPACE and U+3000 IDEOGRAPHIC SPACE are above U+0020, so trim() keeps them.
        CreateItemRequest request = request(" Card　", "　Note ");

        assertThat(request.name()).isEqualTo("Card");
        assertThat(request.description()).isEqualTo("Note");
    }

    @Test
    @DisplayName("a blank description means no description")
    void blankDescriptionBecomesNull() {
        assertThat(request("Card", "   ").description()).isNull();
        assertThat(request("Card", "").description()).isNull();
        assertThat(request("Card", null).description()).isNull();
    }

    @Test
    @DisplayName("a whitespace-only name is normalised to empty and rejected by validation")
    void whitespaceOnlyNameFailsValidation() {
        CreateItemRequest request = request("    ", null);

        assertThat(request.name()).isEmpty();
        assertThat(violatedFields(request)).containsExactly("name");
    }

    @Test
    @DisplayName("a null name stays null and is rejected by validation, not a NullPointerException")
    void nullNameFailsValidation() {
        CreateItemRequest request = request(null, null);

        assertThat(request.name()).isNull();
        assertThat(violatedFields(request)).containsExactly("name");
    }

    @Test
    @DisplayName("length limits apply to the stored (stripped) value, not the raw padded input")
    void sizeLimitIsCheckedAfterStripping() {
        String exactlyMaxLength = "x".repeat(255);
        CreateItemRequest request = request("   " + exactlyMaxLength + "   ", null);

        assertThat(request.name()).hasSize(255);
        assertThat(violatedFields(request)).isEmpty();
    }

    private static CreateItemRequest request(String name, String description) {
        return new CreateItemRequest(name, Category.OTHER, description, new BigDecimal("1.00"));
    }

    private static Set<String> violatedFields(CreateItemRequest request) {
        Set<ConstraintViolation<CreateItemRequest>> violations = validator.validate(request);
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }
}
