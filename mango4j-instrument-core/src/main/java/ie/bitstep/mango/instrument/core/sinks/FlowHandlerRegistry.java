package ie.bitstep.mango.instrument.core.sinks;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FlowHandlerRegistry {
	private final CopyOnWriteArrayList<FlowSinkHandler> handlers = new CopyOnWriteArrayList<>();

	public void register(FlowSinkHandler handler) {
		if (handler != null && !handlers.contains(handler)) {
			handlers.add(handler);
		}
	}

	public List<FlowSinkHandler> handlers() {
		return List.copyOf(handlers);
	}
}
