package uk.gov.hmcts.reform.tecpoc.ccd;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.ccd.sdk.api.CCD;
import uk.gov.hmcts.ccd.sdk.type.FieldType;

/**
 * Case details projection of the current TE7. Field ids are prefixed {@code te7}.
 * CCD capitalises the first letter after that prefix, so each property is named
 * explicitly ({@code te7Form}).
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Te7CaseDetails {

    @CCD(label = "Form validation result", searchable = false)
    @JsonProperty("FormValidationResultDisplay")
    private String formValidationResultDisplay;

    @CCD(
        label = "Form",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "TimeExtensionForm",
        searchable = false
    )
    @JsonProperty("Form")
    private TimeExtensionForm form;

    @CCD(label = "Penalty Charge Number", searchable = false)
    @JsonProperty("PenaltyChargeNumber")
    private String penaltyChargeNumber;

    @CCD(label = "Vehicle reg", searchable = false)
    @JsonProperty("VehicleRegistration")
    private String vehicleRegistration;

    @CCD(label = "Title", searchable = false)
    @JsonProperty("Title")
    private String title;

    @CCD(label = "Other title", searchable = false)
    @JsonProperty("OtherTitle")
    private String otherTitle;

    @CCD(label = "Full name", searchable = false)
    @JsonProperty("FullName")
    private String fullName;

    @CCD(label = "Company name", searchable = false)
    @JsonProperty("CompanyName")
    private String companyName;

    @CCD(label = "Address", searchable = false)
    @JsonProperty("Address")
    private String address;

    @CCD(label = "Postcode", searchable = false)
    @JsonProperty("Postcode")
    private String postcode;

    @CCD(
        label = "Permission sought",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "TimeExtensionPermissionType",
        searchable = false
    )
    @JsonProperty("PermissionType")
    private TimeExtensionPermissionType permissionType;

    @CCD(
        label = "Reasons given",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "YesNo",
        searchable = false
    )
    @JsonProperty("ReasonsGiven")
    private YesNo reasonsGiven;

    @CCD(
        label = "Signed and dated",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "YesNo",
        searchable = false
    )
    @JsonProperty("SignedAndDated")
    private YesNo signedAndDated;

    @CCD(
        label = "Signed by",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "TimeExtensionSignedBy",
        searchable = false
    )
    @JsonProperty("SignedBy")
    private TimeExtensionSignedBy signedBy;

    @CCD(label = "Date signed", searchable = false)
    @JsonProperty("DateSigned")
    private LocalDate dateSigned;

    @CCD(label = "Print full name", searchable = false)
    @JsonProperty("PrintFullName")
    private String printFullName;

    public void copyToTimeExtension(TecCase target) {
        target.setTimeExtensionForm(TimeExtensionForm.TE7);
        target.setTimeExtensionPenaltyChargeNumber(penaltyChargeNumber);
        target.setTimeExtensionVehicleRegistration(vehicleRegistration);
        target.setTimeExtensionTitle(title);
        target.setTimeExtensionOtherTitle(otherTitle);
        target.setTimeExtensionFullName(fullName);
        target.setTimeExtensionCompanyName(companyName);
        target.setTimeExtensionAddress(address);
        target.setTimeExtensionPostcode(postcode);
        target.setTimeExtensionPermissionType(permissionType);
        target.setTimeExtensionReasonsGiven(reasonsGiven);
        target.setTimeExtensionSignedAndDated(signedAndDated);
        target.setTimeExtensionSignedBy(signedBy);
        target.setTimeExtensionDateSigned(dateSigned);
        target.setTimeExtensionPrintFullName(printFullName);
    }
}
