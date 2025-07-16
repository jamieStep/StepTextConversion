package org.stepbible.textconverter.applicationspecificutils

import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleAnatomy
import org.stepbible.textconverter.nonapplicationspecificutils.bibledetails.BibleBookNamesUsx
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.FileLocations
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.w3c.dom.Document
import java.nio.file.Paths


/******************************************************************************/
/**
 * Obtains and supplies details of the order in which books should appear in
 * the module.  This is relevant only when we are generating STEP-internal
 * modules, where the order needs to be reflected in the JSON file which we
 * pass to our own osis2mod.  (It does *not* need to be reflected in the
 * generated OSIS, because it is the JSON file which determines the order.)
 *
 * If ordering information has been overtly supplied, we use that.  Otherwise
 * if we are taking input from a single file (which is the case with OSIS,
 * VL and IMP inputs), we use the order which appears in that file.  Otherwise
 * if input is coming from multiple files (always the case with USX), I order
 * things such that any DC books come out between the OT and the NT, in a
 * fixed order.
 *
 * At the time of writing, the only circumstances under which we are likely to
 * have translator-supplied ordering is on DBL, where the metadata files do
 * list books, and therefore inevitably do so in *some* order.  What I am not
 * sure about, though, is whether this order is meaningful, or whether it can
 * essentially be random.  I am assuming the former, but have not found any
 * documentation to confirm that.
 *
 * @author ARA "Jamie" Jamieson
 */

object BookOrdering: ObjectInterface
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
  * Returns a list of book numbers in the order in which they are to appear in
  * the module.
  *
  * <span class='important'>Where I have to supply a default list (as is the
  * case with USX input in the absence of any overt direction from the
  * translators), this list may be extensive, and may contain books which do
  * not actually appear in the given text.  You should therefore filter the
  * list before actually doing anything with it.
  *
  * @return Ordered list of book numbers.
  */

  fun getOrder (): List<Int>
  {
    if (null == m_Ordering)
      initialiseFromDefault()

    return m_Ordering!!
  }


  /****************************************************************************/
  /**
   * Common interface to external metadata.  At present only DBL is
   * supported.  Initialises the ordering from the metadata if the metadata
   * is available and contains relevant information.
   *
   * If suitable information is not available, just uses a default setting.
   *
   * @return True if successfully initisalised from metadata.  Otherwise
   *   false, even though set to the default value.
   */

  fun initialiseFromMetadata (): Boolean
  {
    val doneIt = when ((ConfigData["stepExternalDataFormat"] ?: "").lowercase())
    {
      "dbl" -> initialiseFromMetadataDbl()
      else -> false
    }

    if (!doneIt)
      initialiseFromDefault()

    return doneIt
  }


  /****************************************************************************/
  /**
  * Takes ordering from an input OSIS document.  The actual ordering used may
  * differ from this if we are dealing with a text where the translators have
  * supplied ordering information.
  *
  * @param doc
  */

  fun initialiseFromOsis (doc: Document)
  {
    if (!initialiseFromMetadata())
      m_Ordering = Osis_FileProtocol.getBookNodes(doc).map { Osis_FileProtocol.getBookNumber(it) }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                              Private                                   **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /* Used as a default when the input is coming from USX (where we have separate
     files per book, and therefore no implicit ordering), when the translators
     have not stipulated an ordering.

     Assumes the OT and NT are ordered in the standard way for Protestant
     Bibles, and then places all of the DC books between them in the order in
     which I happen to have them.

     Note that I do not attempt here to limit this to the books actually
     available.  There is a very extensive collection of seldom-encountered
     books in the DC portion, so the chances are that the list I create here
     will always be far too long.  The caller should therefore filter it to
     limit it to just those books actually available. */

  private fun initialiseFromDefault ()
  {
    val x = IntRange(BibleAnatomy.getBookNumberForStartOfOt(), BibleAnatomy.getBookNumberForEndOfOt()).toMutableList()
    x.addAll(IntRange(BibleAnatomy.getBookNumberForStartOfDc(), BibleAnatomy.getBookNumberForEndOfDc()))
    x.addAll(IntRange(BibleAnatomy.getBookNumberForStartOfNt(), BibleAnatomy.getBookNumberForEndOfNt()))
    m_Ordering = x
  }


  /****************************************************************************/
  /* I probably ought to find some way of integrating this with the main DBL
     external data processing, but that processing is very complicated, and I'm
     not sure I can be bothered.  Plus at present I'm none too sure a) whether
     I'm looking at the right data within the DBL metadata file (I haven't
     found any useful documentation); nor b) whether the order in which things
     appear there is meaningful anyway.

     As things stand, I'm assuming that I need publication/structure/content
     nodes, and that I need to take the 'role' attribute from each. */

  private fun initialiseFromMetadataDbl (): Boolean
  {
     val doc = Dom.getDocument(Paths.get(FileLocations.getMetadataFolderPath(), "metadata.xml").toString())
     val contentNodes = doc.findNodeByName("publication")?.findNodeByName("structure", false)?.findNodesByName("content") ?: return false
     m_Ordering = contentNodes.map { BibleBookNamesUsx.abbreviatedNameToNumber(it["role"]!!) }
     return true
  }


  /****************************************************************************/
  private var m_Ordering: List<Int>? = null
}