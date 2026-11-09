package dev.inditex.scsoutbox.config.producer;

/**
 * The property key and value the application declared for a binding, either at the binding-scoped key or, failing that, at the binder-wide
 * default key.
 */
record DeclaredSetting(String property, String value) {
}
