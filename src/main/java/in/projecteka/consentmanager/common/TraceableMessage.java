package in.projecteka.consentmanager.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.logstash.logback.encoder.org.apache.commons.lang3.StringUtils;

import java.util.UUID;

@AllArgsConstructor
@Builder
@Data
@NoArgsConstructor
public class TraceableMessage {
    String correlationId;
    Object message;

    public String getCorrelationId() {
        return StringUtils.isEmpty(correlationId) ? UUID.randomUUID().toString() : correlationId;
    }
}
