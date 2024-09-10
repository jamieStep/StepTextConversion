package org.stepbible.textconverter.applicationspecificutils

import org.stepbible.textconverter.nonapplicationspecificutils.configdata.ConfigData


/******************************************************************************/
/**
 * Assesses whether footnotes can be applied or not.
 *
 * Footnotes may be applied to verses for a number of reasons -- as a result of
 * reversification, because we want to explain that we have expanded out an
 * elision, etc.
 *
 * However, we may be limited in what we can do by copyright conditions: I
 * assume that if a text is marked as copyright, then we can apply footnotes
 * only in extremis.
 *
 * This class returns an indication, for each class of footnote, whether it
 * can be applied or not.
 *
 * @author ARA "Jamie" Jamieson
 */

object FootnoteApplicabilityAssessor
{
  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Public                                  **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  enum class FootnoteType { EmptyVerseInRawText,
                            VerseGeneratedToFillHoles,
                            SlaveVerseInElision,
                            MasterVerseInElision,
                            SlaveVerseInElidedTable,
                            MasterVerseInElidedTable,
                            EmptyVerseGeneratedByReversification,
                            GeneralReversificationFootnote
  }


  /****************************************************************************/
  /**
  * Checks whether a footnote of a given type is permitted or not.
  *
  * @param footnoteType What is says on the tin.
  * @return True if permitted.
  */

  fun footnoteIsPermitted (footnoteType: FootnoteType): Boolean
  {
    return !m_IsCopyrightText // Until we know better.
  }





  /****************************************************************************/
  /****************************************************************************/
  /**                                                                        **/
  /**                                Private                                 **/
  /**                                                                        **/
  /****************************************************************************/
  /****************************************************************************/

  /****************************************************************************/
  val m_IsCopyrightText = ConfigData.getAsBoolean("stepIsCopyrightText", "yes")
}