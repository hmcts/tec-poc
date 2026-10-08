package uk.gov.hmcts.reform.tecpoc.ccd;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One persisted registration row, including rows that are no longer current.
 */
public record TecCaseRegistration(
    UUID id,
    Instant createdAt,
    String fileIdentifier,
    String batchIdentifier,
    Long batchCaseReference,
    String penaltyChargeNumber,
    LocalAuthority localAuthority,
    String respondentDetails1,
    String respondentDetails2,
    String respondentDetails3,
    String respondentDetails4,
    String respondentDetails5,
    String respondentDetails6,
    String vehicleRegistrationNumber,
    String natureOfOffence,
    String dateChargeCertificateServed,
    int amountDue,
    String paymentStatus,
    String paymentReference,
    String closureReason,
    String registrationDocument,
    LocalDate registrationDate
) {

    int suffix() {
        return PcnNumbers.suffix(penaltyChargeNumber);
    }
}
