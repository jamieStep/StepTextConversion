package org.stepbible.textconverter.applicationspecificutils

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData
import org.stepbible.textconverter.nonapplicationspecificutils.miscellaneous.ObjectInterface


/******************************************************************************/
/**
 * There are various things we might want to do to texts in an ideal world.
 * Unfortunately we don't live in an ideal world, and some of the things we
 * might want to do to a particular text may be ruled out by licence conditions.
 *
 * This class is concerned with indicating whether we have permission for
 * these various activities.
 *
 * In general, I take it here that with copyright texts anything potentially
 * contentious is ruled out; and that with non-copyright texts, anything goes.
 * In fact, we can probably do slightly better than this, because texts from
 * DBL carry a licence file, and this can give more specific information about
 * what is permitted (for example, whether we can or cannot add footnotes).  At
 * present, however, I am not getting down to that level of granularity.
 *
 * I do, in fact, have some doubts about, say, a blanket ban on footnotes.  I
 * can see that translators might not want people adding footnotes which might
 * be at odds with their own view of the meaning of the text.  But where we
 * restructure the text (and although on copyright texts we generally avoid
 * this, there are a few circumstances -- such as complex tables -- where we
 * have no choice) ... where we restructure the text, footnotes explaining the
 * fact seem rather desirable.  This notwithstanding, though, at present I do
 * not permit the processing to add footnotes on any copyright text.
 *
 * @author ARA "Jamie" Jamieson
 */

object Permissions: ObjectInterface
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  enum class FootnoteAction { AddFootnoteToVerseWhichWasEmptyInRawTextGeneric,
                              AddFootnoteToVerseWhichWasEmptyInRawTextReversification,
                              AddFootnoteToVerseGeneratedToFillHolesGeneric,
                              AddFootnoteToVerseGeneratedToFillHolesReversification,
                              AddFootnoteToVerseGeneratedBecauseWeNeedToAddAChapter,
                              AddFootnoteToSlaveVerseInElision,
                              AddFootnoteToMasterVerseInElision,
                              AddFootnoteToSlaveVerseInElidedTable,
                              AddFootnoteToMasterVerseInElidedTable,
                              AddFootnoteToGeneralVerseAffectedByReversification
  }


  /****************************************************************************/
  /**
  * Checks whether it is ok to process the given type of action.
  *
  * @param type Type of action.
  * @return True if permitted.
  */

  fun okToProcess (type: FootnoteAction): Boolean
  {
    return when (type)
    {
      FootnoteAction.AddFootnoteToVerseWhichWasEmptyInRawTextGeneric -> false // We assume if a verse is empty in the raw text, the translators _may_ have added their own footnote, and we don't want to risk duplicating things.
      FootnoteAction.AddFootnoteToVerseGeneratedToFillHolesGeneric -> m_IsOkToGenerateFootnotes
      FootnoteAction.AddFootnoteToVerseWhichWasEmptyInRawTextReversification -> m_IsOkToGenerateFootnotes
      FootnoteAction.AddFootnoteToVerseGeneratedToFillHolesReversification -> m_IsOkToGenerateFootnotes
      FootnoteAction.AddFootnoteToVerseGeneratedBecauseWeNeedToAddAChapter -> true
      FootnoteAction.AddFootnoteToSlaveVerseInElision -> false
      FootnoteAction.AddFootnoteToMasterVerseInElision -> m_IsOkToGenerateFootnotes
      FootnoteAction.AddFootnoteToSlaveVerseInElidedTable -> false
      FootnoteAction.AddFootnoteToMasterVerseInElidedTable -> true
      FootnoteAction.AddFootnoteToGeneralVerseAffectedByReversification -> m_IsOkToGenerateFootnotes
    }
  }





  /****************************************************************************/
  enum class RestructureAction { ExpandElisions, ConvertTablesToElisions
  }


  /****************************************************************************/
  /**
  * Checks whether it is ok to process the given type of action.
  *
  * @param type Type of action.
  * @return True if permitted.
  */

  fun okToProcess (type: RestructureAction): Boolean
  {
    return when (type)
    {
      RestructureAction.ExpandElisions                -> true
      RestructureAction.ConvertTablesToElisions       -> true
    }
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  private val m_IsCopyrightText = ConfigData.getAsBoolean("stepIsCopyrightText", "yes")
  private val m_IsOkToGenerateFootnotes = !ConfigData.getAsBoolean("stepIsCopyrightText", "yes") || ConfigData.getAsBoolean("stepIsOkToGenerateFootnotes", "no")
}