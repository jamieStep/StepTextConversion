/******************************************************************************/
package org.stepbible.textconverter.support.bibledetails

import org.stepbible.textconverter.support.configdata.StandardFileLocations


/******************************************************************************/
/**
 * A version of BibleBookAndFileMapper which looks at the raw USX folder.  This
 * should be used for the first time only after any preprocessing, because by
 * default it looks only at the RawUsx folder, but it will pick up preprocessed
 * files in preference to raw ones if they existed at the time it is first used
 * (which is when it initialises its file list).
 *
 * @author ARA "Jamie" Jamieson
*/

object BibleBookAndFileMapperRawUsx: BibleBookAndFileMapper()
{
  init
  {
    populate(StandardFileLocations.getPreprocessedUsxFolderPath(), StandardFileLocations.getRawInputFolderPath())
  }
}