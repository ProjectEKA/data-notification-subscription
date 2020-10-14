package in.projecteka.datanotificationsubscription.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Builder
@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class DateOfBirth {
    private final Integer date;
    private final Integer month;
    private final Integer year;
}
