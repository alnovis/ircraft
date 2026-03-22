package io.alnovis.ircraft.dialect.semantic.lowering

/** Configuration for Proto → Semantic lowering.
  *
  * Mirrors the relevant fields from proto-wrapper-plugin's GeneratorConfig.
  */
case class LoweringConfig(
    /** Base package for generated API interfaces (e.g., "com.example.model.api"). */
    apiPackage: String,

    /** Package pattern for version-specific impl classes. %s is replaced with version (e.g., "com.example.model.%s"). */
    implPackagePattern: String,

    /** Whether to generate Builder interfaces. */
    generateBuilders: Boolean = false,

    /** Whether to convert Well-Known Types (Timestamp→Instant, etc.). */
    convertWellKnownTypes: Boolean = true,

    /** Whether to generate ProtocolVersions utility class. */
    generateProtocolVersions: Boolean = true,

    /** Whether to generate validation annotations (@NotNull, etc.). */
    generateValidationAnnotations: Boolean = false,

    /** Prefix for abstract class names (default: "Abstract"). */
    abstractClassPrefix: String = "Abstract",
):
  def implPackage(version: String): String = implPackagePattern.format(version)
  def implSubPackage: String               = apiPackage + ".impl"
