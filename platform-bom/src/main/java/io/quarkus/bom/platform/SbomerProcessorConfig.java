package io.quarkus.bom.platform;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class SbomerProcessorConfig {

    private String type;
    private SbomerErrataConfig errata;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public SbomerErrataConfig getErrata() {
        return errata;
    }

    public void setErrata(SbomerErrataConfig errata) {
        this.errata = errata;
    }
}
