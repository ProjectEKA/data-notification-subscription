package in.projecteka.consentmanager.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Builder
@Data
@NoArgsConstructor
public class TraceableMessage {
    String correlationId;
    Object message;
}
