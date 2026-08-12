package uk.gov.hmcts.reform.tecpoc.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.ccd.client.CoreCaseDataApi;
import uk.gov.hmcts.reform.ccd.client.model.CaseDataContent;
import uk.gov.hmcts.reform.ccd.client.model.Event;
import uk.gov.hmcts.reform.ccd.client.model.StartEventResponse;
import uk.gov.hmcts.reform.tecpoc.ccd.CaseState;
import uk.gov.hmcts.reform.tecpoc.http.CreateTecCaseRequest;
import uk.gov.hmcts.reform.tecpoc.http.CreateTecCaseResponse;
import uk.gov.hmcts.reform.tecpoc.ccd.TecCaseConfiguration;

import static java.util.Objects.requireNonNull;

@Service
@RequiredArgsConstructor
public class TecCaseCreationService {

    private static final String EVENT_ID = "createTecCase";

    private final CoreCaseDataApi coreCaseDataApi;
    private final AuthTokenGenerator serviceTokenGenerator;

    public CreateTecCaseResponse create(CreateTecCaseRequest request, String authorisation) {
        String serviceAuthorisation = serviceTokenGenerator.generate();
        StartEventResponse startEvent = coreCaseDataApi.startCase(
            authorisation,
            serviceAuthorisation,
            TecCaseConfiguration.CASE_TYPE,
            EVENT_ID
        );

        var createdCase = coreCaseDataApi.submitCaseCreation(
            authorisation,
            serviceAuthorisation,
            TecCaseConfiguration.CASE_TYPE,
            CaseDataContent.builder()
                .event(Event.builder()
                    .id(EVENT_ID)
                    .summary("PCN case created from datafile")
                    .description("PCN case created from datafile")
                    .build())
                .eventToken(startEvent.getToken())
                .data(request.toCaseData())
                .build()
        );

        return new CreateTecCaseResponse(
            requireNonNull(createdCase.getId(), "CCD returned no case reference"),
            CaseState.valueOf(requireNonNull(createdCase.getState(), "CCD returned no case state"))
        );
    }
}
