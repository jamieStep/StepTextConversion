package org.stepbible.textconverter.subelements

import org.stepbible.textconverter.support.configdata.ConfigData
import org.stepbible.textconverter.support.miscellaneous.MarkerHandler
import org.stepbible.textconverter.support.miscellaneous.Dom
import org.stepbible.textconverter.support.miscellaneous.MarkerHandlerFactory
import org.stepbible.textconverter.support.miscellaneous.Translations
import org.stepbible.textconverter.utils.NodeMarker
import org.stepbible.textconverter.utils.ReversificationData
import org.stepbible.textconverter.utils.ReversificationDataRow
import org.stepbible.textconverter.utils.X_FileProtocol
import org.w3c.dom.Node

/******************************************************************************/
/**
 * Handles footnotes for verses affected by reversification and
 * samification.
 *
 * @author ARA "Jamie" Jamieson
 */

class SE_ReversificationFootnoteHandler (fileProtocol: X_FileProtocol)
 {
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  /**
   * This method has changed a lot over time.
   *
   * The fundamental aim was to have it create the content required for a
   * reversification footnote; and in particular, that footnote would use, as
   * callout, something which gave information about the verse involved in the
   * reversification activity.
   *
   * A subsequent change to the STEP renderer, however, meant that regardless of
   * what callout I requested here, the callout was actually shown as a down-
   * arrow, which meant that the information contained within the callout was no
   * longer visible.
   *
   * In view of this, we took to including the text of the callout actually as
   * part of the canonical text.
   *
   * This has grown in complexity over time.  The reversification data may now
   * call for a) The footnote itself; b) some text which will typically end
   * up in brackets and superscripted or subscripted; and c) another piece
   * of text which will typically end up in boldface.  Not all of these will
   * necessarily be present in all cases.
   *
   * @param sidNode The sid node of the verse to which the footnote is to be
   *   added.
   *
   * @param row: The reversification row which gives the salient details.
   *
   */

  fun addFootnoteAndSourceVerseDetailsToVerse (sidNode: Node, row: ReversificationDataRow)
  {
    /**************************************************************************/
    /* We only want the footnote if we are applying an appropriate level of
       reversification. */

    val wantFootnote = ReversificationData.wantFootnote(row, 'C', if (C_ReversificationNotesLevel_Basic == m_ReversificationNotesLevel) 'B' else 'A')
    val calloutDetails = row.calloutDetails
    val document = sidNode.ownerDocument
    val res: MutableList<Node> = mutableListOf()



    /**************************************************************************/
    /* This is the 'pukka' callout -- ie the piece of text which you click on in
       order to reveal the footnote.  Originally it was expected to be taken
       from the NoteMarker text in the reversification data.  However, we then
       found that STEP always rendered it as a down-arrow; and latterly DIB has
       decided he wants it that way even if STEP is fixed to display the actual
       callout text we request.  I therefore need to generate a down-arrow here,
       which is what the callout generator gives me. */

    val callout: String = m_FootnoteCalloutGenerator.get()



    /**************************************************************************/
    /* Insert the footnote itself. */

    if (wantFootnote)
    {
      var text  = ReversificationData.getFootnoteReversification(row)
      text = text.replace("S3y", "S3Y") // DIB prefers this.
      val ancientVersions = if (m_ReversificationNotesLevel > C_ReversificationNotesLevel_Basic) ReversificationData.getAncientVersions(row) else null
      if (null != ancientVersions) text += " $ancientVersions"
      val noteNode = m_FileProtocol.makeFootnoteNode(document, row.standardRefAsRefKey, text, callout)
      res.add(noteNode)
      res.add(Dom.createTextNode(document, " "))



      /************************************************************************/
      /* Bit of rather yucky special case processing.  I have been asked to
         force certain footnotes to the start of the owning verse, even if their
         natural position would be later.  I flag such notes here with a special
         attribute and then move them later. */

      if (row.requiresNotesToBeMovedToStartOfVerse())
        NodeMarker.setMoveNoteToStartOfVerse(noteNode)



      /************************************************************************/
      /* Check if we need the text which will typically be superscripted and
         bracketed. */

      val alternativeRefCollection = calloutDetails.alternativeRefCollection
      if (null != alternativeRefCollection)
      {
        val basicContent = if (calloutDetails.alternativeRefCollectionHasEmbeddedPlusSign)
          alternativeRefCollection.getLowAsRef().toString("a") + Translations.stringFormatWithLookup("V_reversification_alternativeReferenceEmbeddedPlusSign") + alternativeRefCollection.getHighAsRef().toString("a")
        else if (calloutDetails.alternativeRefCollectionHasPrefixPlusSign)
          Translations.stringFormatWithLookup("V_reversification_alternativeReferencePrefixPlusSign") + alternativeRefCollection.toString("a")
        else
          alternativeRefCollection.toString("a")

        val textNode = Dom.createTextNode(document, Translations.stringFormatWithLookup("V_reversification_alternativeReferenceFormat", basicContent))
        val containerNode = Dom.createNode(document, "<_X_reversificationCalloutAlternativeRefCollection/>")
        containerNode.appendChild(textNode)
        res.add(containerNode)
      }
    } // if (wantFootnote)



    /**************************************************************************/
    /* Add the source verse details. */

    val sourceRefCollection = calloutDetails.sourceVerseCollection
    if (null != sourceRefCollection)
    {
      //if (res.hasChildNodes())
      //  res.appendChild(Dom.createTextNode(document, " "))

      val basicContent = sourceRefCollection.toString("a")
      val textNode = Dom.createTextNode(document, Translations.stringFormatWithLookup("V_reversification_alternativeReferenceFormat", basicContent))
      val containerNode = Dom.createNode(document, "<_X_reversificationCalloutSourceRefCollection/>")
      containerNode.appendChild(textNode)
      res.add(containerNode)
    }



    /**************************************************************************/
    res.reversed().forEach { Dom.insertNodeAfter(sidNode, it) }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private val C_ReversificationNotesLevel_None = 999
  private val C_ReversificationNotesLevel_Basic = 0
  private val C_ReversificationNotesLevel_Academic = 1
  private var m_ReversificationNotesLevel = C_ReversificationNotesLevel_None


  /****************************************************************************/
  private fun getReversificationNotesLevel ()
  {
     when (ConfigData["stepReversificationFootnoteLevel"])
     {
       "basic" ->    m_ReversificationNotesLevel = C_ReversificationNotesLevel_Basic
       "academic" -> m_ReversificationNotesLevel = C_ReversificationNotesLevel_Academic
     }
  }


  /****************************************************************************/
  val m_FileProtocol: X_FileProtocol


  /****************************************************************************/
  /* Used where we want to have a standard footnote callout on reversified
     verses, rather than use the callout defined in the reversification data. */

  private var m_FootnoteCalloutGenerator: MarkerHandler



  init {
    m_FileProtocol = fileProtocol
    m_FootnoteCalloutGenerator = MarkerHandlerFactory.createMarkerHandler(MarkerHandlerFactory.Type.FixedCharacter, ConfigData["stepExplanationCallout"]!!)
    getReversificationNotesLevel() // So we know what kinds of footnotes are needed,
  }


}