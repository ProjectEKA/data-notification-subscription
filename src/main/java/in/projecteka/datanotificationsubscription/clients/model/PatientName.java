package in.projecteka.datanotificationsubscription.clients.model;

import com.google.common.base.Strings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@EqualsAndHashCode
public class PatientName {
    private String first;
    private String middle;
    private String last;

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

