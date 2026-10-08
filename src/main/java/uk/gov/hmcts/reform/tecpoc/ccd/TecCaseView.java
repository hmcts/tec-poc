package uk.gov.hmcts.reform.tecpoc.ccd;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.sdk.CaseView;
import uk.gov.hmcts.ccd.sdk.CaseViewRequest;
import uk.gov.hmcts.ccd.sdk.api.CCD;
import uk.gov.hmcts.ccd.sdk.type.Document;
import uk.gov.hmcts.ccd.sdk.type.ListValue;

@Component
@RequiredArgsConstructor
public class TecCaseView implements CaseView<TecCase, CaseState> {

    private final TecCaseRepository repository;

    private static final String FORM_VALIDATION_NOT_RECORDED = "Not validated";

    @Override
    public Set<String> caseTypeIds() {
        return Set.of(TecCaseConfiguration.CASE_TYPE);
    }

    @Override
    public TecCase getCase(CaseViewRequest<CaseState> request) {
        TecCase tecCase = repository.find(request.caseRef());
        // Event-only fields — never surface on Case details / case_link sync from CaseView.
        tecCase.setBatchLinkCase(null);
        tecCase.setBatchLinkType(null);
        tecCase.setStatusDisplay(stateLabel(request.state()));
        tecCase.setOotRefusalReviewDecision(null);
        tecCase.setOotRejectionEmail(null);
        if (request.state() == CaseState.PENDING_REFUSAL_DECISION) {
            tecCase.setOotApplicationDecisionDisplay("Refused");
        }
        tecCase.setTasksMarkdown(
            TecPrototypeTasks.markdownFor(request.caseRef(), request.state(), tecCase)
        );
        tecCase.setRolesAndAccessMarkdown(
            "<p class=\"govuk-body\">Roles and access (CCD shell). "
                + "The Manage Case Work Allocation tab is not wired for TEC in this PoC.</p>"
        );
        // Leave flagLauncher and caseFlags unset. The data store validates the whole case
        // on submit and has no validator for FlagLauncher.
        tecCase.setParties(List.of());
        tecCase.setPreviousRegistrationsMarkdown(
            "<p class=\"govuk-body\">Previous registrations will be shown here.</p>"
        );
        FormValidationResult validationResult = tecCase.getFormValidationResult();
        String validationDisplay = validationResult == null
            ? FORM_VALIDATION_NOT_RECORDED
            : validationResult.getLabel();
        if (tecCase.getTe9Details() != null) {
            tecCase.getTe9Details().setFormValidationResultDisplay(validationDisplay);
        }
        if (tecCase.getPe3Details() != null) {
            tecCase.getPe3Details().setFormValidationResultDisplay(validationDisplay);
        }
        if (tecCase.getTe7Details() != null) {
            tecCase.getTe7Details().setFormValidationResultDisplay(validationDisplay);
        }
        if (tecCase.getPe2Details() != null) {
            tecCase.getPe2Details().setFormValidationResultDisplay(validationDisplay);
        }
        if (tecCase.getLocalAuthority() != null) {
            tecCase.setCaseAccessCategory(tecCase.getLocalAuthority().getCode());
        }
        // Batch links are owned by the batch case (PCN appears under ExUI "linked from"
        // via CCD case_link / getLinkedCases). PCN caseLinks stay empty.
        tecCase.setCaseLinks(List.of());
        tecCase.setGeneralApplication(null);
        tecCase.setWarrantAuthorisations(
            toWarrantAuthorisations(repository.findWarrantAuthorisations(request.caseRef()))
        );
        tecCase.setGeneralApplications(
            toGeneralApplications(repository.findGeneralApplications(request.caseRef()))
        );
        tecCase.setAllDocuments(toAllDocuments(repository.findDocuments(request.caseRef())));
        return tecCase;
    }

    static String stateLabel(CaseState state) {
        return ccdLabel(state);
    }

    private static String ccdLabel(Enum<?> state) {
        try {
            CCD ccd = state.getClass().getField(state.name()).getAnnotation(CCD.class);
            if (ccd != null && ccd.label() != null && !ccd.label().isBlank()) {
                return ccd.label();
            }
        } catch (NoSuchFieldException ignored) {
            // fall through
        }
        return state.name();
    }

    static List<ListValue<Document>> toAllDocuments(List<TecCaseDocument> documents) {
        return documents.stream()
            .map(TecCaseView::toListValue)
            .toList();
    }

    static List<ListValue<GeneralApplication>> toGeneralApplications(
        List<TecCaseGeneralApplication> applications
    ) {
        return applications.stream()
            .map(TecCaseView::toGeneralApplicationListValue)
            .toList();
    }

    static List<ListValue<WarrantAuthorisation>> toWarrantAuthorisations(
        List<TecCaseWarrantAuthorisation> authorisations
    ) {
        return authorisations.stream()
            .map(TecCaseView::toWarrantAuthorisationListValue)
            .toList();
    }

    private static ListValue<GeneralApplication> toGeneralApplicationListValue(
        TecCaseGeneralApplication application
    ) {
        GeneralApplication value = new GeneralApplication();
        value.setRank(application.rank());
        value.setApplicant(application.applicant());
        value.setDateReceived(application.dateReceived());
        value.setApplicationType(application.applicationType());
        value.setSomethingElseDetails(application.somethingElseDetails());
        value.setWithin14Days(application.within14Days());
        value.setFeeAmountReceived(application.feeAmountReceived());
        value.setAppliedForHwf(application.appliedForHwf());
        value.setHwfReference(application.hwfReference());
        value.setAllPartiesAgree(application.allPartiesAgree());
        value.setWithoutNotice(application.withoutNotice());
        value.setState(application.state());
        return ListValue.<GeneralApplication>builder()
            .id(application.id().toString())
            .value(value)
            .build();
    }

    private static ListValue<WarrantAuthorisation> toWarrantAuthorisationListValue(
        TecCaseWarrantAuthorisation authorisation
    ) {
        WarrantAuthorisation value = new WarrantAuthorisation();
        value.setDateOfIssue(authorisation.dateOfIssue());
        value.setDateOfExpiry(authorisation.dateOfExpiry());
        value.setStatus(authorisation.status());
        return ListValue.<WarrantAuthorisation>builder()
            .id(authorisation.id().toString())
            .value(value)
            .build();
    }

    private static ListValue<Document> toListValue(TecCaseDocument document) {
        Document ccdDocument = Document.builder()
            .url(CdamDocumentUrls.toCdamUrl(document.documentUrl()))
            .binaryUrl(CdamDocumentUrls.toCdamUrl(document.documentBinaryUrl()))
            .filename(document.filename())
            .categoryId(document.categoryId())
            .uploadTimestamp(
                document.createdAt() == null
                    ? null
                    : document.createdAt().atZone(ZoneOffset.UTC).toLocalDateTime()
            )
            .build();
        return ListValue.<Document>builder()
            .id(document.id().toString())
            .value(ccdDocument)
            .build();
    }
}
