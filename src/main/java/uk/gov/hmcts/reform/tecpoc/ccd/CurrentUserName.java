package uk.gov.hmcts.reform.tecpoc.ccd;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uk.gov.hmcts.reform.idam.client.IdamApi;

/**
 * Display name of the user who submitted the current CCD callback.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserName {

    static final int MAX_LENGTH = 255;

    private final IdamApi idamApi;

    public String name() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            throw new IllegalStateException(
                "No HTTP request is available to identify the current user"
            );
        }
        String authorisation = servletAttributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        if (authorisation == null || authorisation.isBlank()) {
            throw new IllegalStateException(
                "Authorization header is required to identify the current user"
            );
        }
        String name = idamApi.retrieveUserInfo(authorisation).getName();
        if (name == null || name.isBlank()) {
            throw new IllegalStateException(
                "IDAM did not return a display name for the current user"
            );
        }
        if (name.length() > MAX_LENGTH) {
            throw new IllegalStateException(
                "IDAM display name is longer than " + MAX_LENGTH + " characters"
            );
        }
        return name;
    }
}
