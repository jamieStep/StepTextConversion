/******************************************************************************/
package org.stepbible.textconverter.support.bibledetails


/******************************************************************************/
/**
 * Chapter and verse structure of the NRSV or NRSVA, depending upon whether or
 * not we are working with DC books.  This is just a convenience function -- it
 * serves as a wrapper to the relevant osis2mod schemes -- but it's useful to
 * have it because NRSV(A) has a special role in the processing from time to
 * time.
 * 
 * @author ARA "Jamie" Jamieson
*/

fun BibleStructureNrsvx(): BibleStructureSupportedByOsis2modIndividual { return BibleStructuresSupportedByOsis2modAll.getStructureFor(if (BibleStructureTextUnderConstruction.hasDc()) "nrsva" else "nrsv") }
