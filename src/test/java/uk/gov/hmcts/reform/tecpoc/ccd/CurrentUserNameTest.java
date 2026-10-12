package uk.gov.hmcts.reform.tecpoc.ccd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uk.gov.hmcts.reform.idam.client.IdamApi;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;

class CurrentUserNameTest {

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void nameReadsTheIdamDisplayNameFromTheAuthorizationHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        IdamApi idamApi = mock(IdamApi.class);
        when(idamApi.retrieveUserInfo("Bearer token")).thenReturn(
            UserInfo.builder().name("Ada Clerk").build()
        );

        assertThat(new CurrentUserName(idamApi).name()).isEqualTo("Ada Clerk");
    }

    @Test
    void nameRejectsAMissingAuthorizationHeader() {
        RequestContextHolder.setRequestAttributes(
            new ServletRequestAttributes(new MockHttpServletRequest())
        );

        assertThatThrownBy(() -> new CurrentUserName(mock(IdamApi.class)).name())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Authorization header is required to identify the current user");
    }
}
