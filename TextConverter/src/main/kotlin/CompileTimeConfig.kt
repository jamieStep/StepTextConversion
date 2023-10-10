package org.stepbible.textconverter

/****************************************************************************/
/**
 * Compile-time configuration flags.
 *
 * @author ARA "Jamie" Jamieson
 */



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
*
*   This was added in Sept 2023 because DIB felt it inappropriate to
*   generate empty verses, and indeed has put forward a good rationale for
*   not doing so.  However, subsequent experience indicated that when using
*   the Crosswire version of osis2mod and JSword, missing verses were
*   fabricated anyway, and I therefore felt it better to generate them
*   myself, since this gives us the chance to explain why the empty verse
*   is there.  I therefore changed the setting below to false.
*
*   Note that if we end up going with Sami's revised osis2mod and JSword,
*   this ceases to be an issue anyway, because we never generate empty
*   verses there.
*/

const val C_ConfigurationFlags_GenerateVersesAtEndsOfChapters = true
