package org.cossbow.feng.c2feng.model;

import java.util.Optional;

/**
 * A C enum constant.
 * <p>
 * When {@code value} is empty the constant is implicitly
 * incremented from its predecessor (the first constant defaults to 0).
 */
public record CEnumConstant(String name, Optional<Long> value) {
}
