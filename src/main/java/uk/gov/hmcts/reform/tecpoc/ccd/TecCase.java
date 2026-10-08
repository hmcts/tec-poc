package uk.gov.hmcts.reform.tecpoc.ccd;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import uk.gov.hmcts.ccd.sdk.api.CCD;
import uk.gov.hmcts.ccd.sdk.type.CaseLink;
import uk.gov.hmcts.ccd.sdk.type.ComponentLauncher;
import uk.gov.hmcts.ccd.sdk.type.Document;
import uk.gov.hmcts.ccd.sdk.type.FieldType;
import uk.gov.hmcts.ccd.sdk.type.FlagLauncher;
import uk.gov.hmcts.ccd.sdk.type.Flags;
import uk.gov.hmcts.ccd.sdk.type.ListValue;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TecCase {

    /**
     * Case-view display for CCD state. Populated by {@link TecCaseView} so ExUI can show
     * State at the top of Case details (state itself is not case data).
     */
    @CCD(label = "State")
    private String statusDisplay;

    @CCD(label = "File identifier")
    private String fileIdentifier;

    @CCD(label = "Batch identifier")
    private String batchIdentifier;

    /**
     * CCD link to the registration {@code TEC_BATCH} case (Case details only).
     * Persisted as {@code tec_case.batch_case_reference}; reconstructed by {@link TecCaseView}.
     * Not used as the {@code linkBatchCase} event target — see {@link #batchLinkCase}.
     */
    @CCD(label = "Batch case")
    private CaseLink batchCase;

    /**
     * Target batch for the {@code linkBatchCase} event (any {@link BatchOperation}).
     * Kept separate from {@link #batchCase} so non-registration links do not overwrite the
     * registration Case details CaseLink or its CCD {@code case_link} row.
     * Cleared by {@link TecCaseView} (event-only).
     */
    @CCD(label = "Linked batch case", searchable = false)
    private CaseLink batchLinkCase;

    /**
     * Batch type for the {@code linkBatchCase} event. Must match the target batch's
     * {@link BatchOperation}. Not shown on Case details.
     */
    @CCD(
        label = "Batch link type",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "BatchOperation",
        searchable = false
    )
    private BatchOperation batchLinkType;

    @CCD(label = "Penalty charge number")
    private String penaltyChargeNumber;

    @CCD(
        label = "Local authority",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "LocalAuthority"
    )
    private LocalAuthority localAuthority;

    /**
     * CCD case-access category (exact field id). Set from {@link #localAuthority} on create/upload;
     * not shown on case-view tabs.
     */
    @CCD(label = "Case access category", searchable = false)
    @JsonProperty("CaseAccessCategory")
    private String caseAccessCategory;

    @CCD(label = "Respondent details 1")
    private String respondentDetails1;

    @CCD(label = "Respondent details 2")
    private String respondentDetails2;

    @CCD(label = "Respondent details 3")
    private String respondentDetails3;

    @CCD(label = "Respondent details 4")
    private String respondentDetails4;

    @CCD(label = "Respondent details 5")
    private String respondentDetails5;

    @CCD(label = "Respondent details 6")
    private String respondentDetails6;

    @CCD(label = "Vehicle registration number")
    private String vehicleRegistrationNumber;

    @CCD(label = "Nature of offence")
    private String natureOfOffence;

    @CCD(label = "Date charge certificate served")
    private String dateChargeCertificateServed;

    @CCD(label = "Amount due", typeOverride = FieldType.MoneyGBP, min = 0, max = 999999)
    @JsonSerialize(using = ToStringSerializer.class)
    private Integer amountDue;

    @CCD(label = "Payment status")
    private String paymentStatus;

    @CCD(label = "Payment reference")
    private String paymentReference;

    @CCD(label = "Closure reason")
    private String closureReason;

    @CCD(label = "Registration authorisation document")
    private String registrationDocument;

    @CCD(label = "Registration date")
    private LocalDate registrationDate;

    /**
     * Case-view display for the local authority's out-of-time refusal. Populated by
     * {@link TecCaseView} only while the case is {@link CaseState#PENDING_REFUSAL_DECISION}.
     */
    @CCD(label = "Decision", searchable = false)
    private String ootApplicationDecisionDisplay;

    /**
     * Event-only choice for {@code reviewOotRefusalDecision}. Not shown on Case details.
     */
    @CCD(
        label = "Decision",
        typeOverride = FieldType.FixedRadioList,
        typeParameterOverride = "OotRefusalReviewDecision",
        searchable = false
    )
    private OotRefusalReviewDecision ootRefusalReviewDecision;

    @CCD(
        label = "Form validation result",
        typeOverride = FieldType.FixedRadioList,
        typeParameterOverride = "FormValidationResult"
    )
    private FormValidationResult formValidationResult;

    @CCD(label = "Comment", typeOverride = FieldType.TextArea)
    private String formValidationComment;

    /**
     * Event-only draft for {@code verifyFormValidation} when the clerk marks the form invalid.
     * Not stored on the case; the submitted text is copied into the event description.
     */
    @CCD(label = "Rejection email", typeOverride = FieldType.TextArea, searchable = false)
    private String ootRejectionEmail;

    /**
     * Case-view display for form validation at the top of the TE9/PE3 section. Always populated so
     * ExUI shows the row even when {@link #formValidationResult} is unset
     * ({@code @JsonInclude(NON_NULL)} would otherwise omit it).
     */
    @CCD(label = "Form validation result", searchable = false)
    private String formValidationResultDisplay;

    /**
     * Case-view display for the same shared form validation result at the top of the TE7/PE2
     * section. Separate field id so CCD can show the value under both form headings.
     */
    @CCD(label = "Form validation result", searchable = false)
    private String timeExtensionFormValidationResultDisplay;

    @CCD(label = "Date received")
    private LocalDate applicationDateReceived;

    @CCD(
        label = "Type",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "ApplicationTimeliness"
    )
    private ApplicationTimeliness applicationType;

    @CCD(
        label = "TE7 submitted?",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "YesNo"
    )
    private YesNo applicationTe7Submitted;

    @CCD(
        label = "Form",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "ApplicationForm"
    )
    private ApplicationForm applicationForm;

    @CCD(label = "Penalty Charge Number")
    private String applicationPenaltyChargeNumber;

    @CCD(label = "Vehicle reg")
    private String applicationVehicleRegistration;

    @CCD(label = "Applicant")
    private String applicationApplicant;

    @CCD(label = "Location of contravention")
    private String applicationLocationOfContravention;

    @CCD(label = "Date of contravention")
    private LocalDate applicationDateOfContravention;

    @CCD(label = "Title")
    private String applicationTitle;

    @CCD(label = "Full name")
    private String applicationFullName;

    @CCD(label = "Company name")
    private String applicationCompanyName;

    @CCD(label = "Address")
    private String applicationAddress;

    @CCD(label = "Postcode")
    private String applicationPostcode;

    @CCD(
        label = "Declaration",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "ApplicationDeclaration"
    )
    private ApplicationDeclaration applicationDeclaration;

    @CCD(
        label = "Reasons given",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "YesNo"
    )
    private YesNo applicationReasonsGiven;

    @CCD(label = "Date it was paid")
    private LocalDate applicationDatePaid;

    @CCD(label = "How it was paid")
    private String applicationHowPaid;

    @CCD(label = "To whom it was paid")
    private String applicationPaidTo;

    @CCD(
        label = "Form",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "TimeExtensionForm"
    )
    private TimeExtensionForm timeExtensionForm;

    @CCD(label = "Penalty Charge Number")
    private String timeExtensionPenaltyChargeNumber;

    @CCD(label = "Vehicle reg")
    private String timeExtensionVehicleRegistration;

    @CCD(label = "Applicant")
    private String timeExtensionApplicant;

    @CCD(label = "Location of contravention")
    private String timeExtensionLocationOfContravention;

    @CCD(label = "Date of contravention")
    private LocalDate timeExtensionDateOfContravention;

    @CCD(label = "Title")
    private String timeExtensionTitle;

    @CCD(label = "Other title")
    private String timeExtensionOtherTitle;

    @CCD(label = "Full name")
    private String timeExtensionFullName;

    @CCD(label = "Company name")
    private String timeExtensionCompanyName;

    @CCD(label = "Address")
    private String timeExtensionAddress;

    @CCD(label = "Postcode")
    private String timeExtensionPostcode;

    @CCD(
        label = "Permission sought",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "TimeExtensionPermissionType"
    )
    private TimeExtensionPermissionType timeExtensionPermissionType;

    @CCD(
        label = "Reasons given",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "YesNo"
    )
    private YesNo timeExtensionReasonsGiven;

    @CCD(
        label = "Signed and dated",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "YesNo"
    )
    private YesNo timeExtensionSignedAndDated;

    @CCD(
        label = "Signed by",
        typeOverride = FieldType.FixedList,
        typeParameterOverride = "TimeExtensionSignedBy"
    )
    private TimeExtensionSignedBy timeExtensionSignedBy;

    @CCD(label = "Date signed")
    private LocalDate timeExtensionDateSigned;

    @CCD(label = "Print full name")
    private String timeExtensionPrintFullName;

    /**
     * Event-only field used by {@code setCaseState}. Value must be a {@link CaseState} name.
     */
    @CCD(label = "Target case state", searchable = false)
    private String targetCaseState;

    /**
     * Event-only field used by {@code applyWarrantAuthorisation}.
     */
    @CCD(label = "Warrant authorisation", searchable = false)
    private WarrantAuthorisation warrantAuthorisation;

    /**
     * Event-only form for {@code enterGeneralApplication}. Unwrapped so the answers
     * are case fields ({@code genApp...}) for page show conditions. Cleared by {@link TecCaseView}.
     */
    @JsonUnwrapped(prefix = "genApp")
    @CCD(label = "General application", searchable = false)
    private GeneralApplicationEntry generalApplication;

    /**
     * General applications shown on Case details. Populated by {@link TecCaseView}.
     */
    @CCD(
        label = "General applications",
        typeOverride = FieldType.Collection,
        typeParameterOverride = "GeneralApplication"
    )
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<ListValue<GeneralApplication>> generalApplications;

    /**
     * Warrant authorisations shown on Case details. Populated by {@link TecCaseView}.
     */
    @CCD(
        label = "Warrant authorisations",
        typeOverride = FieldType.Collection,
        typeParameterOverride = "WarrantAuthorisation"
    )
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<ListValue<WarrantAuthorisation>> warrantAuthorisations;

    /**
     * Case File View source documents. Populated by {@link TecCaseView}; not shown on Case details.
     */
    @CCD(label = "All documents", searchable = false)
    private List<ListValue<Document>> allDocuments;

    /**
     * Event-only field used by {@code attachCaseFileDocument}.
     */
    @CCD(label = "Case file document", searchable = false)
    private Document caseFileDocument;

    @CCD(label = "Case file view")
    private ComponentLauncher caseFileView;

    /**
     * Standard CCD Linked Cases collection. Field id must remain {@code caseLinks}.
     * Batch links are owned by the batch case ({@code caseLinks} there) so the PCN
     * shows them under ExUI "linked from", not in this collection.
     * Registration membership is {@code tec_case.batch_case_reference}; other batch
     * types use {@code tec_batch_pcn_link}.
     */
    @CCD(
        label = "Linked cases",
        typeOverride = FieldType.Collection,
        typeParameterOverride = "CaseLink"
    )
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<ListValue<CaseLink>> caseLinks;

    @CCD(label = "Component Launcher (for displaying Linked Cases data)")
    @JsonProperty("LinkedCasesComponentLauncher")
    private ComponentLauncher linkedCasesComponentLauncher;

    /**
     * CCD shell for the ExUI Roles and access tab. Not the real Work Allocation / CAA UI.
     */
    @CCD(label = "Roles and access", searchable = false)
    private String rolesAndAccessMarkdown;

    /**
     * Case Flags component for the Parties tab. These stay empty: the data store
     * has no validator for {@code FlagLauncher}, so a value here fails event submit.
     * Field id {@code parties} must stay as-is: ExUI treats that collection as
     * party-level flags ({@code #ARGUMENT(Flags)}).
     */
    @CCD(label = "Launch the flags screen", searchable = false)
    private FlagLauncher flagLauncher;

    @CCD(label = "Case Flags", searchable = false)
    private Flags caseFlags;

    @CCD(label = "Party", searchable = false)
    private List<ListValue<Flags>> parties;

    @CCD(
        label = "Payment History",
        typeOverride = FieldType.CasePaymentHistoryViewer,
        searchable = false
    )
    private String casePaymentHistoryViewer;

    @CCD(label = "Tasks", searchable = false)
    private String tasksMarkdown;

    /**
     * Stub content for the Previous registrations tab. Populated by {@link TecCaseView}.
     */
    @CCD(label = "Previous registrations", searchable = false)
    private String previousRegistrationsMarkdown;
}
