package org.stepbible.textconverter.usxinputonly

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Dbg
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.applicationspecificutils.*
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Rpt
import org.w3c.dom.Node


/******************************************************************************/
/**
* Applies list encapsulation to USX.
*
* USX supports things which OSIS sees as lists -- bullet point lists and
* poetry verses.  In theory, OSIS requires these to be encapsulated, in the
* same way that HTML li is held within ul markers.
*
* In point of fact, it appears to be possible to get away without this,
* and at one time -- when we were anticipating that we would be restructuring
* the text as part of reversification processing -- we were relying upon that
* fact.  The reason for this was that reversification required that there be
* no markup running across verse boundaries, and encapsulating lists was very
* likely to introduce such markup.
*
* Now, however, we have decided against reversification; and since osis2mod
* apparently copes rather better with encapsulated lists than without them,
* it is useful to introduce encapsulation.
*
* Unfortunately, USX does not have encapsulating tags, so we need to find a
* way of adding them, which, as you may have guessed, is far from
* straightforward.
*
* @author ARA "Jamie" Jamieson
*/

object Usx_ListEncapsulator: ObjectInterface
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
  * Applies encapsulation processing to a data collection.  Note that despite
  * disclaimers elsewhere, this does actually currently assume that each
  * book comes in a separate document.
  *
  * @param dataCollection: Data to be processed.
  */

  fun process (dataCollection: X_DataCollection)
  {
    Rpt.reportWithContinuation(level = 1, "Tidying ...") {
      with(ParallelRunning(true)) {
        run {
          dataCollection.getRootNodes().forEach { rootNode ->
            asyncable { Usx_EncapsulatorPerBook().processRootNode(rootNode) }
          }
        }
      }
    }
  }
}




/******************************************************************************/
private class Usx_EncapsulatorPerBook
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                               Private                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Canonicalises and generally tidies up a single document -- but does not
     fix any problems which I reckon might also turn up in OSIS when we use
     that as input: things like that I sort out later by messing around with
     the OSIS.

     IMPORTANT: This assumes that the data has already been read into an
     X_DataCollection, and book nodes have been converted into enclosing
     nodes. */

  fun processRootNode (rootNode: Node)
  {
    m_BookName = rootNode["code"]!!
    Rpt.reportBookAsContinuation(m_BookName)
    val chapterNodes = rootNode.findNodesByName("chapter")
    // $$$$$$$$$$$$$$$$$
  }
    /**************************************************************************/
    private var m_BookName = ""
}