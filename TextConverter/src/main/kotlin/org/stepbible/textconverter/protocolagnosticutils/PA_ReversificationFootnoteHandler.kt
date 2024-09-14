package org.stepbible.textconverter.protocolagnosticutils

import org.stepbible.textconverter.applicationspecificutils.*import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.configdata.TranslatableFixedText
import org.stepbible.textconverter.nonapplicationspecificutils.debug.Logger
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.*
import org.stepbible.textconverter.nonapplicationspecificutils.ref.RefCollection
import org.stepbible.textconverter.nonapplicationspecificutils.stepexception.StepExceptionWithStackTraceAbandonRun
import org.w3c.dom.Node

/******************************************************************************/
/**
 * Handles footnotes for verses affected by reversification and
 * samification.
 *
 * @author ARA "Jamie" Jamieson
 */

class PA_ReversificationFootnoteHandler (fileProtocol: X_FileProtocol)
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
   * It is probably a little invidious to say exactly what happens here now,
   * because it seems to change quite regularly.
   *
   * We may or may not have a footnote, depending upon the type of
   * reversification run (conversion-time or runtime) and whether we are
   * creating a basic or an academic module.
   *
   * We may or may not take verse details from the NoteMarker details in the
   * reversification row and embed them in the output (standard) verse, normally
   * to indicate what served as the source verse.
   *
   * One thing we *don't* do any more, though, is to use information from the
   * NoteMarker details as the callout for the reversification footnote.  There
   * is no point in doing so, because STEPBible always renders the callout as a
   * down-arrow, regardless of what we request here.
   *
   * @param sidNode The sid node of the verse to which the footnote is to be
   *   added.
   *
   * @param row: The reversification row which gives the salient details.
   *
   * @param reversificationType C(onversionTime) or R(untime)
   *
   */

  fun addFootnoteAndSourceVerseDetailsToVerse (sidNode: Node, row: ReversificationDataRow, reversificationType: Char)
  {
    /**************************************************************************/
    if ('C' == reversificationType)
      throw StepExceptionWithStackTraceAbandonRun("Not expecting to handle conversion-time reversification.")



    /**************************************************************************/
     val calloutDetails = row.calloutDetails
     val document = sidNode.ownerDocument
     val res: MutableList<Node> = mutableListOf()



    /**************************************************************************/
    /* We want the footnote only for certain combinations of reversification
       type (conversion-time vs runtime) and notes level (basic vs academic). */

    val wantFootnote = if (C_ReversificationNotesLevel_None == m_ReversificationNotesLevel) false else ReversificationData.wantFootnote(row, reversificationType, if (C_ReversificationNotesLevel_Basic == m_ReversificationNotesLevel) 'B' else 'A')



    /**************************************************************************/
    /* We want to include the alternative (source) verse details in visible
       form in the output only on conversion-time runs, and even then only
       if we are generating a footnote. */

    val wantVisibleSourceVerseDetails = wantFootnote && 'C' == reversificationType



    /**************************************************************************/
    /* And finally on conversion-time runs, we want to check the source verse
       details.  (Possibly we'll want this on runtime runs too in due course
       -- TBD.) */

    val checkSourceVerseDetails = 'C' == reversificationType



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
      val noteNode = m_FileProtocol.makeFootnoteNode(Permissions.FootnoteAction.AddFootnoteToGeneralVerseAffectedByReversification, document, row.standardRefAsRefKey, text, callout)
      if (null != noteNode)
      {
        res.add(noteNode)
        res.add(Dom.createTextNode(document, " "))
      }



      /************************************************************************/
      /* A bit of rather yucky special case processing.  I have been asked to
         force certain footnotes to the start of the owning verse, even if their
         natural position would be later.  I flag such notes here with a special
         attribute and then move them later. */

      if (null != noteNode && row.requiresNotesToBeMovedToStartOfVerse())
        NodeMarker.setMoveNoteToStartOfVerse(noteNode)



      /************************************************************************/
      /* Check if we need the text which will typically be superscripted and
         bracketed. */

      val alternativeRefCollection = calloutDetails.alternativeRefCollection
      if (null != alternativeRefCollection)
      {
        val basicContent = if (calloutDetails.alternativeRefCollectionHasEmbeddedPlusSign)
          alternativeRefCollection.getLowAsRef().toString("a") + TranslatableFixedText.stringFormatWithLookup("V_reversification_alternativeReferenceEmbeddedPlusSign") + alternativeRefCollection.getHighAsRef().toString("a")
        else if (calloutDetails.alternativeRefCollectionHasPrefixPlusSign)
          TranslatableFixedText.stringFormatWithLookup("V_reversification_alternativeReferencePrefixPlusSign") + alternativeRefCollection.toString("a")
        else
          alternativeRefCollection.toString("a")

        val textNode = Dom.createTextNode(document, TranslatableFixedText.stringFormatWithLookup("V_reversification_alternativeReferenceFormat", basicContent))
        val containerNode = Dom.createNode(document, "<_X_reversificationCalloutAlternativeRefCollection/>")
        containerNode.appendChild(textNode)
        res.add(containerNode)
      }
    } // if (wantFootnote)



    /**************************************************************************/
    /* Add the source verse details.  I do this in two bites.  I always add
       a stylised comment giving the source verse details.  */

    val sourceRefCollection = calloutDetails.sourceVerseCollection
    if (null != sourceRefCollection)
    {
      if (checkSourceVerseDetails)
      {
        val sourceRefCollectionAsPossiblyAbbreviatedString = sourceRefCollection.toStringUsx()
        val sidRefLow = RefCollection.rdOsis(sidNode["sID"]!!).getLowAsRef()
        val sourceRefLow = RefCollection.rdUsx(sourceRefCollectionAsPossiblyAbbreviatedString, sidRefLow, "v").getLowAsRef()
        if (sidRefLow != sourceRefLow)
          Logger.error(sidRefLow.toRefKey(), "altVerse error (reversification data gives $sourceRefCollectionAsPossiblyAbbreviatedString).")
      }


//      val commentNode = Dom.createCommentNode(document, "altVerse: $altVerseNumber")
//      res.add(commentNode)
      if (wantVisibleSourceVerseDetails)
      {
        val basicContent = sourceRefCollection.toString("a")
        val altVerseNumber = TranslatableFixedText.stringFormatWithLookup("V_reversification_alternativeReferenceFormat", basicContent)
        val textNode = Dom.createTextNode(document, altVerseNumber)
        val containerNode = Dom.createNode(document, "<_X_reversificationCalloutSourceRefCollection/>")
        containerNode.appendChild(textNode)
        res.add(containerNode)
      }
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
  val m_FileProtocol: X_FileProtocol = fileProtocol


  /****************************************************************************/
  /* Used where we want to have a standard footnote callout on reversified
     verses, rather than use the callout defined in the reversification data. */

  private var m_FootnoteCalloutGenerator: MarkerHandler =
    MarkerHandlerFactory.createMarkerHandler(MarkerHandlerFactory.Type.FixedCharacter, ConfigData["stepExplanationCallout"]!!)



  init {
    getReversificationNotesLevel() // So we know what kinds of footnotes are needed,
  }


}