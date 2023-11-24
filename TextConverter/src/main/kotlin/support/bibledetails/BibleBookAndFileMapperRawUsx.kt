/******************************************************************************/
package org.stepbible.textconverter.support.bibledetails

import org.stepbible.textconverter.support.configdata.StandardFileLocations


/******************************************************************************/
/**
 * A version of BibleBookAndFileMapper which looks at the raw USX folder (and
 * *only* that folder.  This can be used at any time, since it is not reliant
 * upon any processing having been applied to the text.  Contrast this with
 * [BibleBookAndFileMapperCombinedRawAndPreprocessedUsxRawUsx], qv.
 *
 * @author ARA "Jamie" Jamieson
*/

object BibleBookAndFileMapperRawUsx: BibleBookAndFileMapper()
{
  init
  {
    populate(StandardFileLocations.getRawInputFolderPath(), null)
  }
}