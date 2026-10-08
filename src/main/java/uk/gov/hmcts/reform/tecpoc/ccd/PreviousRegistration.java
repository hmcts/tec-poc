package uk.gov.hmcts.reform.tecpoc.ccd;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.ccd.sdk.api.CCD;
import uk.gov.hmcts.ccd.sdk.type.CaseLink;
import uk.gov.hmcts.ccd.sdk.type.FieldType;

/**
 * One earlier registration on the Previous registrations tab.
 * The current registration stays on Case details.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PreviousRegistration {

    @CCD(label = "File identifier", searchable = false)
    private String fileIdentifier;

    @CCD(label = "Batch identifier", searchable = false)
    private String batchIdentifier;

    @CCD(label = "Batch case", searchable = false)
    private CaseLink batchCase;

    @CCD(label = "Penalty charge number", searchable = false)
    private String penaltyChargeNumber;

    @CCD(
        label = "Local authority",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "LocalAuthority",
        searchable = false
    )
    private LocalAuthority localAuthority;

    @CCD(label = "Respondent details 1", searchable = false)
    private String respondentDetails1;

    @CCD(label = "Respondent details 2", searchable = false)
    private String respondentDetails2;

    @CCD(label = "Respondent details 3", searchable = false)
    private String respondentDetails3;

    @CCD(label = "Respondent details 4", searchable = false)
    private String respondentDetails4;

    @CCD(label = "Respondent details 5", searchable = false)
    private String respondentDetails5;

    @CCD(label = "Respondent details 6", searchable = false)
    private String respondentDetails6;

    @CCD(label = "Vehicle registration number", searchable = false)
    private String vehicleRegistrationNumber;

    @CCD(label = "Nature of offence", searchable = false)
    private String natureOfOffence;

    @CCD(label = "Date charge certificate served", searchable = false)
    private String dateChargeCertificateServed;

    @CCD(
        label = "Amount due",
        typeOverride = FieldType.MoneyGBP,
        min = 0,
        max = 999999,
        searchable = false
    )
    @JsonSerialize(using = ToStringSerializer.class)
    private Integer amountDue;

    @CCD(label = "Payment status", searchable = false)
    private String paymentStatus;

    @CCD(label = "Payment reference", searchable = false)
    private String paymentReference;

    @CCD(label = "Closure reason", searchable = false)
    private String closureReason;

    @CCD(label = "Registration authorisation document", searchable = false)
    private String registrationDocument;

    @CCD(label = "Registration date", searchable = false)
    private LocalDate registrationDate;
}
