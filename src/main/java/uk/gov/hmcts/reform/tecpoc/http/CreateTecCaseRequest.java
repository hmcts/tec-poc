package uk.gov.hmcts.reform.tecpoc.http;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import uk.gov.hmcts.reform.tecpoc.ccd.TecCase;

public record CreateTecCaseRequest(
    @NotBlank
    @Pattern(regexp = "^R[A-Z]{2,3}[0-9]{5}$")
    String fileIdentifier,
    @NotBlank
    @Pattern(regexp = "^R[A-Z]{2,3}[0-9]{6}$")
    String batchIdentifier,
    @NotBlank
    @Pattern(regexp = "^[A-Z]{2,3}[0-9]{7}[0-9A][0-9]$")
    String penaltyChargeNumber,
    @NotBlank @Size(max = 30) @Pattern(regexp = "^\\P{Ll}*$") String respondentDetails1,
    @NotBlank @Size(max = 30) @Pattern(regexp = "^\\P{Ll}*$") String respondentDetails2,
    @NotBlank @Size(max = 30) @Pattern(regexp = "^\\P{Ll}*$") String respondentDetails3,
    @Size(max = 30) @Pattern(regexp = "^\\P{Ll}*$") String respondentDetails4,
    @Size(max = 30) @Pattern(regexp = "^\\P{Ll}*$") String respondentDetails5,
    @Size(max = 30) @Pattern(regexp = "^\\P{Ll}*$") String respondentDetails6,
    @NotBlank @Size(max = 10) @Pattern(regexp = "^[A-Z0-9]+$") String vehicleRegistrationNumber,
    @NotBlank @Pattern(regexp = "^[0-9]{2}$") String natureOfOffence,
    @NotBlank @Pattern(regexp = "^[0-9]{6}$") String dateChargeCertificateServed,
    @NotNull
    @Min(0)
    @Max(999999)
    Integer amountDue
) {

    public TecCase toCaseData() {
        TecCase tecCase = new TecCase();
        tecCase.setFileIdentifier(fileIdentifier);
        tecCase.setBatchIdentifier(batchIdentifier);
        tecCase.setPenaltyChargeNumber(penaltyChargeNumber);
        tecCase.setRespondentDetails1(respondentDetails1);
        tecCase.setRespondentDetails2(respondentDetails2);
        tecCase.setRespondentDetails3(respondentDetails3);
        tecCase.setRespondentDetails4(respondentDetails4);
        tecCase.setRespondentDetails5(respondentDetails5);
        tecCase.setRespondentDetails6(respondentDetails6);
        tecCase.setVehicleRegistrationNumber(vehicleRegistrationNumber);
        tecCase.setNatureOfOffence(natureOfOffence);
        tecCase.setDateChargeCertificateServed(dateChargeCertificateServed);
        tecCase.setAmountDue(amountDue);
        return tecCase;
    }

    @AssertTrue(message = "file, batch and penalty charge identifiers must have the same authority prefix")
    @JsonIgnore
    public boolean isIdentifierPrefixConsistent() {
        if (fileIdentifier == null || batchIdentifier == null || penaltyChargeNumber == null
            || !fileIdentifier.matches("^R[A-Z]{2,3}[0-9]{5}$")
            || !batchIdentifier.matches("^R[A-Z]{2,3}[0-9]{6}$")
            || !penaltyChargeNumber.matches("^[A-Z]{2,3}[0-9]{7}[0-9A][0-9]$")) {
            return true;
        }

        String filePrefix = fileIdentifier.substring(1, fileIdentifier.length() - 5);
        String batchPrefix = batchIdentifier.substring(1, batchIdentifier.length() - 6);
        String penaltyChargePrefix = penaltyChargeNumber.substring(0, penaltyChargeNumber.length() - 9);
        return filePrefix.equals(batchPrefix) && filePrefix.equals(penaltyChargePrefix);
    }
}
