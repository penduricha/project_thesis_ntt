/** Preserve the existing Vietnamese parser input when a translated example is selected.
 * User-edited text is always sent unchanged, including when it matches no example. */
export function resolveSampleQuery(text, selectedSample) {
  return selectedSample?.label === text ? selectedSample.query : text
}
