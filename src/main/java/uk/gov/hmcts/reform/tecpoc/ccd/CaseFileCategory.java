package uk.gov.hmcts.reform.tecpoc.ccd;

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
}
