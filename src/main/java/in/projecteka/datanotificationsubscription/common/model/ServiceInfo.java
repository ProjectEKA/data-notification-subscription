package in.projecteka.datanotificationsubscription.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@AllArgsConstructor
@Data
@Builder
public class ServiceInfo {
    private String id;
    private String name;
    private RequesterType type;
    private Boolean active;
    private List<Endpoint> endpoints;


    @AllArgsConstructor
    @Data
    @Builder
    public static class Endpoint {
        private String use;
        private String connectionType;
        private String address;
    }

}