package uk.gov.hmcts.reform.tecpoc.ccd;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.ccd.sdk.api.CCD;
import uk.gov.hmcts.ccd.sdk.type.FieldType;

/**
 * Case details projection of the current TE7. Field ids are prefixed {@code te7}.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Te7CaseDetails {

    @CCD(label = "Form validation result", searchable = false)
    private String formValidationResultDisplay;

    @CCD(
        label = "Form",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "TimeExtensionForm",
        searchable = false
    )
    private TimeExtensionForm form;

    @CCD(label = "Penalty Charge Number", searchable = false)
    private String penaltyChargeNumber;

    @CCD(label = "Vehicle reg", searchable = false)
    private String vehicleRegistration;

    @CCD(label = "Title", searchable = false)
    private String title;

    @CCD(label = "Other title", searchable = false)
    private String otherTitle;

    @CCD(label = "Full name", searchable = false)
    private String fullName;

    @CCD(label = "Company name", searchable = false)
    private String companyName;

    @CCD(label = "Address", searchable = false)
    private String address;

    @CCD(label = "Postcode", searchable = false)
    private String postcode;

    @CCD(
        label = "Permission sought",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "TimeExtensionPermissionType",
        searchable = false
    )
    private TimeExtensionPermissionType permissionType;

    @CCD(
        label = "Reasons given",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "YesNo",
        searchable = false
    )
    private YesNo reasonsGiven;

    @CCD(
        label = "Signed and dated",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "YesNo",
        searchable = false
    )
    private YesNo signedAndDated;

    @CCD(
        label = "Signed by",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "TimeExtensionSignedBy",
        searchable = false
    )
    private TimeExtensionSignedBy signedBy;

    @CCD(label = "Date signed", searchable = false)
    private LocalDate dateSigned;

    @CCD(label = "Print full name", searchable = false)
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
