package ie.bitstep.mango.flowsink.model;

import io.opentelemetry.api.trace.StatusCode;

public record FlowStatus(StatusCode code, String message) {}
