package uk.gov.hmcts.reform.tecpoc.ccd;

import java.time.LocalDate;
import java.util.UUID;

public record TecCaseGeneralApplication(
    UUID id,
    int rank,
    GeneralApplicationApplicant applicant,
    LocalDate dateReceived,
    GeneralApplicationType applicationType,
    String somethingElseDetails,
    YesNo within14Days,
    int feeAmountReceived,
    YesNo appliedForHwf,
    String hwfReference,
    YesNo allPartiesAgree,
    YesNo withoutNotice,
    GeneralApplicationState state
) {
}
