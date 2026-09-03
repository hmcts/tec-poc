package uk.gov.hmcts.reform.tecpoc.ccd;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.sdk.api.CCDConfig;
import uk.gov.hmcts.ccd.sdk.api.DecentralisedConfigBuilder;
import uk.gov.hmcts.ccd.sdk.api.EventPayload;
import uk.gov.hmcts.ccd.sdk.api.Permission;
import uk.gov.hmcts.ccd.sdk.api.callback.SubmitResponse;
import uk.gov.hmcts.ccd.sdk.type.Document;

import java.time.LocalDate;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TecCaseConfiguration implements CCDConfig<TecCase, CaseState, UserRole> {

    public static final String CASE_TYPE = "TEC";
    private static final String NEVER_SHOW = "[STATE]=\"NEVER_SHOW\"";

    private final TecCaseRepository repository;

    @Override
    public void configureDecentralised(DecentralisedConfigBuilder<TecCase, CaseState, UserRole> builder) {
        builder.caseType(CASE_TYPE, "TEC case", "A TEC PCN case");
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
            builder.categories(UserRole.CLERK)
                .categoryID(category.getId())
                .categoryLabel(category.getLabel())
                .displayOrder(category.getDisplayOrder());
        }
    }

    private void configureAccessProfiles(DecentralisedConfigBuilder<TecCase, CaseState, UserRole> builder) {
        for (UserRole role : UserRole.values()) {
            builder.caseRoleToAccessProfile(role)
                .accessProfiles(role.getRole())
                .legacyIdamRole();
        }
    }

    private void configureStateAccess(DecentralisedConfigBuilder<TecCase, CaseState, UserRole> builder) {
        for (CaseState state : CaseState.values()) {
            builder.grant(state, Permission.CRUD, UserRole.SYSTEM);
            builder.grant(state, Set.of(Permission.R, Permission.U), UserRole.CLERK);
        }
    }

    private void configureCaseView(DecentralisedConfigBuilder<TecCase, CaseState, UserRole> builder) {
        builder.tab("tasks", "Tasks")
            .label("tasksMarkdownLabel", null, "${tasksMarkdown}")
            .field("tasksMarkdown", NEVER_SHOW);

        builder.tab("caseDetails", "Case details")
            .label("registrationSection", null, "## Registration")
            .field(TecCase::getFileIdentifier)
            .field(TecCase::getBatchIdentifier)
            .field(TecCase::getPenaltyChargeNumber)
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
            .field(TecCase::getFormValidationResultDisplay);

        builder.tab("caseFileView", "Case File View")
            .field(TecCase::getCaseFileView, null, "#ARGUMENT(CaseFileView)")
            .field(TecCase::getAllDocuments, NEVER_SHOW);

        builder.searchInputFields()
            .field(TecCase::getPenaltyChargeNumber, "Penalty charge number");

        builder.searchResultFields()
            .caseReferenceField()
            .field(TecCase::getPenaltyChargeNumber, "Penalty charge number")
            .field(TecCase::getRespondentDetails1, "Respondent details 1")
            .field(TecCase::getRespondentDetails2, "Respondent details 2")
            .field(TecCase::getRespondentDetails3, "Respondent details 3")
            .field(TecCase::getVehicleRegistrationNumber, "Vehicle registration number");

        builder.workBasketInputFields()
            .field(TecCase::getPenaltyChargeNumber, "Penalty charge number");

        builder.workBasketResultFields()
            .caseReferenceField()
            .field(TecCase::getPenaltyChargeNumber, "Penalty charge number")
            .field(TecCase::getRespondentDetails1, "Respondent details 1")
            .field(TecCase::getRespondentDetails2, "Respondent details 2")
            .field(TecCase::getRespondentDetails3, "Respondent details 3")
            .field(TecCase::getVehicleRegistrationNumber, "Vehicle registration number");
    }

    private void configureEvents(DecentralisedConfigBuilder<TecCase, CaseState, UserRole> builder) {
        builder.decentralisedEvent("createTecCase", this::createTecCase)
            .initialState(CaseState.PENDING_CASE_ISSUED)
            .name("PCN case created from datafile")
            .showCondition(NEVER_SHOW)
            .grant(Permission.CRUD, UserRole.SYSTEM)
            .grant(Permission.R, UserRole.CLERK)
            .fields()
            .mandatory(TecCase::getFileIdentifier)
            .mandatory(TecCase::getBatchIdentifier)
            .mandatory(TecCase::getPenaltyChargeNumber)
            .mandatory(TecCase::getRespondentDetails1)
            .mandatory(TecCase::getRespondentDetails2)
            .mandatory(TecCase::getRespondentDetails3)
            .optional(TecCase::getRespondentDetails4)
            .optional(TecCase::getRespondentDetails5)
            .optional(TecCase::getRespondentDetails6)
            .mandatory(TecCase::getVehicleRegistrationNumber)
            .mandatory(TecCase::getNatureOfOffence)
            .mandatory(TecCase::getDateChargeCertificateServed)
            .mandatory(TecCase::getAmountDue);

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
            .name("Verify form validation")
            .grant(Permission.CRU, UserRole.CLERK)
            .fields()
            .mandatory(TecCase::getFormValidationResult);

        builder.decentralisedEvent("attachCaseFileDocument", this::attachCaseFileDocument)
            .forStates(CaseState.values())
            .name("Attach case file document")
            .showCondition(NEVER_SHOW)
            .grant(Permission.CRUD, UserRole.SYSTEM)
            .fields()
            .mandatory(TecCase::getCaseFileDocument);
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
        return SubmitResponse.defaultResponse();
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
            document.getUrl(),
            document.getBinaryUrl(),
            document.getFilename()
        );
        return SubmitResponse.defaultResponse();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private SubmitResponse<CaseState> response(CaseState state) {
        return SubmitResponse.<CaseState>builder().state(state).build();
    }
}
