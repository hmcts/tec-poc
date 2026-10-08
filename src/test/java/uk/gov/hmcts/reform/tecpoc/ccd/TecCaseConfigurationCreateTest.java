package uk.gov.hmcts.reform.tecpoc.ccd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.ccd.sdk.api.EventPayload;

class TecCaseConfigurationCreateTest {

    private TecCaseRepository repository;
    private TecCaseConfiguration configuration;

    @BeforeEach
    void setUp() {
        repository = mock(TecCaseRepository.class);
        configuration = new TecCaseConfiguration(repository, mock(BatchCaseRepository.class));
    }

    @Test
    void createTecCaseStoresStemFromSuffixZeroPcn() {
        TecCase data = new TecCase();
        data.setPenaltyChargeNumber("WE705998990");

        createTecCase(111L, data);

        assertThat(data.getPcnStem()).isEqualTo("WE70599899");
        verify(repository).create(111L, data);
    }

    @Test
    void createTecCaseRejectsNonZeroSuffix() {
        TecCase data = new TecCase();
        data.setPenaltyChargeNumber("WE705998991");

        assertThatThrownBy(() -> createTecCase(111L, data))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("suffix 0");
        verify(repository, never()).create(111L, data);
    }

    @Test
    void addRegistrationRequiresTheNextSuffix() {
        when(repository.currentRegistrationPcn(111L)).thenReturn("AB0531612A2");
        TecCase data = new TecCase();
        data.setPenaltyChargeNumber("AB0531612A4");

        assertThatThrownBy(() -> addRegistration(111L, data))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("AB0531612A3");
    }

    @Test
    void addRegistrationInsertsTheNextSuffix() {
        when(repository.currentRegistrationPcn(111L)).thenReturn("AB0531612A2");
        TecCase data = new TecCase();
        data.setPenaltyChargeNumber("AB0531612A3");

        addRegistration(111L, data);

        verify(repository).insertRegistration(111L, data);
    }

    @Test
    void editTe9StartsFromTheCurrentTe9Record() {
        Te9CaseDetails details = new Te9CaseDetails();
        details.setFullName("Current TE9");
        details.setPenaltyChargeNumber("AB0531612A2");
        TecCase loaded = new TecCase();
        loaded.setTe9Details(details);
        when(repository.find(111L)).thenReturn(loaded);

        TecCase form = startEditTe9(111L);

        assertThat(form.getApplicationFullName()).isEqualTo("Current TE9");
        assertThat(form.getApplicationForm()).isEqualTo(ApplicationForm.TE9);
        assertThat(form.getApplicationPenaltyChargeNumber()).isEqualTo("AB0531612A2");
    }

    private void createTecCase(long caseReference, TecCase data) {
        ReflectionTestUtils.invokeMethod(
            configuration,
            "createTecCase",
            new EventPayload<>(caseReference, data, null)
        );
    }

    private void addRegistration(long caseReference, TecCase data) {
        ReflectionTestUtils.invokeMethod(
            configuration,
            "addRegistration",
            new EventPayload<>(caseReference, data, null)
        );
    }

    private TecCase startEditTe9(long caseReference) {
        return ReflectionTestUtils.invokeMethod(
            configuration,
            "startEditTe9",
            new EventPayload<>(caseReference, new TecCase(), null)
        );
    }
}
