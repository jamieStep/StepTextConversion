package org.stepbible.textconverter

/****************************************************************************/
/**
 * Compile-time configuration flags.
 *
 * @author ARA "Jamie" Jamieson
 */



/****************************************************************************/
/** Determines whether subverses remain in the output as such, or whether we
 *  concatenate the content of all the subverses of a given verse and turn
 *  the result into the owning verse.
 */

const val C_ConfigurationFlag_CollapseSubverses = true


/****************************************************************************/
/** Controls whether or not we bother with USX validation prior to
*   generating OSIS.
*/

const val C_ConfigurationFlag_DoUsxFinalValidation = true


/****************************************************************************/
/** In general, tables in the raw USX are bad news, and in order to avoid
 *  complex problems with cross-boundary markup, we need to do something
 *  fairly drastic.  The two options are "Elide", which creates a large
 *  elision to cover the entire table, and "Flatten" which removes the
 *  table markup altogether and then does a rather half-hearted job of
 *  creating something table-ish using non-breaking spaces.
 */

const val C_ConfigurationFlag_PreferredTableConversionType = "Elide"


/****************************************************************************/
/** In reversification we evaluate the amount of change which reversification
*   has applied by counting how many reversification footnotes are applied.
*/

const val C_ConfigurationFlags_ReversificationThresholdMarkingAFairAmountOfWork = 6


/****************************************************************************/
/** Where verses are missing at the start of or within the body of chapters,
*   we always need to generate empty verses to fill the gaps.  This is not
*   necessarily the case at the end of chapters, though -- we may be happy
*   for chapters to finish prematurely.
*/

const val C_ConfigurationFlags_GenerateVersesAtEndsOfChapters = false
