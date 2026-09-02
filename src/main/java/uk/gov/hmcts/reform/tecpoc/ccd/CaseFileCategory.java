package uk.gov.hmcts.reform.tecpoc.ccd;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Document folders shown in the ExUI Case File View for every TEC case.
 */
@RequiredArgsConstructor
@Getter
public enum CaseFileCategory {

    HEARING_DOCUMENTS("hearingDocuments", "Hearing documents", 1),
    ORDERS_AND_NOTICES_OF_HEARINGS(
        "ordersAndNoticesOfHearings",
        "Orders and notices of hearings",
        2
    ),
    APPLICATIONS("applications", "Applications", 3),
    CORRESPONDENCE("correspondence", "Correspondence", 4),
    UNCATEGORISED("uncategorisedDocuments", "Uncategorised", 5);

    private final String id;
    private final String label;
    private final int displayOrder;

    /**
     * Resolves a folder by category id or display label (case-insensitive).
     */
    public static Optional<CaseFileCategory> resolve(String folderOrCategoryId) {
        if (folderOrCategoryId == null || folderOrCategoryId.isBlank()) {
            return Optional.empty();
        }
        String needle = folderOrCategoryId.trim();
        return Arrays.stream(values())
            .filter(category -> category.id.equalsIgnoreCase(needle)
                || category.label.equalsIgnoreCase(needle))
            .findFirst();
    }

    public static CaseFileCategory require(String folderOrCategoryId) {
        return resolve(folderOrCategoryId).orElseThrow(() -> new IllegalArgumentException(
            "Unknown Case File View folder: '" + folderOrCategoryId + "'. "
                + "Use a category id or label from: "
                + knownFoldersDescription()
        ));
    }

    public static String knownFoldersDescription() {
        return Arrays.stream(values())
            .map(category -> category.id + " (\"" + category.label + "\")")
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
    }

    /**
     * Normalises a blank category to Uncategorised; rejects unknown non-blank values.
     */
    public static String normalisedCategoryId(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return UNCATEGORISED.getId();
        }
        return require(categoryId).getId();
    }
}
