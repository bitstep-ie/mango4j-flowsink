package ie.bitstep.mango.flowsink.model;

public class StepEvent {
	private final String name;
	private final long timeUnixNano;
	private long endTimeUnixNano;
	private long elapsedNanos;
	private final FlowAttributes attributes;
	private final String kind;

	public StepEvent(String name, long timeUnixNano, FlowAttributes attributes, String kind) {
		this.name = name;
		this.timeUnixNano = timeUnixNano;
		this.attributes = attributes == null ? new FlowAttributes() : attributes;
		this.kind = kind;
	}

	public String name() {
		return name;
	}

	public long timeUnixNano() {
		return timeUnixNano;
	}

	public long endTimeUnixNano() {
		return endTimeUnixNano;
	}

	public void setEndTimeUnixNano(long endTimeUnixNano) {
		this.endTimeUnixNano = endTimeUnixNano;
	}

	public long elapsedNanos() {
		return elapsedNanos;
	}

	public void setElapsedNanos(long elapsedNanos) {
		this.elapsedNanos = elapsedNanos;
	}

	public FlowAttributes attributes() {
		return attributes;
	}

	public String kind() {
		return kind;
	}

	public StepEvent snapshot() {
		StepEvent copy = new StepEvent(name, timeUnixNano, new FlowAttributes(attributes.map()), kind);
		copy.setEndTimeUnixNano(endTimeUnixNano);
		copy.setElapsedNanos(elapsedNanos);
		return copy;
	}
}
