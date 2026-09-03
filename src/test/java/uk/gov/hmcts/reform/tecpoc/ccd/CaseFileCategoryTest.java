package uk.gov.hmcts.reform.tecpoc.ccd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CaseFileCategoryTest {

    @Test
    void shouldResolveByIdIgnoringCase() {
        assertThat(CaseFileCategory.resolve("hearingDocuments"))
            .contains(CaseFileCategory.HEARING_DOCUMENTS);
        assertThat(CaseFileCategory.resolve("APPLICATIONS"))
            .contains(CaseFileCategory.APPLICATIONS);
    }

    @Test
    void shouldResolveByLabelIgnoringCase() {
        assertThat(CaseFileCategory.resolve("Orders and notices of hearings"))
            .contains(CaseFileCategory.ORDERS_AND_NOTICES_OF_HEARINGS);
        assertThat(CaseFileCategory.resolve("Uncategorised"))
            .contains(CaseFileCategory.UNCATEGORISED);
        assertThat(CaseFileCategory.resolve("uncategorised"))
            .contains(CaseFileCategory.UNCATEGORISED);
    }

    @Test
    void shouldNormaliseBlankCategoryToUncategorised() {
        assertThat(CaseFileCategory.normalisedCategoryId(null))
            .isEqualTo("uncategorisedDocuments");
        assertThat(CaseFileCategory.normalisedCategoryId("  "))
            .isEqualTo("uncategorisedDocuments");
    }

    @Test
    void shouldRejectUnknownFolder() {
        assertThatThrownBy(() -> CaseFileCategory.require("unknown-folder"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown Case File View folder");
    }
}
