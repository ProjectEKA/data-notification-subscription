package in.projecteka.datanotificationsubscription.hipLink;

import in.projecteka.datanotificationsubscription.common.model.PatientCareContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NewCCLinkEvent {
    private String hipId;
    private String healthNumber;
    private LocalDateTime timestamp;
    private List<PatientCareContext> careContexts;
}

