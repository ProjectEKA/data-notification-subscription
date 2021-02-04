package in.projecteka.datanotificationsubscription.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ServiceInfo {
    private String id;
    private String name;
    private RequesterType type;
    private Boolean active;
    private Endpoints endpoints;

    @AllArgsConstructor
    @Data
    @Builder
    public static class Endpoints {
        List<EndpointDetails> hipEndpoints;
        List<EndpointDetails> hiuEndpoints;
        List<EndpointDetails> healthLockerEndpoints;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    @Builder
    public static class EndpointDetails {
        private String use;
        private String connectionType;
        private String address;
    }

}