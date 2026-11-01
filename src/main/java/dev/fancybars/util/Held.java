package dev.fancybars.util;

import java.util.Objects;
import java.util.function.BiPredicate;

import net.minecraft.util.Util;

/**
 * A value you just changed yourself (or that an instant event told us about) which a slower source, like a poll of
 * Spotify or the tab list, will only catch up with a bit later.
 * <p>
 * Without this the display flicks back and forth: new value, old value from the stale source, new value again.
 * Call {@link #set} when the change happens, and pass every reading from the slow source through {@link #filter}:
 * until the source agrees (or the hold runs out) you keep getting the new value back.
 * <p>
 * Use it for anything in the mod that's both set instantly and polled from somewhere slower.
 */
public final class Held<T> {
	private final long holdMs;
	private final BiPredicate<T, T> agrees;
	private volatile T value;
	private volatile long until;
	private volatile boolean active;

	/** {@code holdMs}: how long to wait at most for the slow source to catch up. */
	public Held(long holdMs) {
		this(holdMs, Objects::equals);
	}

	/** {@code agrees(polled, held)}: when a reading counts as having caught up. */
	public Held(long holdMs, BiPredicate<T, T> agrees) {
		this.holdMs = holdMs;
		this.agrees = agrees;
	}

	/** The value just changed to {@code v}: hold it until the slow source agrees. */
	public void set(T v) {
		value = v;
		until = Util.getMillis() + holdMs;
		active = true;
	}

	/** A reading from the slow source: returns what to show. */
	public T filter(T polled) {
		if (!active) return polled;
		if (agrees.test(polled, value) || Util.getMillis() >= until) {
			active = false;
			return polled;
		}
		return value;
	}

	/** Whether a reading that disagrees is being ignored right now. */
	public boolean holding() {
		if (active && Util.getMillis() >= until) active = false;
		return active;
	}

	public T value() {
		return value;
	}

	public void clear() {
		active = false;
	}
}
