package ie.bitstep.mango.flowsink.core.processor;

public final class FlowMeta {
	private final String kind;
	private final String statusCode;
	private final String statusMessage;
	private final String traceId;
	private final String spanId;
	private final String parentSpanId;
	private final String tracestate;

	private FlowMeta(Builder builder) {
		this.kind = builder.kind;
		this.statusCode = builder.statusCode;
		this.statusMessage = builder.statusMessage;
		this.traceId = builder.traceId;
		this.spanId = builder.spanId;
		this.parentSpanId = builder.parentSpanId;
		this.tracestate = builder.tracestate;
	}

	public String kind() {
		return kind;
	}

	public String statusCode() {
		return statusCode;
	}

	public String statusMessage() {
		return statusMessage;
	}

	public String traceId() {
		return traceId;
	}

	public String spanId() {
		return spanId;
	}

	public String parentSpanId() {
		return parentSpanId;
	}

	public String tracestate() {
		return tracestate;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String kind;
		private String statusCode;
		private String statusMessage;
		private String traceId;
		private String spanId;
		private String parentSpanId;
		private String tracestate;

		public Builder kind(String kind) {
			this.kind = kind;
			return this;
		}

		public Builder status(String statusCode, String statusMessage) {
			this.statusCode = statusCode;
			this.statusMessage = statusMessage;
			return this;
		}

		public Builder trace(String traceId, String spanId, String parentSpanId, String tracestate) {
			this.traceId = traceId;
			this.spanId = spanId;
			this.parentSpanId = parentSpanId;
			this.tracestate = tracestate;
			return this;
		}

		public FlowMeta build() {
			return new FlowMeta(this);
		}
	}
}
