package uk.gov.hmcts.reform.tecpoc.ccd;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.sdk.api.CCDConfig;
import uk.gov.hmcts.ccd.sdk.api.CaseDetails;
import uk.gov.hmcts.ccd.sdk.api.DecentralisedConfigBuilder;
import uk.gov.hmcts.ccd.sdk.api.EventPayload;
import uk.gov.hmcts.ccd.sdk.api.Permission;
import uk.gov.hmcts.ccd.sdk.api.callback.AboutToStartOrSubmitResponse;
import uk.gov.hmcts.ccd.sdk.api.callback.SubmitResponse;
import uk.gov.hmcts.ccd.sdk.type.CaseLink;
import uk.gov.hmcts.ccd.sdk.type.Document;
import uk.gov.hmcts.ccd.sdk.type.ListValue;

import java.util.List;
import java.util.Set;

@Component
public class BatchCaseConfiguration implements CCDConfig<BatchCase, BatchCaseState, UserRole> {

    public static final String CASE_TYPE = "TEC_BATCH";
    public static final String UPLOAD_BATCH_EVENT_ID = "uploadBatch";
    private static final String NEVER_SHOW = "[STATE]=\"NEVER_SHOW\"";

    private final BatchCaseRepository repository;
    private final TecCaseRepository tecCaseRepository;

    public BatchCaseConfiguration(
        @Lazy BatchCaseRepository repository,
        @Lazy TecCaseRepository tecCaseRepository
    ) {
        this.repository = repository;
        this.tecCaseRepository = tecCaseRepository;
    }

    @Override
    public void configureDecentralised(DecentralisedConfigBuilder<BatchCase, BatchCaseState, UserRole> builder) {
        builder.caseType(CASE_TYPE, "TEC Batch", "A TEC Batch case");
        builder.jurisdiction("TEC", "Traffic Enforcement Centre", "Traffic Enforcement Centre");
        builder.hmctsServiceId("TEC1");
        builder.setCallbackHost(System.getenv().getOrDefault("API_URL", "http://localhost:4013"));

        configureAccessProfiles(builder);
        configureStateAccess(builder);
        configureCaseView(builder);
        configureCaseFileCategories(builder);
        configureEvents(builder);
    }

    private void configureCaseFileCategories(
        DecentralisedConfigBuilder<BatchCase, BatchCaseState, UserRole> builder
    ) {
        for (BatchFileCategory category : BatchFileCategory.values()) {
            for (UserRole role : List.of(UserRole.CLERK, UserRole.LOCAL_AUTHORITY)) {
                builder.categories(role)
                    .categoryID(category.getId())
                    .categoryLabel(category.getLabel())
                    .displayOrder(category.getDisplayOrder());
            }
        }
    }

    private void configureAccessProfiles(DecentralisedConfigBuilder<BatchCase, BatchCaseState, UserRole> builder) {
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

    private void configureStateAccess(DecentralisedConfigBuilder<BatchCase, BatchCaseState, UserRole> builder) {
        for (BatchCaseState state : BatchCaseState.values()) {
            builder.grant(state, Permission.CRUD, UserRole.SYSTEM);
            builder.grant(state, Set.of(Permission.R, Permission.U), UserRole.CLERK);
            builder.grant(state, Set.of(Permission.R), UserRole.LOCAL_AUTHORITY);
        }
    }

    private void configureCaseView(DecentralisedConfigBuilder<BatchCase, BatchCaseState, UserRole> builder) {
        builder.tab("tasks", "Tasks")
            .forRoles(UserRole.CLERK, UserRole.SYSTEM)
            .label("tasksMarkdownLabel", null, "${tasksMarkdown}")
            .field("tasksMarkdown", NEVER_SHOW);

        // CCD shell only — real ExUI Roles and access is prepended when WA is enabled for the jurisdiction.
        builder.tab("rolesAndAccess", "Roles and access")
            .label("rolesAndAccessLabel", null, "${rolesAndAccessMarkdown}")
            .field("rolesAndAccessMarkdown", NEVER_SHOW);

        builder.tab("caseDetails", "Batch details")
            .field(BatchCase::getStatusDisplay)
            .field(BatchCase::getFileIdentifier)
            .field(BatchCase::getBatchIdentifier)
            .field(BatchCase::getLocalAuthority)
            .field(BatchCase::getSubmitterEmail)
            .field(BatchCase::getOperation)
            .field(BatchCase::getPcnCount)
            .field(BatchCase::getPcnProcessedCountDisplay)
            .field(
                BatchCase::getFeesPaid,
                "operation=\"registration\" AND [STATE]=\"PROCESSING_COMPLETE\""
            )
            .field(
                BatchCase::getFeesDue,
                "operation=\"registration\" AND [STATE]=\"QUEUED_FOR_PROCESSING\""
            )
            .field(BatchCase::getReceivedVia)
            .field(BatchCase::getReceivedAt, "receivedVia=\"email\"");

        builder.tab("caseFileView", "Case File View")
            .field(BatchCase::getCaseFileView, null, "#ARGUMENT(CaseFileView)")
            .field(BatchCase::getAllDocuments, NEVER_SHOW);

        // Explicit CaseHistory so History sits after Case File View (SDK otherwise prepends it).
        builder.tab("CaseHistory", "History")
            .field("caseHistory");

        builder.tab("caseLinks", "Linked Cases")
            .field(BatchCase::getLinkedCasesComponentLauncher, null, "#ARGUMENT(LinkedCases)")
            .field(
                BatchCase::getCaseLinks,
                "LinkedCasesComponentLauncher!=\"\"",
                "#ARGUMENT(LinkedCases)"
            );

        builder.searchInputFields()
            .field(BatchCase::getFileIdentifier, "File identifier")
            .field(BatchCase::getBatchIdentifier, "Batch identifier")
            .field(BatchCase::getLocalAuthority, "Local authority")
            .field(BatchCase::getSubmitterEmail, "Submitter email")
            .field(BatchCase::getOperation, "Batch type")
            .field(BatchCase::getReceivedVia, "Received via");

        builder.searchResultFields()
            .caseReferenceField()
            .field("[STATE]", "State")
            .field(BatchCase::getFileIdentifier, "File identifier")
            .field(BatchCase::getLocalAuthority, "Local authority")
            .field(BatchCase::getSubmitterEmail, "Submitter email")
            .field(BatchCase::getOperation, "Batch type")
            .field(BatchCase::getPcnCount, "Number of PCNs in batch")
            .field(BatchCase::getReceivedVia, "Received via")
            .field(BatchCase::getReceivedAt, "Email received at");

        builder.workBasketInputFields()
            .field(BatchCase::getFileIdentifier, "File identifier")
            .field(BatchCase::getBatchIdentifier, "Batch identifier")
            .field(BatchCase::getLocalAuthority, "Local authority")
            .field(BatchCase::getSubmitterEmail, "Submitter email")
            .field(BatchCase::getOperation, "Batch type")
            .field(BatchCase::getReceivedVia, "Received via");

        builder.workBasketResultFields()
            .caseReferenceField()
            .field(BatchCase::getFileIdentifier, "File identifier")
            .field(BatchCase::getLocalAuthority, "Local authority")
            .field(BatchCase::getSubmitterEmail, "Submitter email")
            .field(BatchCase::getOperation, "Batch type")
            .field(BatchCase::getPcnCount, "Number of PCNs in batch")
            .field("[STATE]", "State");
    }

    private void configureEvents(DecentralisedConfigBuilder<BatchCase, BatchCaseState, UserRole> builder) {
        builder.decentralisedEvent("createBatch", this::createBatch)
            .initialState(BatchCaseState.QUEUED_FOR_PROCESSING)
            .name("Batch created")
            .showCondition(NEVER_SHOW)
            .grant(Permission.CRUD, UserRole.SYSTEM)
            .grant(Permission.R, UserRole.CLERK)
            .grant(Permission.R, UserRole.LOCAL_AUTHORITY)
            .fields()
            .mandatory(BatchCase::getFileIdentifier)
            .mandatory(BatchCase::getBatchIdentifier)
            .mandatory(BatchCase::getPcnCount)
            .mandatory(BatchCase::getOperation)
            .mandatory(BatchCase::getReceivedVia)
            .mandatory(BatchCase::getReceivedAt)
            .mandatory(BatchCase::getLocalAuthority)
            .mandatory(BatchCase::getSubmitterEmail)
            .optional(BatchCase::getCaseAccessCategory, NEVER_SHOW);

        builder.decentralisedEvent(UPLOAD_BATCH_EVENT_ID, this::uploadBatch)
            .initialState(BatchCaseState.QUEUED_FOR_PROCESSING)
            .name("Upload batch file")
            .showSummary()
            .endButtonLabel("Submit")
            .grant(Permission.CRUD, UserRole.CLERK, UserRole.SYSTEM, UserRole.LOCAL_AUTHORITY)
            .fields()
            .page("selectBatchType")
            .pageLabel("Upload batch file")
            .mandatory(
                BatchCase::getBatchTypeSelection,
                null,
                null,
                "Select batch type",
                "Select the type of batch you want to create"
            )
            .page("interstitial")
            .pageLabel("Before you start")
            .label(
                "batchInterstitialPlaceholder",
                """
                    ## Before you upload your batch

                    Placeholder guidance for the selected batch type will appear here.

                    Make sure your file is in the correct format and that you have permission \
                    to submit this batch.
                    """.stripIndent().trim()
            )
            .page("uploadFile", this::populateValidationPlaceholder)
            .pageLabel("Upload batch file")
            .mandatory(BatchCase::getBatchFileDocument, null, null, "Upload a file")
            .page("validationResults")
            .pageLabel("Some data cannot be processed")
            .label("validationResultsHeading", "## Invalid PCN data")
            .label(
                "validationResultsBody",
                """
                    <p class="govuk-body">
                      3 PCNs you’ve uploaded contain invalid data. They cannot be processed.
                    </p>
                    <p class="govuk-body">
                      These PCNs will be removed from this batch:
                    </p>
                    <table class="govuk-table">
                      <tbody class="govuk-table__body">
                        <tr class="govuk-table__row">
                          <td class="govuk-table__cell">BS41291736</td>
                          <td class="govuk-table__cell">Missing name and address</td>
                        </tr>
                        <tr class="govuk-table__row">
                          <td class="govuk-table__cell">BS10568629</td>
                          <td class="govuk-table__cell">Incorrect characters in row</td>
                        </tr>
                        <tr class="govuk-table__row">
                          <td class="govuk-table__cell">BS73125084</td>
                          <td class="govuk-table__cell">Duplicated PCN</td>
                        </tr>
                      </tbody>
                    </table>
                    <p class="govuk-body">
                      You will be sent an exception report containing all PCNs which have been removed.
                    </p>
                    """.stripIndent().trim()
            )
            .readonly(BatchCase::getExcludedPcnCount, NEVER_SHOW)
            .page("statementOfTruth")
            .pageLabel("Statement of truth")
            .label(
                "statementOfTruthWarning",
                """
                    ---
                    <p class="govuk-body">
                      I understand that proceedings for contempt of court may be brought against
                      anyone who makes, or causes to be made, a false statement in a document
                      verified by a statement of truth without an honest belief in its truth.
                    </p>
                    """.stripIndent().trim()
            )
            .mandatory(BatchCase::getBatchStatementOfTruth, null, null, "Statement of truth")
            .optional(BatchCase::getCaseAccessCategory, NEVER_SHOW);

        builder.decentralisedEvent("startBatchProcessing", this::startBatchProcessing)
            .forStateTransition(
                BatchCaseState.QUEUED_FOR_PROCESSING,
                BatchCaseState.PROCESSING_STARTED
            )
            .name("Batch processing started")
            .showCondition(NEVER_SHOW)
            .grant(Permission.CRUD, UserRole.SYSTEM);

        builder.decentralisedEvent("completeBatchProcessing", this::completeBatchProcessing)
            .forStates(BatchCaseState.PROCESSING_STARTED, BatchCaseState.PROCESSING_COMPLETE)
            .name("Batch processing complete")
            .showCondition(NEVER_SHOW)
            .grant(Permission.CRUD, UserRole.SYSTEM);

        builder.decentralisedEvent("failBatchProcessing", this::failBatchProcessing)
            .forStates(BatchCaseState.PROCESSING_STARTED, BatchCaseState.PROCESSING_FAILED)
            .name("Batch processing failed")
            .showCondition(NEVER_SHOW)
            .grant(Permission.CRUD, UserRole.SYSTEM);

        builder.decentralisedEvent("attachBatchDocument", this::attachBatchDocument)
            .forStates(BatchCaseState.values())
            .name("Attach batch document")
            .showCondition(NEVER_SHOW)
            .grant(Permission.CRUD, UserRole.SYSTEM)
            .fields()
            .mandatory(BatchCase::getBatchFileDocument);

        builder.decentralisedEvent("linkPcnCases", this::linkPcnCases)
            .forStates(BatchCaseState.values())
            .name("Link PCN cases")
            .showCondition(NEVER_SHOW)
            .grant(Permission.CRUD, UserRole.SYSTEM)
            .grant(Permission.R, UserRole.CLERK)
            .fields()
            .mandatory(BatchCase::getCaseLinks);
    }

    AboutToStartOrSubmitResponse<BatchCase, BatchCaseState> populateValidationPlaceholder(
        CaseDetails<BatchCase, BatchCaseState> details,
        CaseDetails<BatchCase, BatchCaseState> detailsBefore
    ) {
        BatchCase data = details.getData();
        if (data == null) {
            data = new BatchCase();
            details.setData(data);
        }
        BatchUploadJourney.applyValidationPlaceholder(data);
        return AboutToStartOrSubmitResponse.<BatchCase, BatchCaseState>builder()
            .data(data)
            .build();
    }

    private SubmitResponse<BatchCaseState> createBatch(EventPayload<BatchCase, BatchCaseState> event) {
        repository.create(event.caseReference(), event.caseData());
        return response(BatchCaseState.QUEUED_FOR_PROCESSING);
    }

    private SubmitResponse<BatchCaseState> uploadBatch(EventPayload<BatchCase, BatchCaseState> event) {
        BatchCase data = event.caseData();
        BatchUploadJourney.applySubmitDefaults(event.caseReference(), data);

        if (data.getBatchTypeSelection() != null) {
            data.setOperation(data.getBatchTypeSelection().toOperation());
        }
        if (data.getOperation() == null) {
            throw new IllegalArgumentException("batch type is required");
        }
        data.setBatchTypeSelection(null);
        if (!BatchUploadJourney.hasAcceptedStatementOfTruth(data)) {
            throw new IllegalArgumentException(
                "You must confirm that the facts stated in this batch request are true"
            );
        }

        repository.create(event.caseReference(), data);
        attachUploadedDocumentIfPresent(event.caseReference(), data.getBatchFileDocument());

        return SubmitResponse.<BatchCaseState>builder()
            .state(BatchCaseState.QUEUED_FOR_PROCESSING)
            .confirmationHeader(BatchUploadJourney.confirmationHeader(data))
            .confirmationBody(BatchUploadJourney.confirmationBody(data, event.caseReference()))
            .build();
    }

    private SubmitResponse<BatchCaseState> startBatchProcessing(EventPayload<BatchCase, BatchCaseState> event) {
        return response(BatchCaseState.PROCESSING_STARTED);
    }

    private SubmitResponse<BatchCaseState> completeBatchProcessing(
        EventPayload<BatchCase, BatchCaseState> event
    ) {
        return response(BatchCaseState.PROCESSING_COMPLETE);
    }

    private SubmitResponse<BatchCaseState> failBatchProcessing(
        EventPayload<BatchCase, BatchCaseState> event
    ) {
        return response(BatchCaseState.PROCESSING_FAILED);
    }

    private SubmitResponse<BatchCaseState> attachBatchDocument(EventPayload<BatchCase, BatchCaseState> event) {
        Document document = event.caseData().getBatchFileDocument();
        requireDocument(document);
        attachUploadedDocumentIfPresent(event.caseReference(), document);
        return SubmitResponse.defaultResponse();
    }

    /**
     * Records standard {@code caseLinks} on the batch so CCD's {@code case_link} table
     * (and ExUI "linked to" / PCN "linked from") include each PCN. Registration batches
     * also set the current registration's batch link; other batch types use
     * {@code tec_batch_pcn_link}.
     */
    private SubmitResponse<BatchCaseState> linkPcnCases(EventPayload<BatchCase, BatchCaseState> event) {
        List<ListValue<CaseLink>> caseLinks = event.caseData().getCaseLinks();
        if (caseLinks == null || caseLinks.isEmpty()) {
            throw new IllegalArgumentException("caseLinks is required");
        }

        BatchCase batch = repository.find(event.caseReference());
        BatchOperation operation = batch.getOperation();
        boolean registration = operation == BatchOperation.REGISTRATION;

        for (ListValue<CaseLink> entry : caseLinks) {
            if (entry == null || entry.getValue() == null || isBlank(entry.getValue().getCaseReference())) {
                throw new IllegalArgumentException("caseLinks[].value.CaseReference is required");
            }
            long pcnCaseReference = parseCaseReference(entry.getValue().getCaseReference());
            if (!tecCaseRepository.exists(pcnCaseReference)) {
                throw new IllegalArgumentException(
                    "No TEC PCN case found for reference " + pcnCaseReference
                );
            }
            if (registration) {
                Long existingBatch = tecCaseRepository.findBatchCaseReference(pcnCaseReference);
                if (existingBatch != null && existingBatch.longValue() != event.caseReference()) {
                    throw new IllegalArgumentException(
                        "PCN case " + pcnCaseReference
                            + " is already linked to batch case " + existingBatch
                    );
                }
                tecCaseRepository.linkBatchCase(pcnCaseReference, event.caseReference());
            } else {
                repository.linkPcnCase(event.caseReference(), pcnCaseReference);
            }
        }
        return SubmitResponse.defaultResponse();
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

    private void attachUploadedDocumentIfPresent(long caseReference, Document document) {
        if (document == null) {
            return;
        }
        requireDocument(document);
        String categoryId = BatchFileCategory.normalisedCategoryId(document.getCategoryId());
        repository.insertDocument(
            caseReference,
            categoryId,
            CdamDocumentUrls.toCdamUrl(document.getUrl()),
            CdamDocumentUrls.toCdamUrl(document.getBinaryUrl()),
            document.getFilename()
        );
    }

    private static void requireDocument(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("batchFileDocument is required");
        }
        if (isBlank(document.getUrl())
            || isBlank(document.getBinaryUrl())
            || isBlank(document.getFilename())) {
            throw new IllegalArgumentException(
                "batchFileDocument requires document_url, document_binary_url and document_filename"
            );
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private SubmitResponse<BatchCaseState> response(BatchCaseState state) {
        return SubmitResponse.<BatchCaseState>builder().state(state).build();
    }
}
