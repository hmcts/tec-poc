package uk.gov.hmcts.reform.tecpoc.ccd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PcnNumbersTest {

    @Test
    void stemDropsTheSuffixAndKeepsTheCheckCharacter() {
        assertThat(PcnNumbers.stem("AB0531612A2")).isEqualTo("AB0531612A");
        assertThat(PcnNumbers.stem("WE705998990")).isEqualTo("WE70599899");
        assertThat(PcnNumbers.stem("WCC123456700")).isEqualTo("WCC12345670");
    }

    @Test
    void nextRegistrationPcnIncrementsTheHighestSuffix() {
        assertThat(PcnNumbers.nextRegistrationPcn("AB0531612A2")).isEqualTo("AB0531612A3");
        assertThat(PcnNumbers.suffix("AB0531612A0")).isZero();
    }

    @Test
    void initialRegistrationMustUseSuffixZero() {
        PcnNumbers.requireInitialRegistration("WE705998990");

        assertThatThrownBy(() -> PcnNumbers.requireInitialRegistration("WE705998991"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("suffix 0");
    }

    @Test
    void suffixCannotExceedNine() {
        assertThatThrownBy(() -> PcnNumbers.nextRegistrationPcn("AB0531612A9"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot exceed 9");
    }
}
