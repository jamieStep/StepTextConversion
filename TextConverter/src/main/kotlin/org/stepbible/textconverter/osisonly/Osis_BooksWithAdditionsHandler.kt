package org.stepbible.textconverter.osisonly

import org.stepbible.textconverter.applicationspecificutils.Globals
import org.stepbible.textconverter.applicationspecificutils.X_DataCollection
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ParallelRunning
import org.w3c.dom.Node
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNamesOsis
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.set


/******************************************************************************/
/**
 * Deals with DC books which exist in two forms in Greek.  At the time of
 * writing, this is limited to Greek Esther, which may be supplied either as
 * the full text of the book or as just those chapters and verses which the
 * Greek text has over and above the Hebrew text.  (In theory I believe there
 * is a similar issue with Daniel, but it doesn't look as though Crosswire
 * cope with both versions there.)
 *
 * The purpose of the present class is to apply renaming if necessary so that
 * we always supply to osis2mod whatever we've decided it should contain
 * (something which, at the time of writing, is still subject to debate).
 *
 * Regrettably it's not as simple as just renaming the book, because there may
 * be cross-references which use the wrong name, and these therefore need to
 * be updated as well.
 *
 * @author ARA "Jamie" Jamieson
 */

object Osis_BooksWithAdditionsHandler
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Public                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
  * Deals with DC books which exist in two forms -- the full book, and additions
  * over and above the standard Hebrew text.  See head-of-class comments for
  * details.
  *
  * @param dataCollection Data to be processed.
  */

  fun process (dataCollection: X_DataCollection)
  {
    process(dataCollection, "EsthGr", "EsthGr", "AddEsth", "Greek Esther")
    process(dataCollection, "DanGr", "DanGr", "AddDan", "Greek Daniel")
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Private                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private fun process (dataCollection: X_DataCollection, preferredName: String, fullTextBookName: String, additionsBookName: String, explanation: String)
  {
    /**************************************************************************/
    /* See if we have either the full text or the additions.  If we do, 'name'
       will end up giving the name of the book, and bookNode will be ... well
       what it says on the tin.  If we have neither, this text doesn't contain
       the book, so there's nothing to do. */

    var name = fullTextBookName
    var bookNode = dataCollection.getRootNode(BibleBookNamesOsis.abbreviatedNameToNumber(name))
    if (null == bookNode)
    {
      name = additionsBookName
      bookNode = dataCollection.getRootNode(BibleBookNamesOsis.abbreviatedNameToNumber(name))
    }

    if (null == bookNode)
      return



    /**************************************************************************/
    /* If the book exists under the preferred name and we didn't make any
       previous changes to achieve that, then there's nothing to do. */

    if (name.equals(preferredName, ignoreCase = true) && null == Globals.BookNamesRevisions[BibleBookNamesOsis.abbreviatedNameToNumber(preferredName)])
      return



    /**************************************************************************/
    Rpt.report(level = 1, "Handling any name changes required for $explanation.")
    val nonPreferredName = if (preferredName.equals(fullTextBookName, ignoreCase = true)) additionsBookName else fullTextBookName
    bookNode["osisID"] = preferredName

    Rpt.reportWithContinuation(level = 1, "Handling any name changes required for $explanation ...") {
      with(ParallelRunning(true)) {
        run {
          dataCollection.getRootNodes().forEach { rootNode ->
            asyncable {
              Rpt.reportBookAsContinuation(dataCollection.getFileProtocol().getBookAbbreviation(rootNode))
              processChange(rootNode, preferredName, nonPreferredName)
            } // asyncable
          } // forEach
        } // run
      } // parallel
    } // reportWithContinuation
  } // fun


  /****************************************************************************/
  private fun processChange (rootNode: Node, preferredName: String, nonPreferredName: String)
  {
  }
}