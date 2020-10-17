package in.projecteka.datanotificationsubscription.common.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class RequesterTypeTest {
    @Test
    void shouldGiveRespectiveEnumFromText() {
        assertThat(RequesterType.fromText("HIP")).isEqualTo(RequesterType.HIP);
        assertThat(RequesterType.fromText("HIU")).isEqualTo(RequesterType.HIU);
        assertThat(RequesterType.fromText("HEALTH_LOCKER")).isEqualTo(RequesterType.HEALTH_LOCKER);
        assertThat(RequesterType.fromText("HIP_AND_HIU")).isEqualTo(RequesterType.HIP_AND_HIU);
    }

    @Test
    void shouldGiveInvalidRequesterTypeWhenNoMatchingFound() {
        assertThat(RequesterType.fromText("Random")).isEqualTo(RequesterType.INVALID_REQUESTER_TYPE);
        assertThat(RequesterType.fromText("Invalid")).isEqualTo(RequesterType.INVALID_REQUESTER_TYPE);
    }
}