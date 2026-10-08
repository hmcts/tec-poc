package uk.gov.hmcts.reform.tecpoc.ccd;

import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.sdk.CaseView;
import uk.gov.hmcts.ccd.sdk.CaseViewRequest;
import uk.gov.hmcts.ccd.sdk.api.CCD;
import uk.gov.hmcts.ccd.sdk.type.CaseLink;
import uk.gov.hmcts.ccd.sdk.type.Document;
import uk.gov.hmcts.ccd.sdk.type.ListValue;

@Component
@RequiredArgsConstructor
public class TecCaseView implements CaseView<TecCase, CaseState> {

    private final TecCaseRepository repository;

    static final String NO_PREVIOUS_REGISTRATIONS =
        "<p class=\"govuk-body\">There are no previous registrations.</p>";

    private static final Comparator<TecCaseRegistration> BY_SUFFIX_THEN_TIME =
        Comparator.comparingInt(TecCaseRegistration::suffix)
            .thenComparing(TecCaseRegistration::createdAt)
            .thenComparing(TecCaseRegistration::id);

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
        List<TecCaseRegistration> previousRegistrations = excludingCurrent(
            repository.findRegistrations(request.caseRef())
        );
        tecCase.setPreviousRegistrations(
            previousRegistrations.isEmpty() ? null : toPreviousRegistrations(previousRegistrations)
        );
        tecCase.setPreviousRegistrationsMarkdown(NO_PREVIOUS_REGISTRATIONS);
        if (tecCase.getTe9Details() != null) {
            defaultFormValidationDisplay(
                tecCase.getTe9Details().getFormValidationResultDisplay(),
                tecCase.getTe9Details()::setFormValidationResultDisplay
            );
        }
        if (tecCase.getPe3Details() != null) {
            defaultFormValidationDisplay(
                tecCase.getPe3Details().getFormValidationResultDisplay(),
                tecCase.getPe3Details()::setFormValidationResultDisplay
            );
        }
        if (tecCase.getTe7Details() != null) {
            defaultFormValidationDisplay(
                tecCase.getTe7Details().getFormValidationResultDisplay(),
                tecCase.getTe7Details()::setFormValidationResultDisplay
            );
        }
        if (tecCase.getPe2Details() != null) {
            defaultFormValidationDisplay(
                tecCase.getPe2Details().getFormValidationResultDisplay(),
                tecCase.getPe2Details()::setFormValidationResultDisplay
            );
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

    private static void defaultFormValidationDisplay(String current, Consumer<String> setter) {
        if (current == null || current.isBlank()) {
            setter.accept(TecCaseRepository.DEFAULT_FORM_VALIDATION_RESULT);
        }
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

    /**
     * Registrations other than the current one (highest suffix, then latest created time),
     * ordered from the lowest suffix to the highest.
     */
    static List<TecCaseRegistration> excludingCurrent(List<TecCaseRegistration> registrations) {
        if (registrations.size() < 2) {
            return List.of();
        }
        TecCaseRegistration current = registrations.stream().max(BY_SUFFIX_THEN_TIME).orElseThrow();
        return registrations.stream()
            .filter(registration -> !registration.id().equals(current.id()))
            .sorted(BY_SUFFIX_THEN_TIME)
            .toList();
    }

    static List<ListValue<PreviousRegistration>> toPreviousRegistrations(
        List<TecCaseRegistration> registrations
    ) {
        return registrations.stream()
            .map(TecCaseView::toPreviousRegistrationListValue)
            .toList();
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

    private static ListValue<PreviousRegistration> toPreviousRegistrationListValue(
        TecCaseRegistration registration
    ) {
        PreviousRegistration value = new PreviousRegistration();
        value.setFileIdentifier(registration.fileIdentifier());
        value.setBatchIdentifier(registration.batchIdentifier());
        if (registration.batchCaseReference() != null) {
            value.setBatchCase(CaseLink.builder()
                .caseReference(Long.toString(registration.batchCaseReference()))
                .caseType(BatchCaseConfiguration.CASE_TYPE)
                .build());
        }
        value.setPenaltyChargeNumber(registration.penaltyChargeNumber());
        value.setLocalAuthority(registration.localAuthority());
        value.setRespondentDetails1(registration.respondentDetails1());
        value.setRespondentDetails2(registration.respondentDetails2());
        value.setRespondentDetails3(registration.respondentDetails3());
        value.setRespondentDetails4(registration.respondentDetails4());
        value.setRespondentDetails5(registration.respondentDetails5());
        value.setRespondentDetails6(registration.respondentDetails6());
        value.setVehicleRegistrationNumber(registration.vehicleRegistrationNumber());
        value.setNatureOfOffence(registration.natureOfOffence());
        value.setDateChargeCertificateServed(registration.dateChargeCertificateServed());
        value.setAmountDue(registration.amountDue());
        value.setPaymentStatus(registration.paymentStatus());
        value.setPaymentReference(registration.paymentReference());
        value.setClosureReason(registration.closureReason());
        value.setRegistrationDocument(registration.registrationDocument());
        value.setRegistrationDate(registration.registrationDate());
        return ListValue.<PreviousRegistration>builder()
            .id(registration.id().toString())
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
