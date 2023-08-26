/******************************************************************************/
package org.stepbible.textconverter.support.bibledetails

import org.stepbible.textconverter.support.configdata.StandardFileLocations


/******************************************************************************/
/**
 * A verse of BibleBookAndFileMapper which handles files stored in the
 * EnhancedUsx folder.
 *
 * @author ARA "Jamie" Jamieson
*/

object BibleBookAndFileMapperEnhancedUsx: BibleBookAndFileMapper()
{
  init
  {
    populate(StandardFileLocations.getEnhancedUsxFolderPath(), null)
  }
}