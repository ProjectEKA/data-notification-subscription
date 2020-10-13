package in.projecteka.datanotificationsubscription.common.model;

import com.google.common.base.Strings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Builder
@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class PatientName {
    private final String first;
    private final String middle;
    private final String last;

    public String createFullName(){
        var fullName = first;

        if (!Strings.isNullOrEmpty(middle)){
            fullName += " " + middle;
        }

        if (!Strings.isNullOrEmpty(last)){
            fullName += " " + last;
        }
        return fullName;
    }
}
