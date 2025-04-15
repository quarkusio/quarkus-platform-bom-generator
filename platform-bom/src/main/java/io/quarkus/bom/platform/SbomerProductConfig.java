package io.quarkus.bom.platform;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class SbomerProductConfig {

    private List<SbomerProcessorConfig> processors;
    private SbomerGeneratorConfig generator;

    public List<SbomerProcessorConfig> getProcessors() {
        return processors;
    }

    public void setProcessors(List<SbomerProcessorConfig> processors) {
        this.processors = processors;
    }

    public SbomerGeneratorConfig getGenerator() {
        return generator;
    }

    public void setGenerator(SbomerGeneratorConfig generator) {
        this.generator = generator;
    }
}
