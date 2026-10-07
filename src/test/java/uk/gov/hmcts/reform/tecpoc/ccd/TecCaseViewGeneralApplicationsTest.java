package uk.gov.hmcts.reform.tecpoc.ccd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.ccd.sdk.CaseViewRequest;
import uk.gov.hmcts.ccd.sdk.type.ListValue;

class TecCaseViewGeneralApplicationsTest {

    @Test
    void toGeneralApplicationsMapsPersistedRows() {
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        List<ListValue<GeneralApplication>> values = TecCaseView.toGeneralApplications(List.of(
            new TecCaseGeneralApplication(
                id,
                1,
                GeneralApplicationApplicant.LOCAL_AUTHORITY,
                LocalDate.of(2026, 4, 16),
                GeneralApplicationType.SET_ASIDE,
                null,
                null,
                30300,
                YesNo.NO,
                null,
                YesNo.YES,
                null,
                GeneralApplicationState.GEN_APP_ISSUED
            )
        ));

        assertThat(values).hasSize(1);
        assertThat(values.get(0).getId()).isEqualTo(id.toString());
        GeneralApplication application = values.get(0).getValue();
        assertThat(application.getRank()).isEqualTo(1);
        assertThat(application.getApplicant()).isEqualTo(GeneralApplicationApplicant.LOCAL_AUTHORITY);
        assertThat(application.getDateReceived()).isEqualTo(LocalDate.of(2026, 4, 16));
        assertThat(application.getApplicationType()).isEqualTo(GeneralApplicationType.SET_ASIDE);
        assertThat(application.getFeeAmountReceived()).isEqualTo(30300);
        assertThat(application.getState()).isEqualTo(GeneralApplicationState.GEN_APP_ISSUED);
    }

    @Test
    void getCaseClearsTheEventFormAndShowsSavedApplications() {
        TecCaseRepository repository = mock(TecCaseRepository.class);
        TecCase stored = new TecCase();
        stored.setGeneralApplication(new GeneralApplicationEntry());
        UUID id = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(repository.find(30L)).thenReturn(stored);
        when(repository.findDocuments(30L)).thenReturn(List.of());
        when(repository.findWarrantAuthorisations(30L)).thenReturn(List.of());
        when(repository.findGeneralApplications(30L)).thenReturn(List.of(
            new TecCaseGeneralApplication(
                id,
                2,
                GeneralApplicationApplicant.RESPONDENT,
                LocalDate.of(2026, 3, 1),
                GeneralApplicationType.ADJOURN,
                null,
                YesNo.YES,
                100,
                YesNo.NO,
                null,
                YesNo.NO,
                YesNo.YES,
                GeneralApplicationState.GEN_APP_ISSUED
            )
        ));

        TecCase result = new TecCaseView(repository).getCase(
            new CaseViewRequest<>(30L, CaseState.CLOSED)
        );

        assertThat(result.getGeneralApplication()).isNull();
        assertThat(result.getGeneralApplications()).hasSize(1);
        assertThat(result.getGeneralApplications().get(0).getValue().getRank()).isEqualTo(2);
        assertThat(result.getGeneralApplications().get(0).getValue().getState())
            .isEqualTo(GeneralApplicationState.GEN_APP_ISSUED);
    }
}
