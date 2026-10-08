package uk.gov.hmcts.reform.tecpoc.ccd;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.ccd.sdk.type.ListValue;

class TecCaseViewPreviousRegistrationsTest {

    @Test
    void singleRegistrationHasNoPreviousRegistrations() {
        assertThat(TecCaseView.excludingCurrent(List.of(registration("AB0531612A0", "2026-01-01T00:00:00Z"))))
            .isEmpty();
    }

    @Test
    void previousRegistrationsExcludeTheCurrentRowAndUseSuffixOrder() {
        TecCaseRegistration suffix2 = registration("AB0531612A2", "2026-03-01T00:00:00Z");
        TecCaseRegistration suffix0 = registration("AB0531612A0", "2026-01-01T00:00:00Z");
        TecCaseRegistration suffix1 = registration("AB0531612A1", "2026-02-01T00:00:00Z");

        List<TecCaseRegistration> previous = TecCaseView.excludingCurrent(
            List.of(suffix2, suffix0, suffix1)
        );

        assertThat(previous).containsExactly(suffix0, suffix1);
    }

    @Test
    void sameSuffixKeepsTheEarlierRowAndDropsTheLaterOne() {
        TecCaseRegistration earlier = registration("AB0531612A1", "2026-01-01T00:00:00Z");
        TecCaseRegistration later = registration("AB0531612A1", "2026-06-01T00:00:00Z");

        assertThat(TecCaseView.excludingCurrent(List.of(later, earlier))).containsExactly(earlier);
    }

    @Test
    void collectionMapsTheRegistrationOntoTheTab() {
        TecCaseRegistration registration = registration("AB0531612A0", "2026-01-01T00:00:00Z");

        List<ListValue<PreviousRegistration>> values = TecCaseView.toPreviousRegistrations(
            List.of(registration)
        );

        assertThat(values).hasSize(1);
        assertThat(values.get(0).getId()).isEqualTo(registration.id().toString());
        PreviousRegistration shown = values.get(0).getValue();
        assertThat(shown.getPenaltyChargeNumber()).isEqualTo("AB0531612A0");
        assertThat(shown.getBatchCase().getCaseReference()).isEqualTo("222");
        assertThat(shown.getLocalAuthority()).isEqualTo(LocalAuthority.WESTMINSTER);
        assertThat(shown.getRespondentDetails1()).isEqualTo("ALEX EXAMPLE");
        assertThat(shown.getAmountDue()).isEqualTo(12345);
    }

    private static TecCaseRegistration registration(String penaltyChargeNumber, String createdAt) {
        return new TecCaseRegistration(
            UUID.randomUUID(),
            Instant.parse(createdAt),
            "RAB12345",
            "RAB123456",
            222L,
            penaltyChargeNumber,
            LocalAuthority.WESTMINSTER,
            "ALEX EXAMPLE",
            "1 EXAMPLE STREET",
            "LONDON",
            "SW1A 1AA",
            null,
            null,
            "AB12CDE",
            "01",
            "260824",
            12345,
            "PENDING",
            null,
            null,
            null,
            LocalDate.of(2026, 1, 2)
        );
    }
}
