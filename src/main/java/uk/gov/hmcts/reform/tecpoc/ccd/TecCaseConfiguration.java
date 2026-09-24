package uk.gov.hmcts.reform.tecpoc.ccd;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.sdk.api.CCDConfig;
import uk.gov.hmcts.ccd.sdk.api.DecentralisedConfigBuilder;
import uk.gov.hmcts.ccd.sdk.api.EventMetadata;
import uk.gov.hmcts.ccd.sdk.api.EventPayload;
import uk.gov.hmcts.ccd.sdk.api.Permission;
import uk.gov.hmcts.ccd.sdk.api.callback.SubmitResponse;
import uk.gov.hmcts.ccd.sdk.type.CaseLink;
import uk.gov.hmcts.ccd.sdk.type.Document;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Component
public class TecCaseConfiguration implements CCDConfig<TecCase, CaseState, UserRole> {

    public static final String CASE_TYPE = "TEC";
    private static final String NEVER_SHOW = "[STATE]=\"NEVER_SHOW\"";

    private final TecCaseRepository repository;
    private final BatchCaseRepository batchCaseRepository;

    public TecCaseConfiguration(
        @Lazy TecCaseRepository repository,
        @Lazy BatchCaseRepository batchCaseRepository
    ) {
        this.repository = repository;
        this.batchCaseRepository = batchCaseRepository;
    }

    @Override
    public void configureDecentralised(DecentralisedConfigBuilder<TecCase, CaseState, UserRole> builder) {
        builder.caseType(CASE_TYPE, "TEC PCN", "A TEC PCN case");
        builder.jurisdiction("TEC", "Traffic Enforcement Centre", "Traffic Enforcement Centre");
        builder.hmctsServiceId("TEC1");
        builder.setCallbackHost(System.getenv().getOrDefault("API_URL", "http://localhost:4013"));

        configureAccessProfiles(builder);
        configureStateAccess(builder);
        configureCaseView(builder);
        configureCaseFileCategories(builder);
        configureEvents(builder);
    }

    private void configureCaseFileCategories(DecentralisedConfigBuilder<TecCase, CaseState, UserRole> builder) {
        for (CaseFileCategory category : CaseFileCategory.values()) {
            for (UserRole role : List.of(UserRole.CLERK, UserRole.LOCAL_AUTHORITY)) {
                builder.categories(role)
                    .categoryID(category.getId())
                    .categoryLabel(category.getLabel())
                    .displayOrder(category.getDisplayOrder());
            }
        }
    }

    private void configureAccessProfiles(DecentralisedConfigBuilder<TecCase, CaseState, UserRole> builder) {
        builder.caseRoleToAccessProfile(UserRole.SYSTEM)
            .accessProfiles(UserRole.SYSTEM.getRole())
            .legacyIdamRole();
        builder.caseRoleToAccessProfile(UserRole.CLERK)
            .accessProfiles(UserRole.CLERK.getRole())
            .legacyIdamRole();
        builder.caseRoleToAccessProfile(UserRole.LOCAL_AUTHORITY)
            .accessProfiles(UserRole.LOCAL_AUTHORITY.getRole())
            .caseAccessCategories(GreaterManchesterLocalAuthorities.accessCategoryCodes())
            .legacyIdamRole();
    }

    private void configureStateAccess(DecentralisedConfigBuilder<TecCase, CaseState, UserRole> builder) {
        for (CaseState state : CaseState.values()) {
            builder.grant(state, Permission.CRUD, UserRole.SYSTEM);
            builder.grant(state, Set.of(Permission.R, Permission.U), UserRole.CLERK);
            builder.grant(state, Set.of(Permission.R), UserRole.LOCAL_AUTHORITY);
        }
    }

    private void configureCaseView(DecentralisedConfigBuilder<TecCase, CaseState, UserRole> builder) {
        builder.tab("tasks", "Tasks")
            .forRoles(UserRole.CLERK, UserRole.SYSTEM)
            .label("tasksMarkdownLabel", null, "${tasksMarkdown}")
            .field("tasksMarkdown", NEVER_SHOW);

        // CCD shell only — real ExUI Roles and access is prepended when WA is enabled for the jurisdiction.
        builder.tab("rolesAndAccess", "Roles and access")
            .label("rolesAndAccessLabel", null, "${rolesAndAccessMarkdown}")
            .field("rolesAndAccessMarkdown", NEVER_SHOW);

        builder.tab("caseDetails", "Case details")
            .field(TecCase::getStatusDisplay)
            .label("registrationSection", null, "## Registration")
            .field(TecCase::getFileIdentifier)
            .field(TecCase::getBatchIdentifier)
            .field(TecCase::getBatchCase)
            .field(TecCase::getPenaltyChargeNumber)
            .field(TecCase::getLocalAuthority)
            .field(TecCase::getRespondentDetails1)
            .field(TecCase::getRespondentDetails2)
            .field(TecCase::getRespondentDetails3)
            .field(TecCase::getRespondentDetails4)
            .field(TecCase::getRespondentDetails5)
            .field(TecCase::getRespondentDetails6)
            .field(TecCase::getVehicleRegistrationNumber)
            .field(TecCase::getNatureOfOffence)
            .field(TecCase::getDateChargeCertificateServed)
            .field(TecCase::getAmountDue)
            .field(TecCase::getPaymentStatus)
            .field(TecCase::getPaymentReference)
            .field(TecCase::getClosureReason)
            .field(TecCase::getRegistrationDocument)
            .field(TecCase::getRegistrationDate)
            .label(
                "applicationsSectionTe9InTime",
                "applicationForm=\"TE9\" AND applicationType=\"inTime\"",
                "## Witness statement - In time"
            )
            .label(
                "applicationsSectionTe9OutOfTime",
                "applicationForm=\"TE9\" AND applicationType=\"outOfTime\"",
                "## Witness statement - Out of time"
            )
            .label(
                "applicationsSectionPe3InTime",
                "applicationForm=\"PE3\" AND applicationType=\"inTime\"",
                "## Statutory declaration - In time"
            )
            .label(
                "applicationsSectionPe3OutOfTime",
                "applicationForm=\"PE3\" AND applicationType=\"outOfTime\"",
                "## Statutory declaration - Out of time"
            )
            .field(
                TecCase::getFormValidationResultDisplay,
                "applicationForm=\"TE9\" OR applicationForm=\"PE3\""
            )
            .field(TecCase::getApplicationDateReceived, "applicationForm=\"TE9\" OR applicationForm=\"PE3\"")
            .field(TecCase::getApplicationType, "applicationForm=\"TE9\" OR applicationForm=\"PE3\"")
            .field(
                TecCase::getApplicationTe7Submitted,
                "(applicationForm=\"TE9\" OR applicationForm=\"PE3\") AND applicationType=\"outOfTime\""
            )
            .field(TecCase::getApplicationForm, "applicationForm=\"TE9\" OR applicationForm=\"PE3\"")
            .field(
                TecCase::getApplicationPenaltyChargeNumber,
                "applicationForm=\"TE9\" OR applicationForm=\"PE3\""
            )
            .field(
                TecCase::getApplicationVehicleRegistration,
                "applicationForm=\"TE9\" OR applicationForm=\"PE3\""
            )
            .field(TecCase::getApplicationApplicant, "applicationForm=\"TE9\" OR applicationForm=\"PE3\"")
            .field(
                TecCase::getApplicationLocationOfContravention,
                "applicationForm=\"TE9\" OR applicationForm=\"PE3\""
            )
            .field(
                TecCase::getApplicationDateOfContravention,
                "applicationForm=\"TE9\" OR applicationForm=\"PE3\""
            )
            .field(TecCase::getApplicationTitle, "applicationForm=\"TE9\" OR applicationForm=\"PE3\"")
            .field(TecCase::getApplicationFullName, "applicationForm=\"TE9\" OR applicationForm=\"PE3\"")
            .field(TecCase::getApplicationCompanyName, "applicationForm=\"TE9\" OR applicationForm=\"PE3\"")
            .field(TecCase::getApplicationAddress, "applicationForm=\"TE9\" OR applicationForm=\"PE3\"")
            .field(TecCase::getApplicationPostcode, "applicationForm=\"TE9\" OR applicationForm=\"PE3\"")
            .field(TecCase::getApplicationDeclaration, "applicationForm=\"TE9\" OR applicationForm=\"PE3\"")
            .field(TecCase::getApplicationReasonsGiven, "applicationForm=\"PE3\"")
            .field(
                TecCase::getApplicationDatePaid,
                "(applicationForm=\"TE9\" OR applicationForm=\"PE3\") AND applicationDeclaration=\"paidInFull\""
            )
            .field(
                TecCase::getApplicationHowPaid,
                "(applicationForm=\"TE9\" OR applicationForm=\"PE3\") AND applicationDeclaration=\"paidInFull\""
            )
            .field(
                TecCase::getApplicationPaidTo,
                "(applicationForm=\"TE9\" OR applicationForm=\"PE3\") AND applicationDeclaration=\"paidInFull\""
            )
            .label(
                "timeExtensionSectionTe7OutOfTime",
                "timeExtensionForm=\"TE7\" AND timeExtensionPermissionType=\"outsideTheGivenTime\"",
                "## Application to file out of time"
            )
            .label(
                "timeExtensionSectionTe7Extension",
                "timeExtensionForm=\"TE7\" AND timeExtensionPermissionType=\"forMoreTime\"",
                "## Application for extension of time"
            )
            .label(
                "timeExtensionSectionPe2",
                "timeExtensionForm=\"PE2\"",
                "## Application to file out of time"
            )
            .field(
                TecCase::getTimeExtensionFormValidationResultDisplay,
                "timeExtensionForm=\"TE7\" OR timeExtensionForm=\"PE2\""
            )
            .field(TecCase::getTimeExtensionForm, "timeExtensionForm=\"TE7\" OR timeExtensionForm=\"PE2\"")
            .field(
                TecCase::getTimeExtensionPenaltyChargeNumber,
                "timeExtensionForm=\"TE7\" OR timeExtensionForm=\"PE2\""
            )
            .field(
                TecCase::getTimeExtensionVehicleRegistration,
                "timeExtensionForm=\"TE7\" OR timeExtensionForm=\"PE2\""
            )
            .field(TecCase::getTimeExtensionApplicant, "timeExtensionForm=\"PE2\"")
            .field(TecCase::getTimeExtensionLocationOfContravention, "timeExtensionForm=\"PE2\"")
            .field(TecCase::getTimeExtensionDateOfContravention, "timeExtensionForm=\"PE2\"")
            .field(TecCase::getTimeExtensionTitle, "timeExtensionForm=\"TE7\"")
            .field(TecCase::getTimeExtensionOtherTitle, "timeExtensionForm=\"TE7\" AND timeExtensionTitle=\"Other\"")
            .field(TecCase::getTimeExtensionFullName, "timeExtensionForm=\"TE7\" OR timeExtensionForm=\"PE2\"")
            .field(TecCase::getTimeExtensionCompanyName, "timeExtensionForm=\"TE7\"")
            .field(TecCase::getTimeExtensionAddress, "timeExtensionForm=\"TE7\" OR timeExtensionForm=\"PE2\"")
            .field(TecCase::getTimeExtensionPostcode, "timeExtensionForm=\"TE7\" OR timeExtensionForm=\"PE2\"")
            .field(TecCase::getTimeExtensionPermissionType, "timeExtensionForm=\"TE7\"")
            .field(TecCase::getTimeExtensionReasonsGiven, "timeExtensionForm=\"TE7\" OR timeExtensionForm=\"PE2\"")
            .field(TecCase::getTimeExtensionSignedAndDated, "timeExtensionForm=\"TE7\" OR timeExtensionForm=\"PE2\"")
            .field(TecCase::getTimeExtensionSignedBy, "timeExtensionForm=\"TE7\"")
            .field(TecCase::getTimeExtensionDateSigned, "timeExtensionForm=\"TE7\" OR timeExtensionForm=\"PE2\"")
            .field(TecCase::getTimeExtensionPrintFullName, "timeExtensionForm=\"TE7\"")
            .label(
                "warrantAuthorisationsSection",
                "warrantAuthorisations!=\"\"",
                "## Warrant authorisations"
            )
            .field(TecCase::getWarrantAuthorisations, "warrantAuthorisations!=\"\"");

        builder.tab("caseFileView", "Case File View")
            .field(TecCase::getCaseFileView, null, "#ARGUMENT(CaseFileView)")
            .field(TecCase::getAllDocuments, NEVER_SHOW);

        // Explicit CaseHistory so History sits after Case File View (SDK otherwise prepends it).
        builder.tab("CaseHistory", "History")
            .field("caseHistory");

        builder.tab("caseLinks", "Linked Cases")
            .field(TecCase::getLinkedCasesComponentLauncher, null, "#ARGUMENT(LinkedCases)")
            .field(
                TecCase::getCaseLinks,
                "LinkedCasesComponentLauncher!=\"\"",
                "#ARGUMENT(LinkedCases)"
            );

        builder.tab("paymentHistory", "Payment History")
            .field(TecCase::getCasePaymentHistoryViewer);

        builder.searchInputFields()
            .field(TecCase::getPenaltyChargeNumber, "Penalty charge number")
            .field(TecCase::getLocalAuthority, "Local authority");

        builder.searchResultFields()
            .caseReferenceField()
            .field("[STATE]", "State")
            .field(TecCase::getPenaltyChargeNumber, "Penalty charge number")
            .field(TecCase::getLocalAuthority, "Local authority")
            .field(TecCase::getRespondentDetails1, "Respondent details 1")
            .field(TecCase::getRespondentDetails2, "Respondent details 2")
            .field(TecCase::getRespondentDetails3, "Respondent details 3")
            .field(TecCase::getVehicleRegistrationNumber, "Vehicle registration number");

        builder.workBasketInputFields()
            .field(TecCase::getPenaltyChargeNumber, "Penalty charge number")
            .field(TecCase::getLocalAuthority, "Local authority");

        builder.workBasketResultFields()
            .caseReferenceField()
            .field(TecCase::getPenaltyChargeNumber, "Penalty charge number")
            .field(TecCase::getLocalAuthority, "Local authority")
            .field(TecCase::getRespondentDetails1, "Respondent details 1")
            .field(TecCase::getVehicleRegistrationNumber, "Vehicle registration number")
            .field("[STATE]", "State");
    }

    private void configureEvents(DecentralisedConfigBuilder<TecCase, CaseState, UserRole> builder) {
        builder.decentralisedEvent("createTecCase", this::createTecCase)
            .initialState(CaseState.PENDING_CASE_ISSUED)
            .name("PCN case created from datafile")
            .showCondition(NEVER_SHOW)
            .grant(Permission.CRUD, UserRole.SYSTEM)
            .grant(Permission.R, UserRole.CLERK)
            .grant(Permission.R, UserRole.LOCAL_AUTHORITY)
            .fields()
            .mandatory(TecCase::getFileIdentifier)
            .mandatory(TecCase::getBatchIdentifier)
            .mandatory(TecCase::getPenaltyChargeNumber)
            .mandatory(TecCase::getLocalAuthority)
            .mandatory(TecCase::getRespondentDetails1)
            .mandatory(TecCase::getRespondentDetails2)
            .mandatory(TecCase::getRespondentDetails3)
            .optional(TecCase::getRespondentDetails4)
            .optional(TecCase::getRespondentDetails5)
            .optional(TecCase::getRespondentDetails6)
            .mandatory(TecCase::getVehicleRegistrationNumber)
            .mandatory(TecCase::getNatureOfOffence)
            .mandatory(TecCase::getDateChargeCertificateServed)
            .mandatory(TecCase::getAmountDue)
            .optional(TecCase::getCaseAccessCategory, NEVER_SHOW);

        builder.decentralisedEvent("registrationPaymentSucceeded", this::registrationPaymentSucceeded)
            .forStateTransition(
                CaseState.PENDING_CASE_ISSUED,
                CaseState.CASE_ISSUED
            )
            .name("Registration payment succeeded")
            .showCondition(NEVER_SHOW)
            .grant(Permission.CRUD, UserRole.SYSTEM)
            .fields()
            .mandatory(TecCase::getPaymentReference);

        builder.decentralisedEvent(
                "registrationAuthorised",
                this::registrationAuthorised
            )
            .forStateTransition(CaseState.CASE_ISSUED, CaseState.AWAITING_RESPONDENT_RESPONSE)
            .name("Registration authorised")
            .showCondition(NEVER_SHOW)
            .grant(Permission.CRUD, UserRole.SYSTEM)
            .fields()
            .mandatory(TecCase::getRegistrationDocument);

        builder.decentralisedEvent("verifyFormValidation", this::verifyFormValidation)
            .forStates(CaseState.PENDING_CASE_ISSUED, CaseState.CASE_ISSUED)
            .name("Validate OOT application")
            .grant(Permission.CRU, UserRole.CLERK)
            .fields()
            .mandatory(TecCase::getFormValidationResult)
            .optional(TecCase::getFormValidationComment);

        builder.decentralisedEvent("editTe9Application", this::editTe9Application)
            .forStates(CaseState.values())
            .name("Edit TE9 application")
            .description("Update TE9 application details")
            .showCondition("applicationForm=\"TE9\"")
            .grant(Permission.CRU, UserRole.CLERK)
            .fields()
            .optional(TecCase::getApplicationDateReceived)
            .optional(TecCase::getApplicationType)
            .optional(TecCase::getApplicationTe7Submitted, "applicationType=\"outOfTime\"")
            .optional(TecCase::getApplicationVehicleRegistration)
            .optional(TecCase::getApplicationApplicant)
            .optional(TecCase::getApplicationLocationOfContravention)
            .optional(TecCase::getApplicationDateOfContravention)
            .optional(TecCase::getApplicationTitle)
            .optional(TecCase::getApplicationFullName)
            .optional(TecCase::getApplicationCompanyName)
            .optional(TecCase::getApplicationAddress)
            .optional(TecCase::getApplicationPostcode)
            .optional(TecCase::getApplicationDeclaration)
            .optional(TecCase::getApplicationDatePaid, "applicationDeclaration=\"paidInFull\"")
            .optional(TecCase::getApplicationHowPaid, "applicationDeclaration=\"paidInFull\"")
            .optional(TecCase::getApplicationPaidTo, "applicationDeclaration=\"paidInFull\"");

        builder.decentralisedEvent("editPe3Application", this::editPe3Application)
            .forStates(CaseState.values())
            .name("Edit PE3 application")
            .description("Update PE3 application details")
            .showCondition("applicationForm=\"PE3\"")
            .grant(Permission.CRU, UserRole.CLERK)
            .fields()
            .optional(TecCase::getApplicationDateReceived)
            .optional(TecCase::getApplicationType)
            .optional(TecCase::getApplicationTe7Submitted, "applicationType=\"outOfTime\"")
            .optional(TecCase::getApplicationVehicleRegistration)
            .optional(TecCase::getApplicationApplicant)
            .optional(TecCase::getApplicationLocationOfContravention)
            .optional(TecCase::getApplicationDateOfContravention)
            .optional(TecCase::getApplicationTitle)
            .optional(TecCase::getApplicationFullName)
            .optional(TecCase::getApplicationCompanyName)
            .optional(TecCase::getApplicationAddress)
            .optional(TecCase::getApplicationPostcode)
            .optional(TecCase::getApplicationDeclaration)
            .optional(TecCase::getApplicationReasonsGiven)
            .optional(TecCase::getApplicationDatePaid, "applicationDeclaration=\"paidInFull\"")
            .optional(TecCase::getApplicationHowPaid, "applicationDeclaration=\"paidInFull\"")
            .optional(TecCase::getApplicationPaidTo, "applicationDeclaration=\"paidInFull\"");

        builder.decentralisedEvent("editTe7Application", this::editTe7Application)
            .forStates(CaseState.values())
            .name("Edit TE7 application")
            .description("Update TE7 time-extension details")
            .showCondition("timeExtensionForm=\"TE7\"")
            .grant(Permission.CRU, UserRole.CLERK)
            .fields()
            .optional(TecCase::getTimeExtensionVehicleRegistration)
            .optional(TecCase::getTimeExtensionTitle)
            .optional(TecCase::getTimeExtensionOtherTitle, "timeExtensionTitle=\"Other\"")
            .optional(TecCase::getTimeExtensionFullName)
            .optional(TecCase::getTimeExtensionCompanyName)
            .optional(TecCase::getTimeExtensionAddress)
            .optional(TecCase::getTimeExtensionPostcode)
            .optional(TecCase::getTimeExtensionPermissionType)
            .optional(TecCase::getTimeExtensionReasonsGiven)
            .optional(TecCase::getTimeExtensionSignedAndDated)
            .optional(TecCase::getTimeExtensionSignedBy)
            .optional(TecCase::getTimeExtensionDateSigned)
            .optional(TecCase::getTimeExtensionPrintFullName);

        builder.decentralisedEvent("editPe2Application", this::editPe2Application)
            .forStates(CaseState.values())
            .name("Edit PE2 application")
            .description("Update PE2 time-extension details")
            .showCondition("timeExtensionForm=\"PE2\"")
            .grant(Permission.CRU, UserRole.CLERK)
            .fields()
            .optional(TecCase::getTimeExtensionVehicleRegistration)
            .optional(TecCase::getTimeExtensionApplicant)
            .optional(TecCase::getTimeExtensionLocationOfContravention)
            .optional(TecCase::getTimeExtensionDateOfContravention)
            .optional(TecCase::getTimeExtensionFullName)
            .optional(TecCase::getTimeExtensionAddress)
            .optional(TecCase::getTimeExtensionPostcode)
            .optional(TecCase::getTimeExtensionReasonsGiven)
            .optional(TecCase::getTimeExtensionSignedAndDated)
            .optional(TecCase::getTimeExtensionDateSigned);

        builder.decentralisedEvent("attachCaseFileDocument", this::attachCaseFileDocument)
            .forStates(CaseState.values())
            .name("Attach case file document")
            .showCondition(NEVER_SHOW)
            .grant(Permission.CRUD, UserRole.SYSTEM)
            .fields()
            .mandatory(TecCase::getCaseFileDocument);

        builder.decentralisedEvent("linkBatchCase", this::linkBatchCase)
            .forStates(CaseState.values())
            .name("Link batch case")
            .showCondition(NEVER_SHOW)
            .grant(Permission.CRUD, UserRole.SYSTEM)
            .grant(Permission.R, UserRole.CLERK)
            .fields()
            .mandatory(TecCase::getBatchLinkCase)
            .mandatory(TecCase::getBatchLinkType);

        builder.decentralisedEvent("recordApplication", this::recordApplication)
            .forStates(CaseState.values())
            .name("Record application")
            .showCondition(NEVER_SHOW)
            .grant(Permission.CRUD, UserRole.SYSTEM)
            .fields()
            .optional(TecCase::getApplicationDateReceived)
            .optional(TecCase::getApplicationType)
            .optional(TecCase::getApplicationTe7Submitted)
            .optional(TecCase::getApplicationForm)
            .optional(TecCase::getApplicationPenaltyChargeNumber)
            .optional(TecCase::getApplicationVehicleRegistration)
            .optional(TecCase::getApplicationApplicant)
            .optional(TecCase::getApplicationLocationOfContravention)
            .optional(TecCase::getApplicationDateOfContravention)
            .optional(TecCase::getApplicationTitle)
            .optional(TecCase::getApplicationFullName)
            .optional(TecCase::getApplicationCompanyName)
            .optional(TecCase::getApplicationAddress)
            .optional(TecCase::getApplicationPostcode)
            .optional(TecCase::getApplicationDeclaration)
            .optional(TecCase::getApplicationReasonsGiven)
            .optional(TecCase::getApplicationDatePaid)
            .optional(TecCase::getApplicationHowPaid)
            .optional(TecCase::getApplicationPaidTo);

        builder.decentralisedEvent("recordTimeExtension", this::recordTimeExtension)
            .forStates(CaseState.values())
            .name("Record time extension")
            .showCondition(NEVER_SHOW)
            .grant(Permission.CRUD, UserRole.SYSTEM)
            .fields()
            .optional(TecCase::getFormValidationResult)
            .optional(TecCase::getTimeExtensionForm)
            .optional(TecCase::getTimeExtensionPenaltyChargeNumber)
            .optional(TecCase::getTimeExtensionVehicleRegistration)
            .optional(TecCase::getTimeExtensionApplicant)
            .optional(TecCase::getTimeExtensionLocationOfContravention)
            .optional(TecCase::getTimeExtensionDateOfContravention)
            .optional(TecCase::getTimeExtensionTitle)
            .optional(TecCase::getTimeExtensionOtherTitle)
            .optional(TecCase::getTimeExtensionFullName)
            .optional(TecCase::getTimeExtensionCompanyName)
            .optional(TecCase::getTimeExtensionAddress)
            .optional(TecCase::getTimeExtensionPostcode)
            .optional(TecCase::getTimeExtensionPermissionType)
            .optional(TecCase::getTimeExtensionReasonsGiven)
            .optional(TecCase::getTimeExtensionSignedAndDated)
            .optional(TecCase::getTimeExtensionSignedBy)
            .optional(TecCase::getTimeExtensionDateSigned)
            .optional(TecCase::getTimeExtensionPrintFullName);

        builder.decentralisedEvent("applyWarrantAuthorisation", this::applyWarrantAuthorisation)
            .forStates(CaseState.values())
            .name("Apply warrant authorisation")
            .showCondition(NEVER_SHOW)
            .grant(Permission.CRUD, UserRole.SYSTEM)
            .grant(Permission.R, UserRole.CLERK)
            .fields()
            .mandatory(TecCase::getWarrantAuthorisation);

        builder.decentralisedEvent("setCaseState", this::setCaseState)
            .forStates(CaseState.values())
            .name("Set case state")
            .showCondition(NEVER_SHOW)
            .grant(Permission.CRUD, UserRole.SYSTEM)
            .fields()
            .mandatory(TecCase::getTargetCaseState);
    }

    private SubmitResponse<CaseState> createTecCase(EventPayload<TecCase, CaseState> event) {
        repository.create(event.caseReference(), event.caseData());
        return response(CaseState.PENDING_CASE_ISSUED);
    }

    private SubmitResponse<CaseState> registrationPaymentSucceeded(EventPayload<TecCase, CaseState> event) {
        repository.recordPayment(event.caseReference(), "SUCCEEDED", event.caseData().getPaymentReference(), null);
        return response(CaseState.CASE_ISSUED);
    }

    private SubmitResponse<CaseState> registrationAuthorised(EventPayload<TecCase, CaseState> event) {
        repository.recordRegistration(
            event.caseReference(),
            event.caseData().getRegistrationDocument(),
            LocalDate.now()
        );
        return response(CaseState.AWAITING_RESPONDENT_RESPONSE);
    }

    private SubmitResponse<CaseState> verifyFormValidation(EventPayload<TecCase, CaseState> event) {
        repository.recordFormValidation(
            event.caseReference(),
            event.caseData().getFormValidationResult()
        );
        String comment = event.caseData().getFormValidationComment();
        if (comment == null || comment.isBlank()) {
            return SubmitResponse.defaultResponse();
        }
        return SubmitResponse.<CaseState>builder()
            .eventMetadata(EventMetadata.builder().description(comment.trim()).build())
            .build();
    }

    private SubmitResponse<CaseState> attachCaseFileDocument(EventPayload<TecCase, CaseState> event) {
        Document document = event.caseData().getCaseFileDocument();
        if (document == null) {
            throw new IllegalArgumentException("caseFileDocument is required");
        }
        if (isBlank(document.getUrl())
            || isBlank(document.getBinaryUrl())
            || isBlank(document.getFilename())) {
            throw new IllegalArgumentException(
                "caseFileDocument requires document_url, document_binary_url and document_filename"
            );
        }

        String categoryId = CaseFileCategory.normalisedCategoryId(document.getCategoryId());
        repository.insertDocument(
            event.caseReference(),
            categoryId,
            CdamDocumentUrls.toCdamUrl(document.getUrl()),
            CdamDocumentUrls.toCdamUrl(document.getBinaryUrl()),
            document.getFilename()
        );
        return SubmitResponse.defaultResponse();
    }

    private SubmitResponse<CaseState> linkBatchCase(EventPayload<TecCase, CaseState> event) {
        CaseLink batchLinkCase = event.caseData().getBatchLinkCase();
        if (batchLinkCase == null || isBlank(batchLinkCase.getCaseReference())) {
            throw new IllegalArgumentException("batchLinkCase.CaseReference is required");
        }
        BatchOperation batchLinkType = event.caseData().getBatchLinkType();
        if (batchLinkType == null) {
            throw new IllegalArgumentException("batchLinkType is required");
        }
        long batchCaseReference = parseCaseReference(batchLinkCase.getCaseReference());
        if (!batchCaseRepository.exists(batchCaseReference)) {
            throw new IllegalArgumentException(
                "No TEC_BATCH case found for reference " + batchCaseReference
            );
        }
        BatchCase batch = batchCaseRepository.find(batchCaseReference);
        if (batch.getOperation() != batchLinkType) {
            throw new IllegalArgumentException(
                "batchLinkType " + batchLinkType
                    + " does not match batch operation " + batch.getOperation()
            );
        }
        if (batchLinkType == BatchOperation.REGISTRATION) {
            Long existingBatch = repository.findBatchCaseReference(event.caseReference());
            if (existingBatch != null && existingBatch.longValue() != batchCaseReference) {
                throw new IllegalArgumentException(
                    "PCN case " + event.caseReference()
                        + " is already linked to batch case " + existingBatch
                );
            }
            repository.linkBatchCase(event.caseReference(), batchCaseReference);
        } else {
            batchCaseRepository.linkPcnCase(batchCaseReference, event.caseReference());
        }
        return switch (batchLinkType) {
            case TRANSFER_REQUEST -> response(CaseState.REFER_FOR_ENFORCEMENT);
            case CASE_CLOSURE_REQUESTS -> response(CaseState.CLOSED);
            default -> SubmitResponse.defaultResponse();
        };
    }

    private SubmitResponse<CaseState> recordApplication(EventPayload<TecCase, CaseState> event) {
        repository.recordApplication(event.caseReference(), event.caseData());
        return SubmitResponse.defaultResponse();
    }

    private SubmitResponse<CaseState> editTe9Application(EventPayload<TecCase, CaseState> event) {
        repository.editTe9Application(event.caseReference(), event.caseData());
        return SubmitResponse.defaultResponse();
    }

    private SubmitResponse<CaseState> editPe3Application(EventPayload<TecCase, CaseState> event) {
        repository.editPe3Application(event.caseReference(), event.caseData());
        return SubmitResponse.defaultResponse();
    }

    private SubmitResponse<CaseState> recordTimeExtension(EventPayload<TecCase, CaseState> event) {
        repository.recordTimeExtension(event.caseReference(), event.caseData());
        return SubmitResponse.defaultResponse();
    }

    private SubmitResponse<CaseState> editTe7Application(EventPayload<TecCase, CaseState> event) {
        repository.editTe7Application(event.caseReference(), event.caseData());
        return SubmitResponse.defaultResponse();
    }

    private SubmitResponse<CaseState> editPe2Application(EventPayload<TecCase, CaseState> event) {
        repository.editPe2Application(event.caseReference(), event.caseData());
        return SubmitResponse.defaultResponse();
    }

    private SubmitResponse<CaseState> applyWarrantAuthorisation(EventPayload<TecCase, CaseState> event) {
        WarrantAuthorisation authorisation = event.caseData().getWarrantAuthorisation();
        if (authorisation == null) {
            throw new IllegalArgumentException("warrantAuthorisation is required");
        }
        if (authorisation.getDateOfIssue() == null) {
            throw new IllegalArgumentException("warrantAuthorisation.dateOfIssue is required");
        }
        if (authorisation.getDateOfExpiry() == null) {
            throw new IllegalArgumentException("warrantAuthorisation.dateOfExpiry is required");
        }
        if (authorisation.getStatus() == null) {
            throw new IllegalArgumentException("warrantAuthorisation.status is required");
        }
        repository.insertWarrantAuthorisation(event.caseReference(), authorisation);
        return switch (authorisation.getStatus()) {
            case ACTIVE -> response(CaseState.WARRANT_AUTHORISATION_ISSUED);
            case EXPIRED -> response(CaseState.WARRANT_AUTHORISATION_EXPIRED);
            case CANCELLED -> SubmitResponse.defaultResponse();
        };
    }

    private SubmitResponse<CaseState> setCaseState(EventPayload<TecCase, CaseState> event) {
        String raw = event.caseData().getTargetCaseState();
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("targetCaseState is required");
        }
        CaseState target;
        try {
            target = CaseState.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                "Unknown targetCaseState '" + raw + "'. Use one of: "
                    + Arrays.toString(CaseState.values()),
                ex
            );
        }
        return response(target);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static long parseCaseReference(String value) {
        String digits = value == null ? "" : value.replace("-", "").trim();
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException(
                "Case reference must contain digits (hyphens optional): '" + value + "'"
            );
        }
        return Long.parseLong(digits);
    }

    private SubmitResponse<CaseState> response(CaseState state) {
        return SubmitResponse.<CaseState>builder().state(state).build();
    }
}
